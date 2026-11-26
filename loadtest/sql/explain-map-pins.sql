-- =============================================================================
-- explain-map-pins.sql : 지도 핀 쿼리 개선 전·후보 L·후보 D의 EXPLAIN과 결과 비교 (읽기 전용) — #220
--
-- 쿼리 (FeedRepository.findMapPinsByTeamId)
--   before : 개선 전. WITH ranked(윈도 함수 COUNT OVER + ROW_NUMBER) → rn = 1 (origin/develop 2579dba)
--   cand_l : 후보 L. 장소별 GROUP BY 개수 + 장소마다 CROSS JOIN LATERAL(ORDER BY created_at DESC, id DESC LIMIT 1)
--   cand_d : 후보 D. DISTINCT ON (place_id)로 최신 피드 + 같은 조건의 GROUP BY 개수를 place_id로 join
--   세 쿼리 모두 팀 id를 bind 파라미터($1 bigint)로 받는다(PREPARE → EXPLAIN EXECUTE).
--   앱(Hibernate)은 같은 :teamId를 $1, $2 두 개로 펼쳐 보내지만(개선 후 pgss 문장), 이 파일은 $1 하나를 두 곳에 쓴다.
--   custom plan에서는 같은 값이라 계획이 같다. -v generic 경로에서도 같은지는 미확인이다.
--
-- 변수:  team_id   EXPLAIN 대상 팀 id (필수. 큰 팀 1, 일반 팀 4)
--        generic   값이 있으면 plan_cache_mode = force_generic_plan으로 EXPLAIN한다
--                  (JDBC 서버 prepare 뒤 generic plan이 쓰일 경우 확인용)
--        compare   값이 있으면 (iv) 전체 팀 결과 비교를 실행한다(실행 시간 미측정. 큰 팀은 before가 팀당 약 120ms, 베이스라인 7-1)
--
-- 출력:  (0) 새 인덱스 idx_feed_team_place_latest 존재 여부와 indcheckxmin
--        (i)~(iii) EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
--        (iv) 전체 팀에 대해 before와 cand_l, before와 cand_d를 EXCEPT ALL로 양방향 비교한 행 수. 모두 0이어야 한다.
--             핀은 order_index 0 이미지가 두 행이면 두 행이 되므로, 행 중복까지 보려고 EXCEPT가 아니라 EXCEPT ALL을 쓴다.
--             비교 키는 팀 id + 모든 반환 컬럼이다.
--
-- 실행 (DB는 바꾸지 않는다. 부하가 없을 때 실행한다)
--   컨테이너 안에 이 파일이 없어 psql의 \i 대신 stdin으로 넘긴다.
--   같은 파일을 두 번 이어서 넘겨 2회차(캐시가 찬 상태)를 기록한다. 파일 맨 앞에서 DEALLOCATE ALL을 해서 다시 PREPARE할 수 있다.
--   잠금 1 — DB가 V25 상태(새 인덱스 없음)이고 앱을 끈 상태: 한 트랜잭션 안에서 V26 인덱스를 만들고 EXPLAIN한 뒤 ROLLBACK한다.
--       bash loadtest/scripts/measure-lock.sh bash -c '{ echo "BEGIN;"; \
--           cat src/main/resources/db/migration/V26__add_feed_team_place_latest_index.sql; \
--           cat loadtest/sql/explain-map-pins.sql loadtest/sql/explain-map-pins.sql; \
--           echo "ROLLBACK;"; } \
--         | docker compose -f loadtest/docker-compose.yml exec -T postgres \
--             psql -U tikitak -d tikitak -v ON_ERROR_STOP=1 -v team_id=1 -f - \
--         > loadtest/results/raw/<시각>-lock1-explain-team1.txt'
--     team_id=4도 같은 형태로 실행한다.
--     전체 팀 비교는 같은 BEGIN … ROLLBACK 형태에서 파일을 한 번만 넘기고 -v team_id=1 -v compare=1 을 붙인다
--     ((i)~(iii)은 team_id 팀으로 한 번 더 나오고, 그 뒤에 (iv)가 나온다).
--     에러로 psql이 멈추면 연결이 끊기며 트랜잭션도 롤백된다. 끝난 뒤
--       SELECT count(*) FROM pg_indexes WHERE indexname = 'idx_feed_team_place_latest';  -- 0이어야 한다
--     로 인덱스가 남지 않았는지 확인한다.
--   잠금 2 — 앱을 띄운 직후, 웜업·측정 부하를 걸기 전에 실행한다(앱은 떠 있지만 요청이 없다).
--     BEGIN/ROLLBACK과 V26 줄 없이 파일만 두 번 넘긴다.
--       cat loadtest/sql/explain-map-pins.sql loadtest/sql/explain-map-pins.sql \
--         | docker compose -f loadtest/docker-compose.yml exec -T postgres \
--             psql -U tikitak -d tikitak -v ON_ERROR_STOP=1 -v team_id=1 -f - \
--         > loadtest/results/raw/<시각>-before-explain-team1.txt
--     개선 전(V25): before만 의미가 있다(cand_l·cand_d는 새 인덱스 없이 실행된다. (0) 출력이 0 rows인 것으로 구분한다).
--     개선 후(V26 적용 뒤): 같은 명령으로 <시각>-after-explain-team<id>.txt에 남긴다.
--       전체 팀 비교는 파일을 한 번만 넘기고 -v team_id=1 -v compare=1 을 붙여 <시각>-after-compare.txt에 남긴다.
--
-- 주의
--   - indcheckxmin이 true이면, 같은 트랜잭션에서 만든 인덱스를 그 트랜잭션의 쿼리가 쓰지 못한다
--     (힙에 깨진 HOT 체인이 있을 때 PostgreSQL이 설정한다). 이때 잠금 1의 cand_l·cand_d 계획은 새 인덱스 없이
--     나온 것이므로 판정에 쓰지 않고, 인덱스를 실제로 만든 뒤(잠금 2의 개선 후) 다시 잰다.
--   - 결과 문서에 옮길 때는 원시 파일 경로, 실행 시각, 회차(1·2회차)를 같이 적는다.
-- =============================================================================

\set ON_ERROR_STOP on

\if :{?team_id}
\else
	\echo 'explain-map-pins.sql: -v team_id=<팀 id> 가 필요하다.'
	\quit
\endif

DEALLOCATE ALL;

\echo '=== (0) 새 인덱스 idx_feed_team_place_latest'
SELECT
	c.relname AS index_name,
	i.indisvalid,
	i.indisready,
	i.indcheckxmin,
	pg_size_pretty(pg_relation_size(c.oid)) AS index_size
FROM pg_index i
JOIN pg_class c ON c.oid = i.indexrelid
WHERE c.relname = 'idx_feed_team_place_latest';

SELECT
	:team_id AS team_id,
	count(*) FILTER (WHERE deleted_at IS NULL AND place_id IS NOT NULL) AS active_feeds_with_place,
	count(DISTINCT place_id) FILTER (WHERE deleted_at IS NULL AND place_id IS NOT NULL) AS active_places
FROM feed
WHERE team_id = :team_id;

PREPARE before_pins(bigint) AS
WITH ranked AS (
	SELECT
		f.place_id,
		f.id AS feed_id,
		COUNT(*) OVER (PARTITION BY f.place_id) AS feed_count,
		ROW_NUMBER() OVER (PARTITION BY f.place_id
						ORDER BY f.created_at DESC, f.id DESC) AS rn
	FROM feed f
	WHERE f.team_id = $1
	AND f.deleted_at IS NULL
	AND f.place_id IS NOT NULL
)
SELECT
	p.external_place_id AS externalPlaceId,
	p.name,
	p.latitude,
	p.longitude,
	p.address,
	r.feed_count AS feedCount,
	fi.img_url AS thumbnailUrl
FROM ranked r
JOIN place p ON p.id = r.place_id
LEFT JOIN feed_image fi ON fi.feed_id = r.feed_id
AND fi.order_index = 0
WHERE r.rn = 1;

PREPARE cand_l_pins(bigint) AS
WITH counts AS (
	SELECT
		f.place_id,
		COUNT(*) AS feed_count
	FROM feed f
	WHERE f.team_id = $1
	AND f.deleted_at IS NULL
	AND f.place_id IS NOT NULL
	GROUP BY f.place_id
)
SELECT
	p.external_place_id AS externalPlaceId,
	p.name,
	p.latitude,
	p.longitude,
	p.address,
	c.feed_count AS feedCount,
	fi.img_url AS thumbnailUrl
FROM counts c
CROSS JOIN LATERAL (
	SELECT f.id AS feed_id
	FROM feed f
	WHERE f.team_id = $1
	AND f.place_id = c.place_id
	AND f.deleted_at IS NULL
	ORDER BY f.created_at DESC, f.id DESC
	LIMIT 1
) latest
JOIN place p ON p.id = c.place_id
LEFT JOIN feed_image fi ON fi.feed_id = latest.feed_id
AND fi.order_index = 0;

PREPARE cand_d_pins(bigint) AS
WITH latest AS (
	SELECT DISTINCT ON (f.place_id)
		f.place_id,
		f.id AS feed_id
	FROM feed f
	WHERE f.team_id = $1
	AND f.deleted_at IS NULL
	AND f.place_id IS NOT NULL
	ORDER BY f.place_id, f.created_at DESC, f.id DESC
),
counts AS (
	SELECT
		f.place_id,
		COUNT(*) AS feed_count
	FROM feed f
	WHERE f.team_id = $1
	AND f.deleted_at IS NULL
	AND f.place_id IS NOT NULL
	GROUP BY f.place_id
)
SELECT
	p.external_place_id AS externalPlaceId,
	p.name,
	p.latitude,
	p.longitude,
	p.address,
	c.feed_count AS feedCount,
	fi.img_url AS thumbnailUrl
FROM latest l
JOIN counts c ON c.place_id = l.place_id
JOIN place p ON p.id = l.place_id
LEFT JOIN feed_image fi ON fi.feed_id = l.feed_id
AND fi.order_index = 0;

\if :{?generic}
	SET plan_cache_mode = force_generic_plan;
	\echo '--- plan_cache_mode = force_generic_plan'
\endif

\echo '=== (i) before (WITH ranked)'
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE before_pins(:team_id);

\echo '=== (ii) cand_l (GROUP BY + CROSS JOIN LATERAL)'
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE cand_l_pins(:team_id);

\echo '=== (iii) cand_d (DISTINCT ON + GROUP BY)'
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE cand_d_pins(:team_id);

\if :{?generic}
	RESET plan_cache_mode;
\endif

\if :{?compare}
\echo '=== (iv) 전체 팀 결과 비교 (EXCEPT ALL 양방향, 모두 0이어야 한다)'
-- 팀마다 세 쿼리를 그대로(팀 id만 t.id로 바꿔) LATERAL로 실행한다.
-- 같은 세션에서 다시 실행할 수 있게 먼저 지운다. 끝에서도 지운다(BEGIN … ROLLBACK이면 어차피 남지 않는다).
DROP TABLE IF EXISTS map_pins_before, map_pins_cand_l, map_pins_cand_d;

CREATE TEMP TABLE map_pins_before AS
SELECT t.id AS team_id, q.*
FROM team t
CROSS JOIN LATERAL (
	WITH ranked AS (
		SELECT
			f.place_id,
			f.id AS feed_id,
			COUNT(*) OVER (PARTITION BY f.place_id) AS feed_count,
			ROW_NUMBER() OVER (PARTITION BY f.place_id
							ORDER BY f.created_at DESC, f.id DESC) AS rn
		FROM feed f
		WHERE f.team_id = t.id
		AND f.deleted_at IS NULL
		AND f.place_id IS NOT NULL
	)
	SELECT
		p.external_place_id AS externalPlaceId,
		p.name,
		p.latitude,
		p.longitude,
		p.address,
		r.feed_count AS feedCount,
		fi.img_url AS thumbnailUrl
	FROM ranked r
	JOIN place p ON p.id = r.place_id
	LEFT JOIN feed_image fi ON fi.feed_id = r.feed_id
	AND fi.order_index = 0
	WHERE r.rn = 1
) q;

CREATE TEMP TABLE map_pins_cand_l AS
SELECT t.id AS team_id, q.*
FROM team t
CROSS JOIN LATERAL (
	WITH counts AS (
		SELECT
			f.place_id,
			COUNT(*) AS feed_count
		FROM feed f
		WHERE f.team_id = t.id
		AND f.deleted_at IS NULL
		AND f.place_id IS NOT NULL
		GROUP BY f.place_id
	)
	SELECT
		p.external_place_id AS externalPlaceId,
		p.name,
		p.latitude,
		p.longitude,
		p.address,
		c.feed_count AS feedCount,
		fi.img_url AS thumbnailUrl
	FROM counts c
	CROSS JOIN LATERAL (
		SELECT f.id AS feed_id
		FROM feed f
		WHERE f.team_id = t.id
		AND f.place_id = c.place_id
		AND f.deleted_at IS NULL
		ORDER BY f.created_at DESC, f.id DESC
		LIMIT 1
	) latest
	JOIN place p ON p.id = c.place_id
	LEFT JOIN feed_image fi ON fi.feed_id = latest.feed_id
	AND fi.order_index = 0
) q;

CREATE TEMP TABLE map_pins_cand_d AS
SELECT t.id AS team_id, q.*
FROM team t
CROSS JOIN LATERAL (
	WITH latest AS (
		SELECT DISTINCT ON (f.place_id)
			f.place_id,
			f.id AS feed_id
		FROM feed f
		WHERE f.team_id = t.id
		AND f.deleted_at IS NULL
		AND f.place_id IS NOT NULL
		ORDER BY f.place_id, f.created_at DESC, f.id DESC
	),
	counts AS (
		SELECT
			f.place_id,
			COUNT(*) AS feed_count
		FROM feed f
		WHERE f.team_id = t.id
		AND f.deleted_at IS NULL
		AND f.place_id IS NOT NULL
		GROUP BY f.place_id
	)
	SELECT
		p.external_place_id AS externalPlaceId,
		p.name,
		p.latitude,
		p.longitude,
		p.address,
		c.feed_count AS feedCount,
		fi.img_url AS thumbnailUrl
	FROM latest l
	JOIN counts c ON c.place_id = l.place_id
	JOIN place p ON p.id = l.place_id
	LEFT JOIN feed_image fi ON fi.feed_id = l.feed_id
	AND fi.order_index = 0
) q;

SELECT
	(SELECT count(*) FROM team) AS teams,
	(SELECT count(*) FROM map_pins_before) AS before_rows,
	(SELECT count(*) FROM map_pins_cand_l) AS cand_l_rows,
	(SELECT count(*) FROM map_pins_cand_d) AS cand_d_rows,
	(SELECT count(*) FROM (SELECT * FROM map_pins_before EXCEPT ALL SELECT * FROM map_pins_cand_l) x) AS before_minus_l,
	(SELECT count(*) FROM (SELECT * FROM map_pins_cand_l EXCEPT ALL SELECT * FROM map_pins_before) x) AS l_minus_before,
	(SELECT count(*) FROM (SELECT * FROM map_pins_before EXCEPT ALL SELECT * FROM map_pins_cand_d) x) AS before_minus_d,
	(SELECT count(*) FROM (SELECT * FROM map_pins_cand_d EXCEPT ALL SELECT * FROM map_pins_before) x) AS d_minus_before;

DROP TABLE map_pins_before, map_pins_cand_l, map_pins_cand_d;
\else
\echo '=== (iv) 전체 팀 비교는 건너뜀 (-v compare=1 로 실행)'
\endif
