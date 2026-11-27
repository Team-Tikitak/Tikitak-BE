-- =============================================================================
-- growth/00_common.sql : 성장 시나리오 공통 시드 (member, team, team_member) — #232
--
-- "회원 약 10만 명까지 성장했다면" 시드. 운영 분포(2026-11-27, 활성 팀 30)를 바탕으로 한 계약
-- docs/contracts/2026-11-27-growth-seed-baseline.md "결정 사항"의 수치를 따른다.
-- #217 스트레스 시드(loadtest/sql/00_common.sql)와는 따로 쓴다. 둘을 같은 DB에 넣지 않는다.
--
-- 전제: **빈 DB**. 앱을 한 번 기동해 Flyway V1~V26가 적용된 직후에만 실행한다.
--       member/team/team_member에 행이 하나라도 있으면 아무것도 넣지 않고 에러로 멈춘다.
--       다시 넣으려면 README 7번 초기화(down → pgdata 볼륨만 삭제 → up -d → 앱 기동)로 비운다.
-- 실행:  README "6-4. 성장 시드" 절차. base_ts를 한 번 정해 세 파일(00/10/20)에 같은 값으로 넘긴다.
--          docker compose -f loadtest/docker-compose.yml exec -T postgres \
--            psql -U tikitak -d tikitak -v base_ts=2026-11-27T18:00:00 -f - < loadtest/sql/growth/00_common.sql
-- 변수:  teams      팀 수 (기본 15000)
--        salt       난수 salt 정수 (기본 232)
--        base_ts    기준 시각(TIMESTAMP, KST). 기본은 실행 시점 LOCALTIMESTAMP(초 단위)다.
--                   같은 salt·base_ts면 결과가 같다. 지금보다 미래면 에러로 멈춘다.
--        guest_p    slot 1 이후 자리가 다른 팀 회원(게스트)일 확률 (기본 0.058).
--                   slot 0(팀장)은 게스트가 아니므로, 전체 slot의 약 5%가 게스트가 되도록
--                   0.05 × (평균 팀원 수 6.925 / 팀장 외 자리 5.925) ≈ 0.058로 둔다.
--
-- 난수: random() 대신 10_feed.sql과 같은 pg_temp.lt_r(key, stream) = hashint8extended(key, salt*100+stream)를 [0,1)로.
--
-- 만드는 것 (ID를 명시해서 넣고, 마지막에 identity 시퀀스를 맞춘다)
--   team        : id 1~teams. name 'growth-team-NNNNN', description 'loadtest:growth'(30_targets.sql의 isBig = false).
--                 팀원 수: 2~3명 10%, 4~5명 20%, 6~8명 45%, 9~12명 25%, 구간 안에서 균등. 1명 팀 없음.
--                 created_at: base_ts에서 0~1,095일 전 사이 균등(마이크로초까지).
--   team_member : slot 0이 OWNER, 나머지 MEMBER. 모두 ACTIVE. id는 (team_id, slot) 순.
--                 created_at(가입 시각) = 팀 생성 + least(slot시간, (base_ts - 팀 생성) × slot/(팀원 수+1)). base_ts를 넘지 않는다.
--                 profile_img_url은 약 50%만 채운다(hashint4 기준).
--   member      : 게스트가 아닌 slot마다 1명(그 팀이 주 소속, active_team_id). ACTIVE, onboarding_completed=true,
--                 profile_character_type 채움, 약관·개인정보 동의. provider_id 'growth-<id>'.
--                 created_at = 그 회원의 가장 이른 가입 시각.
--   게스트      : 게스트 slot g번째(team_id, slot 순)는 주 소속 회원을 lt_r 순서로 섞은 목록의 g번째 회원이다.
--                 그 회원의 주 소속 팀이 이 팀이면 (게스트 수 + g)번째 회원으로 바꾼다.
--                 두 번호 구간이 겹치지 않으므로 한 회원은 최대 한 번만 게스트가 된다(최대 2개 팀 소속).
--                 바꾼 회원도 같은 팀이면(확률이 아주 낮다) 무결성 검사에서 에러로 멈춘다. 그때는 salt를 바꾼다.
-- 시각: TIMESTAMP(타임존 없음) 컬럼에 Asia/Seoul 기준 값을 넣는다.
-- =============================================================================

\set ON_ERROR_STOP on

\if :{?teams}
\else
	\set teams 15000
\endif
\if :{?salt}
\else
	\set salt 232
\endif
\if :{?guest_p}
\else
	\set guest_p 0.058
\endif

SET TIME ZONE 'Asia/Seoul';

\if :{?base_ts}
\else
	SELECT to_char(LOCALTIMESTAMP(0), 'YYYY-MM-DD"T"HH24:MI:SS') AS base_ts \gset
	\echo '경고: base_ts를 주지 않아 실행 시각을 쓴다. 10_feed.sql, 20_notification.sql에도 아래 값을 -v base_ts=로 넘긴다.'
\endif

SELECT (:teams)::int >= 1 AS teams_ok,
	(:'base_ts')::timestamp <= LOCALTIMESTAMP + interval '1 minute' AS base_ok \gset
\if :teams_ok
\else
	\echo 'teams는 1 이상이어야 한다'
	\quit 3
\endif
\if :base_ok
\else
	\echo 'base_ts가 지금보다 미래다. 피드·알림 시각이 미래가 되므로 멈춘다.'
	\quit 3
\endif

\echo 'growth/00_common.sql: teams=' :teams ', salt=' :salt ', base_ts=' :base_ts ', guest_p=' :guest_p

BEGIN;

DO $$
BEGIN
	IF EXISTS (SELECT 1 FROM member)
		OR EXISTS (SELECT 1 FROM team)
		OR EXISTS (SELECT 1 FROM team_member) THEN
		RAISE EXCEPTION 'growth/00_common.sql은 빈 DB 전제다. member/team/team_member에 이미 행이 있다. README 7번 초기화(pgdata 볼륨만 삭제) 절차로 비운 뒤 다시 실행한다.';
	END IF;
END $$;

-- 결정적 난수 [0,1). 10_feed.sql과 같은 정의.
SELECT format($fmt$
CREATE FUNCTION pg_temp.lt_r(k bigint, stream int) RETURNS double precision
LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $f$
	SELECT ((hashint8extended(k, %s::bigint * 100 + stream) >> 11) & 9007199254740991)::double precision
		/ 9007199254740992.0
$f$
$fmt$, (:salt)::int) \gexec

-- 팀별 크기와 생성 시각
CREATE TEMP TABLE lt_team ON COMMIT DROP AS
SELECT
	x.team_id,
	CASE
		WHEN x.u_bucket < 0.10 THEN 2 + floor(2 * x.u_size)
		WHEN x.u_bucket < 0.30 THEN 4 + floor(2 * x.u_size)
		WHEN x.u_bucket < 0.75 THEN 6 + floor(3 * x.u_size)
		ELSE 9 + floor(4 * x.u_size)
	END::int AS size,
	(:'base_ts')::timestamp - make_interval(secs => 1095 * 86400 * x.u_age) AS created_at
FROM (
	SELECT t AS team_id, pg_temp.lt_r(t, 1) AS u_bucket, pg_temp.lt_r(t, 2) AS u_size, pg_temp.lt_r(t, 3) AS u_age
	FROM generate_series(1, :teams) AS t
) x;

-- 팀 × slot(0부터). team_member_id는 (team_id, slot) 순, 주 소속 회원 ID는 게스트가 아닌 slot의 순번.
CREATE TEMP TABLE lt_slot ON COMMIT DROP AS
WITH s AS (
	SELECT
		t.team_id,
		g AS slot_no,
		t.size,
		t.created_at AS team_created_at,
		(g >= 1 AND pg_temp.lt_r(t.team_id::bigint * 16 + g, 4) < (:guest_p)) AS is_guest
	FROM lt_team t
	CROSS JOIN LATERAL generate_series(0, t.size - 1) AS g
)
SELECT
	s.*,
	row_number() OVER (ORDER BY s.team_id, s.slot_no) AS team_member_id,
	CASE WHEN NOT s.is_guest
		THEN row_number() OVER (PARTITION BY s.is_guest ORDER BY s.team_id, s.slot_no)
	END AS member_id,
	CASE WHEN s.is_guest
		THEN row_number() OVER (PARTITION BY s.is_guest ORDER BY s.team_id, s.slot_no)
	END AS guest_no,
	s.team_created_at + least(
		make_interval(hours => s.slot_no),
		((:'base_ts')::timestamp - s.team_created_at) * (s.slot_no::double precision / (s.size + 1))) AS joined_at
FROM s;

-- 게스트 후보: 주 소속 회원을 lt_r 순서로 섞은 목록(rk 1부터)
CREATE TEMP TABLE lt_pool ON COMMIT DROP AS
SELECT member_id, team_id,
	row_number() OVER (ORDER BY pg_temp.lt_r(member_id, 5), member_id) AS rk
FROM lt_slot
WHERE NOT is_guest;
CREATE INDEX ON lt_pool (rk);

SELECT count(*) AS guest_count FROM lt_slot WHERE is_guest \gset
SELECT count(*) AS pool_count FROM lt_pool \gset

DO $$
BEGIN
	IF (SELECT count(*) FROM lt_slot WHERE is_guest) * 2 > (SELECT count(*) FROM lt_pool) THEN
		RAISE EXCEPTION '게스트 slot이 주 소속 회원의 절반보다 많다. guest_p를 낮춘다.';
	END IF;
END $$;

UPDATE lt_slot g
SET member_id = CASE WHEN a.team_id <> g.team_id THEN a.member_id ELSE b.member_id END
FROM lt_pool a, lt_pool b
WHERE g.is_guest
	AND a.rk = g.guest_no
	AND b.rk = (:guest_count) + g.guest_no;

DO $$
BEGIN
	IF EXISTS (SELECT 1 FROM lt_slot WHERE member_id IS NULL) THEN
		RAISE EXCEPTION '회원을 배정하지 못한 slot이 있다.';
	END IF;
	IF EXISTS (SELECT 1 FROM lt_slot GROUP BY team_id, member_id HAVING count(*) > 1) THEN
		RAISE EXCEPTION '한 팀에 같은 회원이 두 번 들어갔다(게스트 대체 회원도 같은 팀). salt를 바꿔 다시 실행한다.';
	END IF;
END $$;

-- 회원: 주 소속 팀과 가장 이른 가입 시각
CREATE TEMP TABLE lt_member ON COMMIT DROP AS
SELECT
	p.member_id,
	p.team_id AS primary_team_id,
	j.first_joined_at
FROM lt_slot p
JOIN (SELECT member_id, min(joined_at) AS first_joined_at FROM lt_slot GROUP BY member_id) j ON j.member_id = p.member_id
WHERE NOT p.is_guest;

INSERT INTO team (id, name, description, team_img_url, status, created_at, updated_at, deleted_at)
SELECT
	t.team_id,
	'growth-team-' || lpad(t.team_id::text, 5, '0'),
	'loadtest:growth',
	'https://loadtest-media.invalid/teams/' || t.team_id || '.jpg',
	'ACTIVE',
	t.created_at,
	t.created_at,
	NULL
FROM lt_team t
ORDER BY t.team_id;

INSERT INTO member (
	id, email, name, nickname, profile_img_url, social_provider, provider_id, status,
	terms_agreed, privacy_agreed, terms_agreed_at, created_at, updated_at, deleted_at,
	active_team_id, profile_character_type, onboarding_completed
)
SELECT
	m.member_id,
	'growth+' || m.member_id || '@tikitak.invalid',
	'성장회원' || m.member_id,
	'grw' || m.member_id,
	NULL,
	(ARRAY['GOOGLE', 'KAKAO', 'APPLE'])[(m.member_id % 3) + 1],
	'growth-' || m.member_id,
	'ACTIVE',
	TRUE,
	TRUE,
	m.first_joined_at,
	m.first_joined_at,
	m.first_joined_at,
	NULL,
	m.primary_team_id,
	(ARRAY['TAK_LEADER', 'TAK_SPARK', 'TAK_BURNER', 'TAK_BUILDER', 'TAK_FREE', 'TAK_CARE'])[(m.member_id % 6) + 1],
	TRUE
FROM lt_member m
ORDER BY m.member_id;

INSERT INTO team_member (
	id, team_id, member_id, nickname, profile_img_url, role, status,
	created_at, deleted_at, last_activity_checked_at, profile_media_id
)
SELECT
	s.team_member_id,
	s.team_id,
	s.member_id,
	'팀원' || s.team_member_id,
	CASE WHEN abs(hashint4(s.team_member_id::int)) % 2 = 0
		THEN 'https://loadtest-media.invalid/profiles/tm-' || s.team_member_id || '.jpg'
	END,
	CASE WHEN s.slot_no = 0 THEN 'OWNER' ELSE 'MEMBER' END,
	'ACTIVE',
	s.joined_at,
	NULL,
	greatest(s.joined_at, (:'base_ts')::timestamp - make_interval(hours => (s.team_member_id % 72)::int)),
	NULL
FROM lt_slot s
ORDER BY s.team_member_id;

-- ID를 직접 넣었으므로 identity 시퀀스를 맞춘다(앱이 이후 INSERT할 때 충돌하지 않게).
SELECT setval(pg_get_serial_sequence('team', 'id'), (SELECT max(id) FROM team));
SELECT setval(pg_get_serial_sequence('member', 'id'), (SELECT max(id) FROM member));
SELECT setval(pg_get_serial_sequence('team_member', 'id'), (SELECT max(id) FROM team_member));

COMMIT;

VACUUM (ANALYZE) team;
VACUUM (ANALYZE) member;
VACUUM (ANALYZE) team_member;

-- ---------------------------------------------------------------------------
-- 확인 쿼리 (계약 완료 조건 전체는 README 6-4의 확인 쿼리)
-- ---------------------------------------------------------------------------
\echo '== 건수'
SELECT
	(SELECT count(*) FROM team) AS teams,
	(SELECT count(*) FROM member) AS members,
	(SELECT count(*) FROM team_member) AS team_members,
	:guest_count AS guest_slots,
	round(:guest_count::numeric / (SELECT count(*) FROM team_member), 4) AS guest_slot_ratio;

\echo '== 팀원 수 구간 (구간, 팀 수, 비율, 목표)'
SELECT bucket, count(*) AS teams, round(count(*)::numeric / sum(count(*)) OVER (), 4) AS ratio,
	CASE bucket WHEN '1' THEN 0 WHEN '2-3' THEN 0.10 WHEN '4-5' THEN 0.20 WHEN '6-8' THEN 0.45 ELSE 0.25 END AS target
FROM (
	SELECT CASE WHEN c = 1 THEN '1' WHEN c <= 3 THEN '2-3' WHEN c <= 5 THEN '4-5' WHEN c <= 8 THEN '6-8' ELSE '9-12' END AS bucket
	FROM (SELECT team_id, count(*) AS c FROM team_member GROUP BY team_id) x
) y
GROUP BY bucket ORDER BY bucket;

\echo '== 회원별 소속 팀 수 분포'
SELECT team_count, count(*) AS members, round(count(*)::numeric / sum(count(*)) OVER (), 4) AS ratio
FROM (SELECT member_id, count(*) AS team_count FROM team_member GROUP BY member_id) x
GROUP BY team_count
ORDER BY team_count;

\echo '== 팀 나이 (최소/최대 created_at)'
SELECT min(created_at) AS min_team_created_at, max(created_at) AS max_team_created_at, :'base_ts' AS base_ts FROM team;

\echo '== 무결성 검사 (모두 0이어야 한다)'
SELECT
	(SELECT count(*) FROM (
		SELECT team_id FROM team_member GROUP BY team_id
		HAVING count(*) FILTER (WHERE role = 'OWNER') <> 1) x) AS teams_owner_not_1,
	(SELECT count(*) FROM team_member tm JOIN member m ON m.id = tm.member_id
		WHERE tm.role = 'OWNER' AND m.active_team_id <> tm.team_id) AS owner_not_primary,
	(SELECT count(*) FROM (
		SELECT team_id, member_id FROM team_member GROUP BY team_id, member_id HAVING count(*) > 1) x) AS dup_team_member,
	(SELECT count(*) FROM (SELECT member_id FROM team_member GROUP BY member_id HAVING count(*) > 2) x) AS members_over_2_teams,
	(SELECT count(*) FROM member m
		WHERE NOT EXISTS (SELECT 1 FROM team_member tm
			WHERE tm.member_id = m.id AND tm.team_id = m.active_team_id AND tm.status = 'ACTIVE')) AS bad_active_team,
	(SELECT count(*) FROM member
		WHERE status <> 'ACTIVE' OR NOT onboarding_completed OR profile_character_type IS NULL
			OR NOT terms_agreed OR NOT privacy_agreed) AS bad_member_state,
	(SELECT count(*) FROM team_member tm JOIN team t ON t.id = tm.team_id
		WHERE tm.created_at < t.created_at OR tm.created_at > (:'base_ts')::timestamp) AS bad_joined_at;
