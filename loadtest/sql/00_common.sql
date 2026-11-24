-- =============================================================================
-- 00_common.sql : 공통 시드 (member, team, team_member)  — #217, #215 공용
--
-- 전제: **빈 DB**. 앱을 한 번 기동해 Flyway V1~V25가 적용된 직후에만 실행한다.
--       member/team/team_member에 행이 하나라도 있으면 아무것도 넣지 않고 에러로 멈춘다.
--       다시 넣으려면 README의 초기화 절차(docker compose down -v → up -d → 앱 기동)로 비운다.
-- 실행:  docker compose -f loadtest/docker-compose.yml exec -T postgres \
--          psql -U tikitak -d tikitak -v normal_teams=2000 -f - < loadtest/sql/00_common.sql
-- 변수:  normal_teams  일반 팀 수 (기본 2000)
--        seed          setseed 값, -1.0 ~ 1.0 (기본 0.217). 같은 값이면 같은 결과가 나온다.
--
-- 만드는 것 (ID를 명시해서 넣고, 마지막에 identity 시퀀스를 맞춘다)
--   team        : id 1~3 = 큰 팀(name 'loadtest-big-N', description 'loadtest:big', 멤버 6명)
--                 id 4~  = 일반 팀(name 'loadtest-team-NNNNN', description 'loadtest:normal', 멤버 3~6명)
--   team_member : 팀마다 slot 1이 OWNER, 나머지 MEMBER. 모두 ACTIVE.
--                 profile_img_url은 약 50%만 채운다(hashint4 기준, 결정적).
--   member      : 모두 ACTIVE, onboarding_completed=true, profile_character_type 채움, 약관·개인정보 동의.
--                 provider_id는 'loadtest-<id>'라 (social_provider, provider_id) 부분 유니크를 지킨다.
--                 active_team_id = 그 회원의 "주 소속" 팀.
--   여러 팀 소속: 큰 팀 전부와 일반 팀 약 20%는 마지막 slot을 "게스트"로 채운다.
--                 게스트는 다른 팀(team_id + 전체 팀 수/2 위치의 팀)의 slot 2 회원이다.
--                 팀 → 반대편 팀 대응이 1:1이라 게스트가 된 회원(약 400명)은 정확히 2개 팀에 속하고,
--                 한 팀에 같은 회원이 두 번 들어가지 않는다. 큰 팀의 게스트는 일반 팀이 주 소속인 회원이다.
-- 시각: TIMESTAMP(타임존 없음) 컬럼에 LOCALTIMESTAMP(세션 타임존 Asia/Seoul) 기준 값을 넣는다.
--       팀은 약 760~790일 전에 만들어진 것으로 둔다(피드 시드가 최근 2년에 분포하므로).
-- =============================================================================

\set ON_ERROR_STOP on

\if :{?normal_teams}
\else
	\set normal_teams 2000
\endif
\if :{?seed}
\else
	\set seed 0.217
\endif

SELECT (:normal_teams)::int >= 1 AS normal_teams_ok,
	3 + (:normal_teams)::int AS total_teams \gset
\if :normal_teams_ok
\else
	\echo 'normal_teams는 1 이상이어야 한다'
	\quit 3
\endif

\echo '00_common.sql: normal_teams=' :normal_teams ', total_teams=' :total_teams ', seed=' :seed

BEGIN;

DO $$
BEGIN
	IF EXISTS (SELECT 1 FROM member)
		OR EXISTS (SELECT 1 FROM team)
		OR EXISTS (SELECT 1 FROM team_member) THEN
		RAISE EXCEPTION '00_common.sql은 빈 DB 전제다. member/team/team_member에 이미 행이 있다. README의 초기화(down -v) 절차로 비운 뒤 다시 실행한다.';
	END IF;
END $$;

SELECT setseed(:seed);

-- 팀별 크기와 게스트 slot 여부. generate_series 한 번만 훑으므로 random() 호출 순서가 고정된다.
CREATE TEMP TABLE lt_team ON COMMIT DROP AS
SELECT
	x.team_id,
	x.team_id <= 3 AS is_big,
	CASE WHEN x.team_id <= 3 THEN 6 ELSE 3 + floor(x.r_size * 4)::int END AS size,
	(x.team_id <= 3 OR x.r_guest < 0.2) AS has_guest,
	date_trunc('day', LOCALTIMESTAMP) - make_interval(days => 760 + (x.team_id % 30)::int) AS created_at
FROM (
	SELECT t AS team_id, random() AS r_size, random() AS r_guest
	FROM generate_series(1, :total_teams) AS t
) x;

-- 팀 × slot. team_member_id는 (team_id, slot_no) 순서, 주 소속 회원 ID는 게스트가 아닌 slot의 순번.
CREATE TEMP TABLE lt_slot ON COMMIT DROP AS
WITH s AS (
	SELECT t.team_id, g AS slot_no, (t.has_guest AND g = t.size) AS is_guest, t.created_at AS team_created_at
	FROM lt_team t
	CROSS JOIN LATERAL generate_series(1, t.size) AS g
)
SELECT
	s.*,
	row_number() OVER (ORDER BY s.team_id, s.slot_no) AS team_member_id,
	CASE WHEN NOT s.is_guest
		THEN row_number() OVER (PARTITION BY s.is_guest ORDER BY s.team_id, s.slot_no)
	END AS member_id
FROM s;

-- 게스트 slot: 반대편 팀의 slot 2 회원(항상 주 소속 회원, 게스트는 size >= 3인 마지막 slot이므로)
UPDATE lt_slot g
SET member_id = d.member_id
FROM lt_slot d
WHERE g.is_guest
	AND d.slot_no = 2
	AND d.team_id = ((g.team_id - 1 + (:total_teams) / 2) % (:total_teams)) + 1;

INSERT INTO team (id, name, description, team_img_url, status, created_at, updated_at, deleted_at)
SELECT
	t.team_id,
	CASE WHEN t.is_big THEN 'loadtest-big-' || t.team_id ELSE 'loadtest-team-' || lpad(t.team_id::text, 5, '0') END,
	CASE WHEN t.is_big THEN 'loadtest:big' ELSE 'loadtest:normal' END,
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
	s.member_id,
	'loadtest+' || s.member_id || '@tikitak.invalid',
	'부하회원' || s.member_id,
	'tak' || s.member_id,
	NULL,
	(ARRAY['GOOGLE', 'KAKAO', 'APPLE'])[(s.member_id % 3) + 1],
	'loadtest-' || s.member_id,
	'ACTIVE',
	TRUE,
	TRUE,
	s.team_created_at,
	s.team_created_at,
	s.team_created_at,
	NULL,
	s.team_id,
	(ARRAY['TAK_LEADER', 'TAK_SPARK', 'TAK_BURNER', 'TAK_BUILDER', 'TAK_FREE', 'TAK_CARE'])[(s.member_id % 6) + 1],
	TRUE
FROM lt_slot s
WHERE NOT s.is_guest
ORDER BY s.member_id;

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
	CASE WHEN s.slot_no = 1 THEN 'OWNER' ELSE 'MEMBER' END,
	'ACTIVE',
	s.team_created_at + make_interval(hours => s.slot_no::int),
	NULL,
	LOCALTIMESTAMP - make_interval(hours => (s.team_member_id % 72)::int),
	NULL
FROM lt_slot s
ORDER BY s.team_member_id;

-- ID를 직접 넣었으므로 identity 시퀀스를 맞춘다(앱이 이후 INSERT할 때 충돌하지 않게).
SELECT setval(pg_get_serial_sequence('team', 'id'), (SELECT max(id) FROM team));
SELECT setval(pg_get_serial_sequence('member', 'id'), (SELECT max(id) FROM member));
SELECT setval(pg_get_serial_sequence('team_member', 'id'), (SELECT max(id) FROM team_member));

COMMIT;

ANALYZE team;
ANALYZE member;
ANALYZE team_member;

-- ---------------------------------------------------------------------------
-- 확인 쿼리
-- ---------------------------------------------------------------------------
\echo '== 건수'
SELECT
	(SELECT count(*) FROM team) AS teams,
	(SELECT count(*) FROM team WHERE description = 'loadtest:big') AS big_teams,
	(SELECT count(*) FROM member) AS members,
	(SELECT count(*) FROM team_member) AS team_members;

\echo '== 팀별 멤버 수 분포 (kind, 멤버 수, 팀 수)'
SELECT
	CASE WHEN t.description = 'loadtest:big' THEN 'big' ELSE 'normal' END AS kind,
	c.member_count,
	count(*) AS teams
FROM team t
JOIN (SELECT team_id, count(*) AS member_count FROM team_member GROUP BY team_id) c ON c.team_id = t.id
GROUP BY 1, 2
ORDER BY 1, 2;

\echo '== 회원별 소속 팀 수 분포'
SELECT team_count, count(*) AS members
FROM (SELECT member_id, count(*) AS team_count FROM team_member GROUP BY member_id) x
GROUP BY team_count
ORDER BY team_count;

\echo '== 큰 팀 멤버 (team_id, member_id, role, 소속 팀 수)'
SELECT tm.team_id, tm.member_id, tm.role,
	(SELECT count(*) FROM team_member o WHERE o.member_id = tm.member_id) AS member_team_count
FROM team_member tm
JOIN team t ON t.id = tm.team_id AND t.description = 'loadtest:big'
ORDER BY tm.team_id, tm.id;

\echo '== 무결성 검사 (모두 0이어야 한다)'
SELECT
	(SELECT count(*) FROM (
		SELECT team_id FROM team_member GROUP BY team_id
		HAVING count(*) FILTER (WHERE role = 'OWNER') <> 1) x) AS teams_owner_not_1,
	(SELECT count(*) FROM (
		SELECT team_id, member_id FROM team_member GROUP BY team_id, member_id HAVING count(*) > 1) x) AS dup_team_member,
	(SELECT count(*) FROM member m
		WHERE NOT EXISTS (SELECT 1 FROM team_member tm
			WHERE tm.member_id = m.id AND tm.team_id = m.active_team_id AND tm.status = 'ACTIVE')) AS bad_active_team,
	(SELECT count(*) FROM member
		WHERE status <> 'ACTIVE' OR NOT onboarding_completed OR profile_character_type IS NULL
			OR NOT terms_agreed OR NOT privacy_agreed) AS bad_member_state;

\echo '== team_member.profile_img_url 채움 비율'
SELECT round(avg((profile_img_url IS NOT NULL)::int), 3) AS profile_img_filled_ratio
FROM team_member;
