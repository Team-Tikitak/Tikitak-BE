// 피드·알림 조회 API 베이스라인 측정 (#217)
//
// 실행은 저장소 루트에서 한다(요약 JSON 경로가 실행 위치 기준이다). 절차는 loadtest/README.md "8. 측정".
//   JWT_SECRET=<loadtest/.env 값> k6 run loadtest/k6/read-api-baseline.js                  # MODE=mixed
//   JWT_SECRET=... MODE=ramp k6 run loadtest/k6/read-api-baseline.js
//   JWT_SECRET=... MODE=solo API=map_pins k6 run loadtest/k6/read-api-baseline.js
//
// env
//   JWT_SECRET      (필수) 앱과 같은 HS256 키. setup()이 회원마다 access token을 서명한다
//   MODE            mixed(기본) | ramp | solo
//   API             solo 대상 (아래 APIS 키)
//   BASE_URL        API 주소(기본 http://localhost:8080)
//   MANAGEMENT_URL  actuator 주소(기본 http://localhost:8081, loadtest/.env의 MANAGEMENT_SERVER_PORT). setup()의 health 확인에 쓴다
//   RATE            mixed 측정 RPS(기본 100) / solo RPS(기본 20)
//   WARMUP          mixed 웜업 시간(기본 1m)
//   WARMUP_RATE     mixed 웜업 RPS(기본 20)
//   WARMUP_ONLY     1이면 mixed에서 웜업 시나리오만 돌린다(판정 없음). scripts/run-measure.sh의 측정 전 웜업이 쓴다
//   DURATION        mixed 측정 시간(기본 5m) / solo 시간(기본 1m)
//   RAMP_RATES      ramp 단계 RPS 목록(기본 25,50,100,200,400)
//   STAGE_DURATION  ramp 단계 유지 시간(기본 2m)
//   TRANSITION      ramp 단계 사이 전환 시간(기본 10s, 이 구간 요청은 stage=transition)
//   MAX_VUS         maxVUs(기본 300)
//   RUN_ID          요약 파일 이름 접두사(기본 실행 시각). scripts/run-measure.sh가 넘긴다
//   SUMMARY_DIR     요약 저장 디렉터리(기본 loadtest/results/raw)
//   TARGETS, COMMENT_FEEDS  입력 JSON 경로(이 파일 기준, 기본 ./data/targets.json, ./data/comment-feeds.json)

import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';
import crypto from 'k6/crypto';
import encoding from 'k6/encoding';
import exec from 'k6/execution';

const MODE = (__ENV.MODE || 'mixed').toLowerCase();
const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
const MANAGEMENT_URL = (__ENV.MANAGEMENT_URL || 'http://localhost:8081').replace(/\/+$/, '');
const WARMUP_ONLY = MODE === 'mixed' && __ENV.WARMUP_ONLY === '1';
const MAX_VUS = parseInt(__ENV.MAX_VUS || '300', 10);
const SUMMARY_DIR = (__ENV.SUMMARY_DIR || 'loadtest/results/raw').replace(/\/+$/, '');
const BIG_TEAM_RATIO = 0.3; // 요청의 30%는 큰 팀
const HEAVY_COMMENT_RATIO = 0.2; // 댓글 요청 중 댓글 많은 피드(500~2,000개) 비율. 나머지는 팀 최신 피드 20개 중 하나
const JWT_TTL_SECONDS = 24 * 60 * 60;

// ---------------------------------------------------------------------------
// 요청 유형. weight 합은 100이다(지도 중심, 계약 "k6 시나리오").
// ---------------------------------------------------------------------------
const APIS = {
	map_pins: { weight: 20, build: buildMapPins },
	feed_by_place: { weight: 20, build: buildFeedByPlace },
	feed_first: { weight: 15, build: buildFeedFirst },
	feed_deep: { weight: 5, build: buildFeedDeep },
	best_attendance: { weight: 5, build: buildBestAttendance },
	comments: { weight: 10, build: buildComments },
	notifications: { weight: 10, build: buildNotifications },
	unread_count: { weight: 15, build: buildUnreadCount },
};
const API_NAMES = Object.keys(APIS);

// ---------------------------------------------------------------------------
// 입력 데이터. SharedArray는 원소를 접근할 때마다 그 원소만 역직렬화하므로 팀 하나를 원소 하나로 둔다.
// ---------------------------------------------------------------------------
const TARGETS_PATH = __ENV.TARGETS || './data/targets.json';
const COMMENT_FEEDS_PATH = __ENV.COMMENT_FEEDS || './data/comment-feeds.json';
let rawCache = null;

function raw() {
	if (rawCache === null) {
		const targets = JSON.parse(open(TARGETS_PATH));
		const recent = {};
		for (const row of JSON.parse(open(COMMENT_FEEDS_PATH))) {
			recent[row.teamId] = row.feedIds;
		}
		rawCache = { targets, recent };
	}
	return rawCache;
}

function toTeam(t, recent) {
	// 핀 선택은 장소의 피드 수에 비례해 고르므로 누적 가중치를 미리 만든다.
	const placeIds = [];
	const placeCum = [];
	let sum = 0;
	for (const p of t.places) {
		sum += p.feedCount;
		placeIds.push(p.externalPlaceId);
		placeCum.push(sum);
	}
	return {
		teamId: t.teamId,
		memberIds: t.members.map((m) => m.memberId),
		placeIds,
		placeCum,
		recentFeedIds: recent[t.teamId] || [],
	};
}

const bigTeams = new SharedArray('bigTeams', () => {
	const { targets, recent } = raw();
	return targets.teams.filter((t) => t.isBig).map((t) => toTeam(t, recent));
});
const normalTeams = new SharedArray('normalTeams', () => {
	const { targets, recent } = raw();
	return targets.teams.filter((t) => !t.isBig).map((t) => toTeam(t, recent));
});
// 댓글 많은 피드·깊은 커서에는 그 팀이 bigTeams/normalTeams의 몇 번째인지(teamRef)를 미리 붙인다.
// VU에서 팀을 teamId로 찾으려고 팀 배열 전체를 훑으면 원소마다 역직렬화가 일어나 VU당 수백 ms가 든다.
function teamRefs() {
	const refs = {};
	const { targets } = raw();
	targets.teams.filter((t) => t.isBig).forEach((t, i) => (refs[t.teamId] = { big: true, index: i }));
	targets.teams.filter((t) => !t.isBig).forEach((t, i) => (refs[t.teamId] = { big: false, index: i }));
	return refs;
}
const heavyFeeds = new SharedArray('heavyFeeds', () => {
	const refs = teamRefs();
	return raw().targets.commentHeavyFeeds.map((f) => ({ ...f, teamRef: refs[f.teamId] }));
});
const deepCursors = new SharedArray('deepCursors', () => {
	const refs = teamRefs();
	return raw().targets.deepCursors.map((c) => ({ ...c, teamRef: refs[c.teamId] }));
});

// ---------------------------------------------------------------------------
// 시나리오
// ---------------------------------------------------------------------------
function parseDuration(s) {
	const m = /^(?:(\d+)h)?(?:(\d+)m)?(?:(\d+)s)?$/.exec(String(s).trim());
	if (!m || s === '') {
		throw new Error(`duration 형식이 아니다: ${s} (예: 30s, 2m, 1m30s)`);
	}
	return (parseInt(m[1] || '0', 10) * 3600) + (parseInt(m[2] || '0', 10) * 60) + parseInt(m[3] || '0', 10);
}

const RAMP_RATES = (__ENV.RAMP_RATES || '25,50,100,200,400').split(',').map((v) => parseInt(v.trim(), 10));
const STAGE_SECONDS = parseDuration(__ENV.STAGE_DURATION || '2m');
const TRANSITION_SECONDS = parseDuration(__ENV.TRANSITION || '10s');

function stageLabel(rate) {
	return `r${String(rate).padStart(3, '0')}`;
}

// ramp 시나리오 시작 기준 경과 초로 단계 태그를 정한다.
// 0~STAGE: 첫 단계, 이후 (TRANSITION + STAGE) 주기로 전환 구간과 유지 구간이 번갈아 온다.
function currentStage() {
	const elapsed = (Date.now() - exec.scenario.startTime) / 1000;
	if (elapsed < STAGE_SECONDS) {
		return stageLabel(RAMP_RATES[0]);
	}
	const period = TRANSITION_SECONDS + STAGE_SECONDS;
	const k = elapsed - STAGE_SECONDS;
	const i = Math.floor(k / period);
	if (i + 1 >= RAMP_RATES.length) {
		return stageLabel(RAMP_RATES[RAMP_RATES.length - 1]); // gracefulStop 구간
	}
	return k - (i * period) < TRANSITION_SECONDS ? 'transition' : stageLabel(RAMP_RATES[i + 1]);
}

function rampSchedule() {
	const schedule = [];
	let t = 0;
	RAMP_RATES.forEach((rate, i) => {
		if (i > 0) {
			schedule.push({ stage: 'transition', fromSeconds: t, toSeconds: t + TRANSITION_SECONDS, rate: `${RAMP_RATES[i - 1]}->${rate}` });
			t += TRANSITION_SECONDS;
		}
		schedule.push({ stage: stageLabel(rate), fromSeconds: t, toSeconds: t + STAGE_SECONDS, rate });
		t += STAGE_SECONDS;
	});
	return schedule;
}

function buildScenarios() {
	if (MODE === 'mixed') {
		const warmup = __ENV.WARMUP || '1m';
		const warmupScenario = {
			executor: 'constant-arrival-rate',
			rate: parseInt(__ENV.WARMUP_RATE || '20', 10),
			timeUnit: '1s',
			duration: warmup,
			preAllocatedVUs: 20,
			maxVUs: MAX_VUS,
			gracefulStop: '0s',
		};
		if (WARMUP_ONLY) {
			return { warmup: warmupScenario };
		}
		return {
			// 웜업은 별도 시나리오라 threshold(scenario:measure)에 섞이지 않는다.
			warmup: warmupScenario,
			measure: {
				executor: 'constant-arrival-rate',
				startTime: warmup,
				rate: parseInt(__ENV.RATE || '100', 10),
				timeUnit: '1s',
				duration: __ENV.DURATION || '5m',
				preAllocatedVUs: 50,
				maxVUs: MAX_VUS,
			},
		};
	}
	if (MODE === 'ramp') {
		const stages = [{ target: RAMP_RATES[0], duration: `${STAGE_SECONDS}s` }];
		for (const rate of RAMP_RATES.slice(1)) {
			stages.push({ target: rate, duration: `${TRANSITION_SECONDS}s` });
			stages.push({ target: rate, duration: `${STAGE_SECONDS}s` });
		}
		return {
			ramp: {
				executor: 'ramping-arrival-rate',
				startRate: RAMP_RATES[0],
				timeUnit: '1s',
				stages,
				preAllocatedVUs: 50,
				maxVUs: MAX_VUS,
			},
		};
	}
	if (MODE === 'solo') {
		if (!APIS[__ENV.API]) {
			throw new Error(`MODE=solo에는 API=<${API_NAMES.join('|')}>가 필요하다. 받은 값: ${__ENV.API}`);
		}
		return {
			solo: {
				executor: 'constant-arrival-rate',
				rate: parseInt(__ENV.RATE || '20', 10),
				timeUnit: '1s',
				duration: __ENV.DURATION || '1m',
				preAllocatedVUs: 50,
				maxVUs: MAX_VUS,
			},
		};
	}
	throw new Error(`알 수 없는 MODE: ${MODE} (mixed | ramp | solo)`);
}

function buildThresholds() {
	const th = {};
	if (WARMUP_ONLY) {
		// 판정하지 않는다. 요약 표에 API별 웜업 지연이 나오도록 항상 통과하는 조건으로 하위 지표만 만든다.
		for (const api of API_NAMES) {
			th[`http_req_duration{scenario:warmup,api:${api}}`] = ['max>=0'];
			th[`http_req_failed{scenario:warmup,api:${api}}`] = ['rate>=0'];
			th[`checks{scenario:warmup,api:${api}}`] = ['rate>=0'];
		}
		return th;
	}
	if (MODE === 'mixed') {
		for (const api of API_NAMES) {
			th[`http_req_duration{scenario:measure,api:${api}}`] = ['p(95)<300'];
			th[`http_req_failed{scenario:measure,api:${api}}`] = ['rate<0.01'];
			th[`checks{scenario:measure,api:${api}}`] = ['rate>0.99'];
		}
		th['dropped_iterations{scenario:measure}'] = ['count==0'];
	} else if (MODE === 'ramp') {
		// 판정용이 아니라 단계별 P95·실패율을 요약에 남기려고 건다(높은 단계에서 실패하는 것이 정상일 수 있다).
		for (const rate of RAMP_RATES) {
			th[`http_req_duration{stage:${stageLabel(rate)}}`] = ['p(95)<300'];
			th[`http_req_failed{stage:${stageLabel(rate)}}`] = ['rate<0.01'];
			th[`checks{stage:${stageLabel(rate)}}`] = ['rate>0.99'];
		}
		for (const api of API_NAMES) {
			th[`http_req_duration{api:${api}}`] = ['p(95)<300'];
			th[`http_req_failed{api:${api}}`] = ['rate<0.01'];
			th[`checks{api:${api}}`] = ['rate>0.99'];
		}
		th['dropped_iterations'] = ['count==0'];
	} else {
		const api = __ENV.API;
		th[`http_req_duration{scenario:solo,api:${api}}`] = ['p(95)<300'];
		th[`http_req_failed{scenario:solo,api:${api}}`] = ['rate<0.01'];
		th[`checks{scenario:solo,api:${api}}`] = ['rate>0.99'];
		th['dropped_iterations{scenario:solo}'] = ['count==0'];
	}
	return th;
}

export const options = {
	scenarios: buildScenarios(),
	thresholds: buildThresholds(),
	summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max', 'count'],
	setupTimeout: '2m',
	discardResponseBodies: false,
	insecureSkipTLSVerify: false,
};

// ---------------------------------------------------------------------------
// 토큰: TokenService.createToken과 같은 형식
//   header  {"alg":"HS256","typ":"JWT"}
//   payload {"sub":"<memberId>","type":"access","iat":<sec>,"exp":<sec>,"jti":"<uuid>"} (키 순서도 같게)
//   base64url 패딩 없음(Base64.getUrlEncoder().withoutPadding()), 서명은 HMAC-SHA256(secret UTF-8)
//   앱은 서명을 문자열로 비교하므로 인코딩이 한 글자라도 다르면 AUTH009가 난다.
// ---------------------------------------------------------------------------
function uuidV4() {
	const b = new Uint8Array(crypto.randomBytes(16));
	b[6] = (b[6] & 0x0f) | 0x40;
	b[8] = (b[8] & 0x3f) | 0x80;
	const h = Array.from(b, (x) => x.toString(16).padStart(2, '0')).join('');
	return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
}

function signAccessToken(memberId, secret, nowSeconds) {
	const header = encoding.b64encode(JSON.stringify({ alg: 'HS256', typ: 'JWT' }), 'rawurl');
	const payload = encoding.b64encode(JSON.stringify({
		sub: String(memberId),
		type: 'access',
		iat: nowSeconds,
		exp: nowSeconds + JWT_TTL_SECONDS,
		jti: uuidV4(),
	}), 'rawurl');
	const unsigned = `${header}.${payload}`;
	return `${unsigned}.${crypto.hmac('sha256', secret, unsigned, 'base64rawurl')}`;
}

export function setup() {
	const secret = __ENV.JWT_SECRET;
	if (!secret) {
		throw new Error('JWT_SECRET env가 필요하다(loadtest/.env 값). scripts/run-measure.sh는 자동으로 넘긴다.');
	}
	const health = http.get(`${MANAGEMENT_URL}/actuator/health`, { tags: { api: 'setup' } });
	if (health.status !== 200) {
		throw new Error(`앱이 응답하지 않는다: ${MANAGEMENT_URL}/actuator/health status=${health.status}`);
	}
	const nowSeconds = Math.floor(Date.now() / 1000);
	const tokens = {};
	const sign = (memberId) => {
		if (!tokens[memberId]) {
			tokens[memberId] = signAccessToken(memberId, secret, nowSeconds);
		}
	};
	for (let i = 0; i < bigTeams.length; i++) {
		bigTeams[i].memberIds.forEach(sign);
	}
	for (let i = 0; i < normalTeams.length; i++) {
		normalTeams[i].memberIds.forEach(sign);
	}
	return {
		tokens,
		startedAt: new Date().toISOString(),
		startedAtMs: Date.now(),
	};
}

// ---------------------------------------------------------------------------
// 요청 만들기
// ---------------------------------------------------------------------------
function randInt(n) {
	return Math.floor(Math.random() * n);
}

function pick(arr) {
	return arr[randInt(arr.length)];
}

// 30%는 큰 팀, 70%는 일반 팀. 팀 안에서 회원은 무작위.
function pickTeam() {
	return Math.random() < BIG_TEAM_RATIO ? bigTeams[randInt(bigTeams.length)] : normalTeams[randInt(normalTeams.length)];
}

function teamOf(ref) {
	return ref.big ? bigTeams[ref.index] : normalTeams[ref.index];
}

function req(path, team, memberId, name) {
	return { url: `${BASE_URL}${path}`, teamId: team ? team.teamId : null, memberId, name };
}

function buildMapPins() {
	const team = pickTeam();
	return req(`/api/v1/teams/${team.teamId}/map/pins`, team, pick(team.memberIds), '/teams/{teamId}/map/pins');
}

function buildFeedByPlace() {
	let team = pickTeam();
	for (let i = 0; i < 10 && team.placeIds.length === 0; i++) {
		team = pickTeam();
	}
	// 장소의 피드 수에 비례해 고른다.
	const r = Math.random() * team.placeCum[team.placeCum.length - 1];
	let lo = 0;
	let hi = team.placeCum.length - 1;
	while (lo < hi) {
		const mid = (lo + hi) >> 1;
		if (team.placeCum[mid] > r) {
			hi = mid;
		} else {
			lo = mid + 1;
		}
	}
	const placeId = team.placeIds[lo];
	return req(`/api/v1/teams/${team.teamId}/feeds?placeId=${encodeURIComponent(placeId)}`, team, pick(team.memberIds),
		'/teams/{teamId}/feeds?placeId');
}

function buildFeedFirst() {
	const team = pickTeam();
	return req(`/api/v1/teams/${team.teamId}/feeds`, team, pick(team.memberIds), '/teams/{teamId}/feeds');
}

// 깊은 페이지는 큰 팀에만 있다(1만·5만·9만 번째 피드 다음 페이지).
function buildFeedDeep() {
	const c = deepCursors[randInt(deepCursors.length)];
	const team = teamOf(c.teamRef);
	return req(`/api/v1/teams/${team.teamId}/feeds?cursor=${encodeURIComponent(c.cursor)}`, team, pick(team.memberIds),
		'/teams/{teamId}/feeds?cursor');
}

function buildBestAttendance() {
	const team = pickTeam();
	return req(`/api/v1/teams/${team.teamId}/home/best-attendance`, team, pick(team.memberIds),
		'/teams/{teamId}/home/best-attendance');
}

function buildComments() {
	let team;
	let feedId;
	if (Math.random() < HEAVY_COMMENT_RATIO) {
		const f = heavyFeeds[randInt(heavyFeeds.length)];
		team = teamOf(f.teamRef);
		feedId = f.feedId;
	} else {
		team = pickTeam();
		feedId = pick(team.recentFeedIds);
	}
	return req(`/api/v1/teams/${team.teamId}/feeds/${feedId}/comments`, team, pick(team.memberIds),
		'/teams/{teamId}/feeds/{feedId}/comments');
}

// 알림은 teamId 없이 회원 전체 알림을 조회한다.
function buildNotifications() {
	const team = pickTeam();
	return req('/api/v1/me/notifications', null, pick(team.memberIds), '/me/notifications');
}

function buildUnreadCount() {
	const team = pickTeam();
	return req('/api/v1/me/notifications/unread-count', null, pick(team.memberIds), '/me/notifications/unread-count');
}

const CUM_WEIGHTS = (() => {
	let sum = 0;
	return API_NAMES.map((name) => {
		sum += APIS[name].weight;
		return [name, sum];
	});
})();

function pickApi() {
	if (MODE === 'solo') {
		return __ENV.API;
	}
	const r = Math.random() * CUM_WEIGHTS[CUM_WEIGHTS.length - 1][1];
	for (const [name, cum] of CUM_WEIGHTS) {
		if (r < cum) {
			return name;
		}
	}
	return API_NAMES[API_NAMES.length - 1];
}

export default function (data) {
	const api = pickApi();
	const r = APIS[api].build();
	const tags = { api, name: r.name };
	if (MODE === 'ramp') {
		tags.stage = currentStage();
	}
	const res = http.get(r.url, {
		headers: { Authorization: `Bearer ${data.tokens[r.memberId]}` },
		tags,
	});
	let body = null;
	try {
		body = res.json();
	} catch (e) {
		body = null;
	}
	const checks = {
		'status 200': () => res.status === 200,
		'success true': () => body !== null && body.success === true,
	};
	if (api === 'feed_by_place') {
		checks['place feed >= 1'] = () => body !== null && !!body.data && Array.isArray(body.data.items) && body.data.items.length >= 1;
	}
	check(res, checks, tags);
	if (res.status !== 200 && __ENV.LOG_ERRORS !== '0') {
		console.warn(`${api} ${res.status} ${r.url} member=${r.memberId} body=${String(res.body).slice(0, 200)}`);
	}
}

// ---------------------------------------------------------------------------
// 요약: loadtest/results/raw/<RUN_ID>-<mode>[-api]-k6-summary.json 으로 저장하고, API별 표를 stdout에 출력한다.
// ---------------------------------------------------------------------------
function pad(v, n) {
	return String(v).padEnd(n);
}

function fmt(v) {
	return v === undefined || v === null ? '-' : Number(v).toFixed(1);
}

function findMetric(metrics, base, mustInclude) {
	for (const key of Object.keys(metrics)) {
		if (!key.startsWith(`${base}{`)) {
			continue;
		}
		if (mustInclude.every((part) => key.includes(part))) {
			return metrics[key];
		}
	}
	return null;
}

function rows(metrics, labels, filtersFor) {
	const lines = [];
	lines.push(`${pad('key', 18)}${pad('count', 9)}${pad('avg', 9)}${pad('p95', 9)}${pad('p99', 9)}${pad('max', 9)}${pad('fail%', 8)}checks%`);
	for (const label of labels) {
		const f = filtersFor(label);
		const d = findMetric(metrics, 'http_req_duration', f);
		const fail = findMetric(metrics, 'http_req_failed', f);
		const chk = findMetric(metrics, 'checks', f);
		if (!d) {
			continue;
		}
		const v = d.values;
		lines.push(`${pad(label, 18)}${pad(v.count, 9)}${pad(fmt(v.avg), 9)}${pad(fmt(v['p(95)']), 9)}${pad(fmt(v['p(99)']), 9)}`
			+ `${pad(fmt(v.max), 9)}${pad(fail ? fmt(fail.values.rate * 100) : '-', 8)}${chk ? fmt(chk.values.rate * 100) : '-'}`);
	}
	return lines.join('\n');
}

function timestamp() {
	const d = new Date(Date.now() + (9 * 3600 * 1000)); // KST
	return d.toISOString().replace(/[-:]/g, '').replace('T', '-').slice(0, 15);
}

export function handleSummary(data) {
	const runId = __ENV.RUN_ID || timestamp();
	const suffix = MODE === 'solo' ? `${MODE}-${__ENV.API}` : (WARMUP_ONLY ? 'prewarm' : MODE);
	const file = `${SUMMARY_DIR}/${runId}-${suffix}-k6-summary.json`;
	const setupData = data.setup_data || {};
	const meta = {
		mode: MODE,
		api: __ENV.API || null,
		baseUrl: BASE_URL,
		managementUrl: MANAGEMENT_URL,
		warmupOnly: WARMUP_ONLY,
		scenarios: options.scenarios,
		setupFinishedAt: setupData.startedAt || null,
		setupFinishedAtMs: setupData.startedAtMs || null,
		tokens: setupData.tokens ? Object.keys(setupData.tokens).length : 0,
		weights: Object.fromEntries(API_NAMES.map((n) => [n, APIS[n].weight])),
		bigTeamRatio: BIG_TEAM_RATIO,
		heavyCommentRatio: HEAVY_COMMENT_RATIO,
		rampSchedule: MODE === 'ramp' ? rampSchedule() : null,
	};
	const out = { meta, state: data.state, metrics: data.metrics };

	const m = data.metrics;
	const dropped = m.dropped_iterations ? m.dropped_iterations.values.count : 0;
	let text = `\n[${WARMUP_ONLY ? 'prewarm' : MODE}${__ENV.API ? ` ${__ENV.API}` : ''}] summary -> ${file}\n`;
	if (MODE === 'mixed') {
		text += rows(m, API_NAMES, (api) => [`api:${api}`, `scenario:${WARMUP_ONLY ? 'warmup' : 'measure'}`]);
	} else if (MODE === 'ramp') {
		text += `${rows(m, RAMP_RATES.map(stageLabel), (s) => [`stage:${s}`])}\n\n`;
		text += rows(m, API_NAMES, (api) => [`api:${api}`]);
	} else {
		text += rows(m, [__ENV.API], (api) => [`api:${api}`, 'scenario:solo']);
	}
	text += `\ndropped_iterations: ${dropped}\n`;
	const failed = Object.entries(m).filter(([, v]) => v.thresholds && Object.values(v.thresholds).some((t) => !t.ok)).map(([k]) => k);
	text += failed.length === 0 ? 'thresholds: all passed\n' : `thresholds failed:\n  ${failed.join('\n  ')}\n`;

	const files = {
		stdout: text,
		[file]: JSON.stringify(out, null, 2),
	};
	// ramp: 단계 구간의 절대 시각. 시작 기준은 setup() 끝 시각(시나리오 시작과 거의 같다).
	// scripts/run-measure.sh가 이 파일로 Hikari CSV를 단계별로 나눠 최대값을 낸다.
	if (MODE === 'ramp' && setupData.startedAtMs) {
		const lines = ['stage,rate,from_epoch_ms,to_epoch_ms'];
		for (const s of rampSchedule()) {
			lines.push(`${s.stage},${s.rate},${setupData.startedAtMs + (s.fromSeconds * 1000)},${setupData.startedAtMs + (s.toSeconds * 1000)}`);
		}
		files[`${SUMMARY_DIR}/${runId}-${suffix}-stages.csv`] = `${lines.join('\n')}\n`;
	}
	return files;
}
