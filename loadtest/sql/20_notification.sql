-- =============================================================================
-- 20_notification.sql : 알림 시드 (notification) — #217
--
-- 전제: 00_common.sql, 10_feed.sql을 실행한 직후. notification이 비어 있어야 한다.
--       행이 있으면 아무것도 넣지 않고 에러로 멈춘다. 다시 넣으려면 README의 초기화(down -v) 절차로 비운다.
-- 실행:  docker compose -f loadtest/docker-compose.yml exec -T postgres \
--          psql -U tikitak -d tikitak -f - < loadtest/sql/20_notification.sql
-- 변수:  salt      난수 salt 정수 (기본 217). 같은 값이면 건수·팀·작성자는 같다. 시각은 실행 시점 LOCALTIMESTAMP 기준이라 날짜 경계에 걸리는 데일리 수 등은 조금 달라질 수 있다.
--        min_per   회원당 최소 알림 수 (기본 200)
--        max_per   회원당 최대 알림 수 (기본 2000)
--        days      알림 기간(일) (기본 90)
--
-- 난수는 10_feed.sql과 같은 방식(pg_temp.lt_r = hashint8extended를 [0,1)로)이라 실행 계획과 관계없이 결과가 같다.
--
-- 만드는 것
--   회원마다 n = min_per + floor((max_per - min_per + 1) * u^0.85)건 (평균 약 1,170건, 합계 약 1,000만 건).
--   알림 하나는 그 회원이 속한 팀 하나(여러 팀 소속이면 무작위)와, 그 팀에서 최근 days일 안에 만든 피드 하나를 가리킨다.
--   created_at = 피드 created_at + 0~6시간(지금을 넘지 않음). 그래서 최근 days일 안에, 피드 분포(이번 달이 더 많음)를 따라 퍼진다.
--   type·title·body·actor는 NotificationEventListener의 실제 값과 같은 형식이다(body의 닉네임은 actor의 team_member.nickname).
--     데일리 답변 피드          → DAILY_QUESTION_UPLOADED, actor = 작성자
--     일반 피드 35%             → FEED_UPLOADED, actor = 작성자
--     일반 피드 20%             → FEED_TAGGED, actor = 작성자
--     일반 피드 30%             → FEED_COMMENT, actor = 받는 회원이 아닌 같은 팀 멤버
--     일반 피드 15%             → FEED_COMMENT_REPLIED, actor = 받는 회원이 아닌 같은 팀 멤버
--     작성자가 받는 회원 자신이면 FEED_COMMENT로 바꾸고 actor는 다른 멤버로 둔다(자기 자신에게 업로드·태그 알림은 가지 않음).
--   is_read 약 70%. 읽은 알림의 read_at = created_at + 0~1일(지금을 넘지 않음).
--   ID는 created_at 순(실제처럼 여러 회원의 알림이 시간 순으로 섞여 쌓임).
-- =============================================================================

\set ON_ERROR_STOP on

\if :{?salt}
\else
	\set salt 217
\endif
\if :{?min_per}
\else
	\set min_per 200
\endif
\if :{?max_per}
\else
	\set max_per 2000
\endif
\if :{?days}
\else
	\set days 90
\endif

\echo '20_notification.sql: salt=' :salt ', min_per=' :min_per ', max_per=' :max_per ', days=' :days

SET TIME ZONE 'Asia/Seoul';

BEGIN;

-- 1,000만 행을 created_at 순으로 정렬해 ID를 매기므로 이 트랜잭션에서만 올린다.
SET LOCAL work_mem = '128MB';
-- FK 트리거(member_id)를 이 트랜잭션에서만 끈다(10_feed.sql과 같은 이유). 커밋 뒤 FK 고아 검사로 확인한다.
SET LOCAL session_replication_role = replica;

DO $$
BEGIN
	IF EXISTS (SELECT 1 FROM notification) THEN
		RAISE EXCEPTION '20_notification.sql은 notification이 빈 상태 전제다. 이미 행이 있다. README의 초기화(down -v) 절차로 비운 뒤 다시 실행한다.';
	END IF;
	IF NOT EXISTS (SELECT 1 FROM feed) THEN
		RAISE EXCEPTION '10_feed.sql을 먼저 실행해야 한다(feed가 비어 있다).';
	END IF;
END $$;

SELECT format($fmt$
CREATE FUNCTION pg_temp.lt_r(k bigint, stream int) RETURNS double precision
LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $f$
	SELECT ((hashint8extended(k, %s::bigint * 100 + stream) >> 11) & 9007199254740991)::double precision
		/ 9007199254740992.0
$f$
$fmt$, (:salt)::int) \gexec

-- 팀원 slot(0부터, team_member.id 순). 10_feed.sql의 slot과 같은 정의.
CREATE TEMP TABLE lt_tm ON COMMIT DROP AS
SELECT
	tm.team_id,
	(row_number() OVER (PARTITION BY tm.team_id ORDER BY tm.id) - 1)::int AS idx,
	tm.id AS tm_id,
	tm.member_id,
	tm.nickname,
	count(*) OVER (PARTITION BY tm.team_id)::int AS size
FROM team_member tm
WHERE tm.status = 'ACTIVE';
ANALYZE lt_tm;

-- 회원별 소속 팀(0부터)
CREATE TEMP TABLE lt_member_team ON COMMIT DROP AS
SELECT
	member_id,
	(row_number() OVER (PARTITION BY member_id ORDER BY team_id) - 1)::int AS k,
	count(*) OVER (PARTITION BY member_id)::int AS n_teams,
	team_id,
	idx AS member_idx,
	size
FROM lt_tm;
ANALYZE lt_member_team;

-- 팀별 최근 days일 피드(0부터, 시각 순)
CREATE TEMP TABLE lt_recent ON COMMIT DROP AS
SELECT
	f.team_id,
	(row_number() OVER (PARTITION BY f.team_id ORDER BY f.created_at, f.id) - 1)::int AS rn,
	f.id AS feed_id,
	f.created_at,
	f.question_id IS NOT NULL AS is_daily,
	a.idx AS author_idx
FROM feed f
JOIN lt_tm a ON a.tm_id = f.team_member_id
WHERE f.deleted_at IS NULL
	AND f.created_at >= LOCALTIMESTAMP - make_interval(days => :days);
ANALYZE lt_recent;

CREATE TEMP TABLE lt_recent_cnt ON COMMIT DROP AS
SELECT team_id, count(*)::int AS cnt FROM lt_recent GROUP BY team_id;

DO $$
BEGIN
	IF EXISTS (SELECT 1 FROM team t WHERE NOT EXISTS (SELECT 1 FROM lt_recent_cnt c WHERE c.team_id = t.id)) THEN
		RAISE EXCEPTION '최근 기간 안에 피드가 없는 팀이 있다. 10_feed.sql(팀마다 이번 달 피드 최소 1건)을 확인한다.';
	END IF;
END $$;

-- 회원별 알림 수
CREATE TEMP TABLE lt_member_n ON COMMIT DROP AS
SELECT
	m.id AS member_id,
	(:min_per) + floor(((:max_per) - (:min_per) + 1) * power(pg_temp.lt_r(m.id, 1), 0.85))::int AS n,
	(SELECT count(*) FROM lt_member_team mt WHERE mt.member_id = m.id)::int AS n_teams
FROM member m;

INSERT INTO notification (
	id, member_id, type, title, body, team_id, feed_id, is_read, read_at, created_at, actor_team_member_id
)
SELECT
	row_number() OVER (ORDER BY y.created_at, y.member_id, y.g),
	y.member_id,
	y.type,
	CASE y.type
		WHEN 'FEED_COMMENT' THEN '새 댓글이 도착했어요'
		WHEN 'FEED_COMMENT_REPLIED' THEN '새 댓글이 도착했어요'
		WHEN 'DAILY_QUESTION_UPLOADED' THEN '오늘의 질문 답변이 올라왔어요'
		WHEN 'FEED_UPLOADED' THEN '새 게시글이 올라왔어요'
		WHEN 'FEED_TAGGED' THEN '게시글에 태그됐어요'
	END,
	a.nickname || CASE y.type
		WHEN 'FEED_COMMENT' THEN '님이 댓글을 남겼어요.'
		WHEN 'FEED_COMMENT_REPLIED' THEN '님이 회원님의 댓글에 답글을 남겼어요.'
		WHEN 'DAILY_QUESTION_UPLOADED' THEN '님이 오늘의 질문에 답변했어요.'
		WHEN 'FEED_UPLOADED' THEN '님이 새 게시글을 올렸어요.'
		WHEN 'FEED_TAGGED' THEN '님이 회원님을 게시글에 태그했어요.'
	END,
	y.team_id,
	y.feed_id,
	y.is_read,
	CASE WHEN y.is_read
		THEN y.created_at + least(interval '1 day', LOCALTIMESTAMP - y.created_at) * pg_temp.lt_r(y.key, 7)
	END,
	y.created_at,
	a.tm_id
FROM (
	SELECT
		x.*,
		CASE WHEN x.type IN ('FEED_COMMENT', 'FEED_COMMENT_REPLIED')
			THEN (x.member_idx + 1 + floor((x.size - 1) * pg_temp.lt_r(x.key, 5))::int) % x.size
			ELSE x.author_idx
		END AS actor_idx
	FROM (
		SELECT
			s.member_id,
			s.g,
			s.key,
			mt.team_id,
			mt.member_idx,
			mt.size,
			rf.feed_id,
			rf.author_idx,
			CASE
				WHEN rf.author_idx = mt.member_idx THEN 'FEED_COMMENT'
				WHEN rf.is_daily THEN 'DAILY_QUESTION_UPLOADED'
				WHEN pg_temp.lt_r(s.key, 4) < 0.35 THEN 'FEED_UPLOADED'
				WHEN pg_temp.lt_r(s.key, 4) < 0.55 THEN 'FEED_TAGGED'
				WHEN pg_temp.lt_r(s.key, 4) < 0.85 THEN 'FEED_COMMENT'
				ELSE 'FEED_COMMENT_REPLIED'
			END AS type,
			rf.created_at + least(interval '6 hours', LOCALTIMESTAMP - rf.created_at) * pg_temp.lt_r(s.key, 6) AS created_at,
			pg_temp.lt_r(s.key, 8) < 0.7 AS is_read
		FROM (
			-- key = member_id * 4096 + g (g <= max_per < 4096 전제)
			SELECT n.member_id, n.n_teams, g, n.member_id::bigint * 4096 + g AS key
			FROM lt_member_n n
			CROSS JOIN LATERAL generate_series(1, n.n) AS g
		) s
		JOIN lt_member_team mt
			ON mt.member_id = s.member_id
			AND mt.k = floor(s.n_teams * pg_temp.lt_r(s.key, 2))::int
		JOIN lt_recent_cnt c ON c.team_id = mt.team_id
		JOIN lt_recent rf ON rf.team_id = mt.team_id AND rf.rn = floor(c.cnt * pg_temp.lt_r(s.key, 3))::int
	) x
) y
JOIN lt_tm a ON a.team_id = y.team_id AND a.idx = y.actor_idx
ORDER BY 1;

SELECT setval(pg_get_serial_sequence('notification', 'id'), (SELECT max(id) FROM notification));

COMMIT;

-- 통계 갱신과 visibility map 설정(10_feed.sql과 같은 이유).
VACUUM (ANALYZE) notification;

-- ---------------------------------------------------------------------------
-- 확인 쿼리
-- ---------------------------------------------------------------------------
\echo '== 건수, 회원당 알림 수 최소/평균/최대, 읽음 비율, 기간'
SELECT
	count(*) AS notifications,
	(SELECT min(c) FROM (SELECT count(*) AS c FROM notification GROUP BY member_id) x) AS min_per_member,
	(SELECT round(avg(c)) FROM (SELECT count(*) AS c FROM notification GROUP BY member_id) x) AS avg_per_member,
	(SELECT max(c) FROM (SELECT count(*) AS c FROM notification GROUP BY member_id) x) AS max_per_member,
	(SELECT count(*) FROM member m WHERE NOT EXISTS (SELECT 1 FROM notification n WHERE n.member_id = m.id)) AS members_without,
	round(avg(is_read::int), 3) AS read_ratio,
	min(created_at) AS min_created_at,
	max(created_at) AS max_created_at
FROM notification;

\echo '== 회원별 읽음 비율 범위 (최소/최대)'
SELECT round(min(r), 3) AS min_read_ratio, round(max(r), 3) AS max_read_ratio
FROM (SELECT avg(is_read::int) AS r FROM notification GROUP BY member_id) x;

\echo '== type 분포'
SELECT type, count(*) AS notifications, round(count(*)::numeric / sum(count(*)) OVER (), 3) AS ratio
FROM notification GROUP BY type ORDER BY 1;

\echo '== 큰 팀 멤버 알림 수 (member_id, 전체, 안 읽음)'
SELECT n.member_id, count(*) AS notifications, count(*) FILTER (WHERE NOT n.is_read) AS unread
FROM notification n
WHERE n.member_id IN (SELECT tm.member_id FROM team_member tm WHERE tm.team_id IN (SELECT id FROM team WHERE description = 'loadtest:big'))
GROUP BY n.member_id
ORDER BY n.member_id;

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
	-- 시드 시각과 확인 시각이 달라서 하한에 1일 여유를 둔다
	(SELECT count(*) FROM notification
		WHERE created_at > LOCALTIMESTAMP OR created_at < LOCALTIMESTAMP - make_interval(days => :days + 1)) AS out_of_period,
	(SELECT count(*) FROM notification WHERE is_read <> (read_at IS NOT NULL) OR read_at > LOCALTIMESTAMP) AS bad_read_at,
	(SELECT count(*) FROM notification WHERE length(title) > 100 OR length(body) > 255) AS too_long;
