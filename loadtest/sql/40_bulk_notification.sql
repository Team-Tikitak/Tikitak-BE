-- =============================================================================
-- 40_bulk_notification.sql : 대량 알림 측정용 디바이스 토큰 시드 (member_device_token)  — #215
--
-- 주의: #217의 20_notification.sql과 같은 DB에서 쓰지 않는다.
--       이 파일은 시작할 때 notification을 TRUNCATE하므로 20_notification.sql이 넣은 알림 약 1,000만 건이 지워진다.
--       #215 측정 DB는 00_common.sql → 40_bulk_notification.sql만 실행한다(10·20·30번은 실행하지 않는다).
--
-- 전제: 00_common.sql이 회원·팀·팀원을 넣은 DB. 앱(배치)은 꺼져 있어야 한다.
-- 실행:  docker compose -f loadtest/docker-compose.yml exec -T postgres \
--          psql -U tikitak -d tikitak -f - < loadtest/sql/40_bulk_notification.sql
-- 변수:  seed  setseed 값, -1.0 ~ 1.0 (기본 0.215). 같은 값이면 같은 토큰 분포가 나온다.
--
-- 매 측정 전에 다시 실행한다(run-batch-measure.sh가 실행한다). 앞 회차 배치가 쌓은 알림과,
-- 가짜 발송기가 INVALID_TOKEN으로 판정해 앱이 지운 토큰이 모두 처음 상태로 돌아간다.
--
-- 하는 일
--   1) TRUNCATE notification, member_device_token RESTART IDENTITY
--      (두 테이블을 FK로 참조하는 테이블은 없다. identity 시퀀스도 1부터 다시 시작한다)
--   2) status = 'ACTIVE'인 회원마다 토큰 0개 10% / 1개 75% / 2개 15%
--      토큰마다 platform ANDROID 60% / IOS 40%
--      fcm_token = 'loadtest-' || member_id || '-' || n  (n = 1, 2. unique, 512자 이하)
--      created_at = updated_at = LOCALTIMESTAMP (세션 타임존 Asia/Seoul)
--   id는 identity 기본값으로 넣는다(시퀀스를 따로 맞출 필요가 없다).
--   난수는 회원 ID 순으로 정렬한 뒤 random()을 부르므로 같은 seed·같은 회원이면 같은 결과가 나온다.
-- =============================================================================

\set ON_ERROR_STOP on

\if :{?seed}
\else
	\set seed 0.215
\endif

\echo '40_bulk_notification.sql: seed=' :seed

BEGIN;

TRUNCATE notification, member_device_token RESTART IDENTITY;

SELECT setseed(:seed);

-- 회원 ID 순으로 정렬한 다음 바깥에서 random()을 불러 호출 순서를 고정한다.
CREATE TEMP TABLE lt_device ON COMMIT DROP AS
SELECT
	x.member_id,
	CASE
		WHEN x.r_count < 0.10 THEN 0
		WHEN x.r_count < 0.85 THEN 1
		ELSE 2
	END AS token_count,
	x.r_platform1,
	x.r_platform2
FROM (
	SELECT s.member_id, random() AS r_count, random() AS r_platform1, random() AS r_platform2
	FROM (SELECT id AS member_id FROM member WHERE status = 'ACTIVE' ORDER BY id) s
) x;

INSERT INTO member_device_token (member_id, fcm_token, platform, created_at, updated_at)
SELECT
	d.member_id,
	'loadtest-' || d.member_id || '-' || n,
	CASE WHEN (CASE WHEN n = 1 THEN d.r_platform1 ELSE d.r_platform2 END) < 0.6 THEN 'ANDROID' ELSE 'IOS' END,
	LOCALTIMESTAMP,
	LOCALTIMESTAMP
FROM lt_device d
CROSS JOIN LATERAL generate_series(1, d.token_count) AS n
ORDER BY d.member_id, n;

COMMIT;

ANALYZE member_device_token;
ANALYZE notification;

-- ---------------------------------------------------------------------------
-- 확인 쿼리
-- ---------------------------------------------------------------------------
\echo '== 건수'
SELECT
	(SELECT count(*) FROM member) AS members,
	(SELECT count(*) FROM member WHERE status = 'ACTIVE') AS active_members,
	(SELECT count(*) FROM member_device_token) AS tokens,
	(SELECT count(*) FROM notification) AS notifications;

\echo '== ACTIVE 회원의 토큰 수 분포 (목표 0개 0.10 / 1개 0.75 / 2개 0.15)'
SELECT c.token_count, count(*) AS members, round(count(*)::numeric / sum(count(*)) OVER (), 3) AS ratio
FROM (
	SELECT m.id, count(t.id) AS token_count
	FROM member m
	LEFT JOIN member_device_token t ON t.member_id = m.id
	WHERE m.status = 'ACTIVE'
	GROUP BY m.id
) c
GROUP BY c.token_count
ORDER BY c.token_count;

\echo '== 플랫폼 비율 (목표 ANDROID 0.6 / IOS 0.4)'
SELECT platform, count(*) AS tokens, round(count(*)::numeric / sum(count(*)) OVER (), 3) AS ratio
FROM member_device_token
GROUP BY platform
ORDER BY platform;

\echo '== 무결성 검사 (dup_fcm_token, too_long_token, tokens_of_non_active_member는 0이어야 한다)'
SELECT
	(SELECT count(*) FROM (SELECT fcm_token FROM member_device_token GROUP BY fcm_token HAVING count(*) > 1) x) AS dup_fcm_token,
	(SELECT count(*) FROM member_device_token WHERE length(fcm_token) > 512) AS too_long_token,
	(SELECT count(*) FROM member_device_token t JOIN member m ON m.id = t.member_id WHERE m.status <> 'ACTIVE') AS tokens_of_non_active_member,
	(SELECT max(length(fcm_token)) FROM member_device_token) AS max_token_length;

-- 배치(TeamMemberRepository.findDistinctMemberIdsWithActiveMembership)와 같은 조건
\echo '== 배치가 받는 사람 수 (recipients = 배치 시작 로그 recipientCount 예상값, recipient_tokens = 가짜 발송기가 받는 토큰 수 예상값)'
WITH r AS (
	SELECT DISTINCT m.id
	FROM team_member tm
	JOIN member m ON m.id = tm.member_id
	JOIN team t ON t.id = tm.team_id
	WHERE m.status = 'ACTIVE'
		AND tm.status = 'ACTIVE'
		AND t.status = 'ACTIVE'
)
SELECT
	(SELECT count(*) FROM r) AS recipients,
	(SELECT count(DISTINCT t.member_id) FROM member_device_token t WHERE t.member_id IN (SELECT id FROM r)) AS recipients_with_token,
	(SELECT count(*) FROM member_device_token t WHERE t.member_id IN (SELECT id FROM r)) AS recipient_tokens;
