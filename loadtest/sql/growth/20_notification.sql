-- =============================================================================
-- growth/20_notification.sql : 성장 시나리오 알림 시드 (notification) — #232
--
-- 전제: growth/00_common.sql, growth/10_feed.sql을 실행한 직후. notification이 비어 있어야 한다.
--       행이 있으면 아무것도 넣지 않고 에러로 멈춘다. 다시 넣으려면 README 7번 초기화(pgdata 볼륨만 삭제) 절차로 비운다.
-- 실행:  README "6-4. 성장 시드". 00_common.sql과 같은 salt·base_ts를 넘긴다.
--          docker compose -f loadtest/docker-compose.yml exec -T postgres \
--            psql -U tikitak -d tikitak -v base_ts=2026-11-27T18:00:00 -f - < loadtest/sql/growth/20_notification.sql
-- 변수:  salt          난수 salt 정수 (기본 232). 00_common.sql과 같은 값을 쓴다
--        base_ts       기준 시각(KST). 00_common.sql과 같은 값을 쓴다
--        zero_ratio    알림 0건 회원 비율 (기본 0.3)
--        notif_mu      나머지 회원 알림 수 로그정규분포의 로그 평균 (기본 4.4630 = ln(143) - σ²/2, 즉 평균 약 143)
--        notif_sigma   그 로그 표준편차 (기본 1.0)
--        max_per       회원당 최대 알림 수 (기본 1300. key = member_id * 4096 + g 이므로 4096 미만이어야 한다)
--        recent_days   "최근" 알림 기준 일수 (기본 30)
--        old_unread    오래된 알림의 안 읽음 확률 배수 (기본 0.2. 오래된 알림 읽음 확률 = 1 - (1 - p) × old_unread)
--
-- 난수는 10_feed.sql과 같은 pg_temp.lt_r. 로그정규는 lt_r 두 개로 Box-Muller.
--
-- 만드는 것
--   회원마다 n = 0 (확률 zero_ratio), 아니면 least(max_per, max(1, round(exp(notif_mu + notif_sigma × z)))).
--   알림 하나는 그 회원이 속한 팀 하나(두 팀 소속이면 균등)와 그 팀의 피드 하나(균등)를 가리킨다.
--   created_at = greatest(피드 created_at, 그 팀 가입 시각) + least(6시간, base_ts - 그 시각) × u.
--     그래서 회원이 처음 팀에 들어간 시각 ~ base_ts 사이이고, 팀 피드 분포(팀 생성 ~ base_ts 균등)를 따른다.
--   type은 5종(DAILY_QUESTION_OPENED 제외). type·title·body·actor는 20_notification.sql과 같은 규칙과 문구다.
--     데일리 답변 피드 → DAILY_QUESTION_UPLOADED, 일반 피드 35% FEED_UPLOADED, 20% FEED_TAGGED (actor = 작성자),
--     30% FEED_COMMENT, 15% FEED_COMMENT_REPLIED (actor = 받는 회원이 아닌 같은 팀 멤버).
--     작성자가 받는 회원 자신이면 FEED_COMMENT로 바꾸고 actor는 다른 멤버로 둔다.
--   읽음: 회원마다 p = u^(2/3) (Beta(1.5,1), 평균 0.6).
--     created_at >= base_ts - recent_days 이면 확률 p, 그보다 오래되면 확률 1 - (1 - p) × old_unread로 읽음.
--     읽은 알림의 read_at = created_at + 0~1일(base_ts를 넘지 않음).
--   ID는 created_at 순.
-- =============================================================================

\set ON_ERROR_STOP on

\if :{?salt}
\else
	\set salt 232
\endif
\if :{?zero_ratio}
\else
	\set zero_ratio 0.3
\endif
\if :{?notif_mu}
\else
	\set notif_mu 4.4630
\endif
\if :{?notif_sigma}
\else
	\set notif_sigma 1.0
\endif
\if :{?max_per}
\else
	\set max_per 1300
\endif
\if :{?recent_days}
\else
	\set recent_days 30
\endif
\if :{?old_unread}
\else
	\set old_unread 0.2
\endif

SET TIME ZONE 'Asia/Seoul';

\if :{?base_ts}
\else
	SELECT to_char(LOCALTIMESTAMP(0), 'YYYY-MM-DD"T"HH24:MI:SS') AS base_ts \gset
	\echo '경고: base_ts를 주지 않아 실행 시각을 쓴다. 00_common.sql과 같은 값을 넘겨야 결과가 재현된다.'
\endif

SELECT (:max_per)::int BETWEEN 1 AND 4095 AS max_ok \gset
\if :max_ok
\else
	\echo 'max_per는 1~4095여야 한다(key = member_id * 4096 + g)'
	\quit 3
\endif

\echo 'growth/20_notification.sql: salt=' :salt ', base_ts=' :base_ts ', zero_ratio=' :zero_ratio ', notif_mu=' :notif_mu ', notif_sigma=' :notif_sigma ', max_per=' :max_per ', recent_days=' :recent_days ', old_unread=' :old_unread

BEGIN;

-- 1,000만 행을 created_at 순으로 정렬해 ID를 매기므로 이 트랜잭션에서만 올린다.
SET LOCAL work_mem = '128MB';
-- FK 트리거(member_id)를 이 트랜잭션에서만 끈다. 커밋 뒤 FK 고아 검사로 확인한다.
SET LOCAL session_replication_role = replica;

DO $$
BEGIN
	IF EXISTS (SELECT 1 FROM notification) THEN
		RAISE EXCEPTION 'growth/20_notification.sql은 notification이 빈 상태 전제다. 이미 행이 있다. README 7번 초기화(pgdata 볼륨만 삭제) 절차로 비운 뒤 다시 실행한다.';
	END IF;
	IF NOT EXISTS (SELECT 1 FROM feed) THEN
		RAISE EXCEPTION 'growth/10_feed.sql을 먼저 실행해야 한다(feed가 비어 있다).';
	END IF;
	IF EXISTS (SELECT 1 FROM team WHERE description IS DISTINCT FROM 'loadtest:growth') THEN
		RAISE EXCEPTION 'growth 팀이 아닌 팀이 있다. 스트레스 시드와 섞지 않는다.';
	END IF;
END $$;

SELECT (SELECT max(created_at) FROM feed) <= (:'base_ts')::timestamp AS base_ok \gset
\if :base_ok
\else
	\echo 'base_ts가 피드 시각보다 이르다. 00_common.sql·10_feed.sql과 같은 base_ts를 넘긴다.'
	\quit 3
\endif

SELECT format($fmt$
CREATE FUNCTION pg_temp.lt_r(k bigint, stream int) RETURNS double precision
LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $f$
	SELECT ((hashint8extended(k, %s::bigint * 100 + stream) >> 11) & 9007199254740991)::double precision
		/ 9007199254740992.0
$f$
$fmt$, (:salt)::int) \gexec

-- 팀원 slot(0부터, team_member.id 순). growth/10_feed.sql의 slot과 같은 정의.
CREATE TEMP TABLE lt_tm ON COMMIT DROP AS
SELECT
	tm.team_id,
	(row_number() OVER (PARTITION BY tm.team_id ORDER BY tm.id) - 1)::int AS idx,
	tm.id AS tm_id,
	tm.member_id,
	tm.nickname,
	tm.created_at AS joined_at,
	count(*) OVER (PARTITION BY tm.team_id)::int AS size
FROM team_member tm
WHERE tm.status = 'ACTIVE';
CREATE INDEX ON lt_tm (team_id, idx);
ANALYZE lt_tm;

-- 회원별 소속 팀(0부터)
CREATE TEMP TABLE lt_member_team ON COMMIT DROP AS
SELECT
	member_id,
	(row_number() OVER (PARTITION BY member_id ORDER BY team_id) - 1)::int AS k,
	count(*) OVER (PARTITION BY member_id)::int AS n_teams,
	team_id,
	idx AS member_idx,
	size,
	joined_at
FROM lt_tm;
CREATE INDEX ON lt_member_team (member_id, k);
ANALYZE lt_member_team;

-- 팀별 피드(0부터, 시각 순)
CREATE TEMP TABLE lt_team_feed ON COMMIT DROP AS
SELECT
	f.team_id,
	(row_number() OVER (PARTITION BY f.team_id ORDER BY f.created_at, f.id) - 1)::int AS rn,
	f.id AS feed_id,
	f.created_at,
	f.question_id IS NOT NULL AS is_daily,
	a.idx AS author_idx
FROM feed f
JOIN lt_tm a ON a.tm_id = f.team_member_id
WHERE f.deleted_at IS NULL;
CREATE INDEX ON lt_team_feed (team_id, rn);
ANALYZE lt_team_feed;

CREATE TEMP TABLE lt_team_feed_cnt ON COMMIT DROP AS
SELECT team_id, count(*)::int AS cnt FROM lt_team_feed GROUP BY team_id;

DO $$
BEGIN
	IF EXISTS (SELECT 1 FROM team t WHERE NOT EXISTS (SELECT 1 FROM lt_team_feed_cnt c WHERE c.team_id = t.id)) THEN
		RAISE EXCEPTION '피드가 없는 팀이 있다. growth/10_feed.sql(팀마다 피드 최소 1건)을 확인한다.';
	END IF;
END $$;

-- 회원별 알림 수와 읽는 성향 p
CREATE TEMP TABLE lt_member_n ON COMMIT DROP AS
SELECT
	m.id AS member_id,
	CASE WHEN pg_temp.lt_r(m.id, 1) < (:zero_ratio) THEN 0
		ELSE least((:max_per)::int, greatest(1, round(exp((:notif_mu)
			+ (:notif_sigma) * sqrt(-2 * ln(1 - pg_temp.lt_r(m.id, 10))) * cos(2 * pi() * pg_temp.lt_r(m.id, 11))))))::int
	END AS n,
	power(pg_temp.lt_r(m.id, 9), 2.0 / 3.0) AS read_p,
	(SELECT count(*) FROM lt_member_team mt WHERE mt.member_id = m.id)::int AS n_teams
FROM member m;

INSERT INTO notification (
	id, member_id, type, title, body, team_id, feed_id, is_read, read_at, created_at, actor_team_member_id
)
SELECT
	row_number() OVER (ORDER BY z.created_at, z.member_id, z.g),
	z.member_id,
	z.type,
	CASE z.type
		WHEN 'FEED_COMMENT' THEN '새 댓글이 도착했어요'
		WHEN 'FEED_COMMENT_REPLIED' THEN '새 댓글이 도착했어요'
		WHEN 'DAILY_QUESTION_UPLOADED' THEN '오늘의 질문 답변이 올라왔어요'
		WHEN 'FEED_UPLOADED' THEN '새 게시글이 올라왔어요'
		WHEN 'FEED_TAGGED' THEN '게시글에 태그됐어요'
	END,
	a.nickname || CASE z.type
		WHEN 'FEED_COMMENT' THEN '님이 댓글을 남겼어요.'
		WHEN 'FEED_COMMENT_REPLIED' THEN '님이 회원님의 댓글에 답글을 남겼어요.'
		WHEN 'DAILY_QUESTION_UPLOADED' THEN '님이 오늘의 질문에 답변했어요.'
		WHEN 'FEED_UPLOADED' THEN '님이 새 게시글을 올렸어요.'
		WHEN 'FEED_TAGGED' THEN '님이 회원님을 게시글에 태그했어요.'
	END,
	z.team_id,
	z.feed_id,
	z.is_read,
	CASE WHEN z.is_read
		THEN z.created_at + least(interval '1 day', (:'base_ts')::timestamp - z.created_at) * pg_temp.lt_r(z.key, 7)
	END,
	z.created_at,
	a.tm_id
FROM (
	SELECT
		y.*,
		pg_temp.lt_r(y.key, 8) < CASE
			WHEN y.created_at >= (:'base_ts')::timestamp - make_interval(days => :recent_days) THEN y.read_p
			ELSE 1 - (1 - y.read_p) * (:old_unread)
		END AS is_read
	FROM (
		SELECT
			x.*,
			CASE WHEN x.type IN ('FEED_COMMENT', 'FEED_COMMENT_REPLIED')
				THEN (x.member_idx + 1 + floor((x.size - 1) * pg_temp.lt_r(x.key, 5))::int) % x.size
				ELSE x.author_idx
			END AS actor_idx,
			x.from_ts + least(interval '6 hours', (:'base_ts')::timestamp - x.from_ts) * pg_temp.lt_r(x.key, 6) AS created_at
		FROM (
			SELECT
				s.member_id,
				s.g,
				s.key,
				s.read_p,
				mt.team_id,
				mt.member_idx,
				mt.size,
				rf.feed_id,
				rf.author_idx,
				greatest(rf.created_at, mt.joined_at) AS from_ts,
				CASE
					WHEN rf.author_idx = mt.member_idx THEN 'FEED_COMMENT'
					WHEN rf.is_daily THEN 'DAILY_QUESTION_UPLOADED'
					WHEN pg_temp.lt_r(s.key, 4) < 0.35 THEN 'FEED_UPLOADED'
					WHEN pg_temp.lt_r(s.key, 4) < 0.55 THEN 'FEED_TAGGED'
					WHEN pg_temp.lt_r(s.key, 4) < 0.85 THEN 'FEED_COMMENT'
					ELSE 'FEED_COMMENT_REPLIED'
				END AS type
			FROM (
				-- key = member_id * 4096 + g (g <= max_per < 4096, 위에서 확인)
				SELECT n.member_id, n.n_teams, n.read_p, g, n.member_id::bigint * 4096 + g AS key
				FROM lt_member_n n
				CROSS JOIN LATERAL generate_series(1, n.n) AS g
				WHERE n.n > 0
			) s
			JOIN lt_member_team mt
				ON mt.member_id = s.member_id
				AND mt.k = floor(s.n_teams * pg_temp.lt_r(s.key, 2))::int
			JOIN lt_team_feed_cnt c ON c.team_id = mt.team_id
			JOIN lt_team_feed rf ON rf.team_id = mt.team_id AND rf.rn = floor(c.cnt * pg_temp.lt_r(s.key, 3))::int
		) x
	) y
) z
JOIN lt_tm a ON a.team_id = z.team_id AND a.idx = z.actor_idx
ORDER BY 1;

SELECT setval(pg_get_serial_sequence('notification', 'id'), (SELECT max(id) FROM notification));

COMMIT;

-- 통계 갱신과 visibility map 설정(10_feed.sql과 같은 이유).
VACUUM (ANALYZE) notification;

-- ---------------------------------------------------------------------------
-- 확인 쿼리 (계약 완료 조건 전체는 README 6-4의 확인 쿼리)
-- ---------------------------------------------------------------------------
\echo '== 건수, 회원당 알림 수(0건 포함) 평균/최대, 0건 회원 비율, 읽음 비율'
SELECT
	(SELECT count(*) FROM notification) AS notifications,
	(SELECT count(*) FROM member) AS members,
	round((SELECT count(*) FROM notification)::numeric / (SELECT count(*) FROM member), 2) AS avg_per_member,
	(SELECT max(c) FROM (SELECT count(*) AS c FROM notification GROUP BY member_id) x) AS max_per_member,
	round((SELECT count(*) FROM member m WHERE NOT EXISTS (SELECT 1 FROM notification n WHERE n.member_id = m.id))::numeric
		/ (SELECT count(*) FROM member), 4) AS zero_member_ratio,
	(SELECT round(avg(is_read::int), 4) FROM notification) AS read_ratio;

\echo '== type 분포'
SELECT type, count(*) AS notifications, round(count(*)::numeric / sum(count(*)) OVER (), 3) AS ratio
FROM notification GROUP BY type ORDER BY 1;

\echo '== 읽음 분포 (회원별 읽음 비율 표준편차, 안 읽은 알림 중 최근 recent_days일 비율, 전체 알림 중 최근 비율)'
SELECT
	(SELECT round(stddev_pop(r)::numeric, 4) FROM (SELECT avg(is_read::int) AS r FROM notification GROUP BY member_id) x) AS read_ratio_stddev,
	round(avg((created_at >= (:'base_ts')::timestamp - make_interval(days => :recent_days))::int) FILTER (WHERE NOT is_read), 4) AS unread_recent_share,
	round(avg((created_at >= (:'base_ts')::timestamp - make_interval(days => :recent_days))::int), 4) AS all_recent_share
FROM notification;

\echo '== FK 고아·의미 검사 (모두 0이어야 한다)'
SELECT
	(SELECT count(*) FROM notification n WHERE NOT EXISTS (SELECT 1 FROM member m WHERE m.id = n.member_id)) AS orphan_member,
	(SELECT count(*) FROM notification n WHERE NOT EXISTS (
		SELECT 1 FROM team_member tm WHERE tm.member_id = n.member_id AND tm.team_id = n.team_id)) AS team_not_member_team,
	(SELECT count(*) FROM notification n WHERE NOT EXISTS (
		SELECT 1 FROM feed f WHERE f.id = n.feed_id AND f.team_id = n.team_id)) AS feed_not_in_team,
	(SELECT count(*) FROM notification n WHERE NOT EXISTS (
		SELECT 1 FROM team_member tm WHERE tm.id = n.actor_team_member_id AND tm.team_id = n.team_id
			AND tm.member_id <> n.member_id)) AS bad_actor,
	(SELECT count(*) FROM notification n JOIN feed f ON f.id = n.feed_id WHERE n.created_at < f.created_at) AS before_feed,
	(SELECT count(*) FROM notification n
		JOIN (SELECT member_id, min(created_at) AS first_joined_at FROM team_member GROUP BY member_id) j ON j.member_id = n.member_id
		WHERE n.created_at > (:'base_ts')::timestamp OR n.created_at < j.first_joined_at) AS out_of_period,
	(SELECT count(*) FROM notification WHERE is_read <> (read_at IS NOT NULL) OR read_at > (:'base_ts')::timestamp) AS bad_read_at,
	(SELECT count(*) FROM notification WHERE type = 'DAILY_QUESTION_OPENED') AS daily_opened,
	(SELECT count(*) FROM notification WHERE length(title) > 100 OR length(body) > 255) AS too_long;
