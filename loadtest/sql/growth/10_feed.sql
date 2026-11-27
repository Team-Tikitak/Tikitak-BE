-- =============================================================================
-- growth/10_feed.sql : 성장 시나리오 피드 시드 (place, feed, feed_image, feed_tag, feed_reaction, feed_comment) — #232
--
-- 전제: growth/00_common.sql을 실행한 직후. place/feed/feed_image/feed_tag/feed_reaction/feed_comment가 모두 비어 있어야 한다.
--       하나라도 행이 있으면 아무것도 넣지 않고 에러로 멈춘다. 다시 넣으려면 README 7번 초기화(pgdata 볼륨만 삭제) 절차로 비운다.
-- 실행:  README "6-4. 성장 시드". 00_common.sql과 같은 salt·base_ts를 넘긴다.
--          docker compose -f loadtest/docker-compose.yml exec -T postgres \
--            psql -U tikitak -d tikitak -v base_ts=2026-11-27T18:00:00 -f - < loadtest/sql/growth/10_feed.sql
-- 변수:  salt         난수 salt 정수 (기본 232). 00_common.sql과 같은 값을 쓴다
--        base_ts      기준 시각(KST). 00_common.sql과 같은 값을 쓴다(기본 실행 시점 LOCALTIMESTAMP, 그러면 결과가 달라진다)
--        places       전역 place 풀 크기 (기본 50000)
--        rate_median  월간 피드 비율 로그정규분포의 중앙값 (기본 5)
--        rate_sigma   그 로그 표준편차 (기본 0.9076 = ln(16/5) / 1.2816, 즉 곱하기 전 p90 = 16)
--        rate_cap     월간 피드 비율 상한 (기본 23)
--        size_exp     팀원 수 계수 (팀원 수 / 7)^size_exp 의 지수 (기본 0.4)
--        daily_p      2025-01-01 이후 피드 중 데일리 후보 비율 (기본 0.205)
--        place_ratio  전체 피드 중 장소 있는 피드 목표 비율 (기본 0.5)
--        pool_ratio   팀 장소 풀 크기 = max(1, ceil(장소 피드 수 × pool_ratio)) (기본 0.4)
--
-- 보정 결과: rate_median=5, rate_sigma=0.9076 그대로 둔다(보정 없음). 2026-11-27 teams=1000, salt=232,
--   base_ts=2026-11-27T17:00:00 실행에서 팀 나이 6개월 이상 팀(845개)의 피드 수/나이(월)가 p50 4.65, p90 14.93, 최대 23.000으로
--   계약 범위(p50 4~6, p90 13~19, 최대 23 이하) 안이었다. 같은 salt, teams=15000, base_ts=2026-11-27T17:00:00 전체 규모에서는
--   12,476개 팀 p50 4.78, p90 15.71, 최대 23.000이다(README 6-4-6 실행 기록).
--
-- 난수: 10_feed.sql과 같은 pg_temp.lt_r(key, stream). 로그정규는 lt_r 두 개로 Box-Muller.
-- 시각: TIMESTAMP(타임존 없음) 컬럼에 base_ts(KST) 기준 값을 넣는다. 마이크로초까지 들어간다.
--
-- 만드는 것
--   place         : id 1~places. 주소·region은 10_feed.sql과 같은 36개 지역 형식(extractRegion 결과와 같은 region, 서울 쏠림).
--   feed          : 팀마다 rate = least(rate_cap, lognormal(rate_median, rate_sigma) × (팀원 수/7)^size_exp).
--                   피드 수 = max(1, round(rate × 나이(월))). 나이(월) = (base_ts - 팀 생성) / 30.4375일.
--                   단, round로 rate_cap을 넘지 않게 floor(rate_cap × 나이(월))에서 한 번 더 자른다(1건 하한이 우선).
--                   시각은 팀 생성 ~ base_ts 균등. ID는 created_at 순. soft delete 없음.
--                   작성자는 팀원 slot floor(팀원 수 × u^1.3)(10_feed.sql과 같은 쏠림).
--   데일리 답변   : 2025-01-01(활성 질문 effective_from 최댓값) 이후 피드의 daily_p를 후보로, (팀, 작성자, 날짜)마다 첫 후보 1건만.
--                   question_id = 활성 질문(question_order 순)[(답변일 - 팀 생성일) % 질문 수],
--                   key = team_id:team_member_id:question_id:yyyy-MM-dd. 이미지 1장, 작성자 태그만, place 없음.
--   place 지정    : 데일리가 아닌 피드에 팀마다 확률 least(1, place_ratio × 팀 피드 수 / 팀의 데일리 아닌 피드 수).
--                   팀 장소 풀 K = max(1, ceil(그 팀 장소 피드 수 × pool_ratio)), 풀의 j번째 = 전역 place 중 균등 하나.
--                   피드의 장소 = 풀[floor(K × u^2)] (앞쪽 장소로 쏠림).
--   feed_image    : 일반 피드 1~3장(50/30/20%). 10_feed.sql과 같은 url 형식.
--   feed_tag      : 작성자 + 같은 팀 다른 멤버 0~2명(50/30/20%, 팀원 수 - 1을 넘지 않음).
--   feed_reaction : 0~4개(균등, 팀원 수를 넘지 않음), (feed, 멤버) 유니크.
--   feed_comment  : 피드마다 floor(18 × u^4)개(0~17). 작성자는 같은 팀 멤버, feed_image_id는 그 피드의 이미지.
-- =============================================================================

\set ON_ERROR_STOP on

\if :{?salt}
\else
	\set salt 232
\endif
\if :{?places}
\else
	\set places 50000
\endif
\if :{?rate_median}
\else
	\set rate_median 5
\endif
\if :{?rate_sigma}
\else
	\set rate_sigma 0.9076
\endif
\if :{?rate_cap}
\else
	\set rate_cap 23
\endif
\if :{?size_exp}
\else
	\set size_exp 0.4
\endif
\if :{?daily_p}
\else
	\set daily_p 0.205
\endif
\if :{?place_ratio}
\else
	\set place_ratio 0.5
\endif
\if :{?pool_ratio}
\else
	\set pool_ratio 0.4
\endif

SET TIME ZONE 'Asia/Seoul';

\if :{?base_ts}
\else
	SELECT to_char(LOCALTIMESTAMP(0), 'YYYY-MM-DD"T"HH24:MI:SS') AS base_ts \gset
	\echo '경고: base_ts를 주지 않아 실행 시각을 쓴다. 00_common.sql과 같은 값을 넘겨야 결과가 재현된다.'
\endif

\echo 'growth/10_feed.sql: salt=' :salt ', base_ts=' :base_ts ', places=' :places ', rate_median=' :rate_median ', rate_sigma=' :rate_sigma ', rate_cap=' :rate_cap ', size_exp=' :size_exp ', daily_p=' :daily_p ', place_ratio=' :place_ratio ', pool_ratio=' :pool_ratio

BEGIN;

-- 대량 정렬(피드 ID를 시각 순으로 매기는 window 정렬 등)을 메모리에서 끝내기 위해 이 트랜잭션에서만 올린다.
SET LOCAL work_mem = '128MB';
-- FK 트리거를 이 트랜잭션에서만 끈다(10_feed.sql과 같은 이유). 커밋 뒤 확인 쿼리의 "FK 고아 검사"로 확인한다.
SET LOCAL session_replication_role = replica;

DO $$
BEGIN
	IF EXISTS (SELECT 1 FROM place)
		OR EXISTS (SELECT 1 FROM feed)
		OR EXISTS (SELECT 1 FROM feed_image)
		OR EXISTS (SELECT 1 FROM feed_tag)
		OR EXISTS (SELECT 1 FROM feed_reaction)
		OR EXISTS (SELECT 1 FROM feed_comment) THEN
		RAISE EXCEPTION 'growth/10_feed.sql은 피드 테이블이 빈 상태 전제다. place/feed/feed_image/feed_tag/feed_reaction/feed_comment에 이미 행이 있다. README 7번 초기화(pgdata 볼륨만 삭제) 절차로 비운 뒤 다시 실행한다.';
	END IF;
	IF NOT EXISTS (SELECT 1 FROM team WHERE description = 'loadtest:growth') THEN
		RAISE EXCEPTION 'growth/00_common.sql을 먼저 실행해야 한다(growth 팀이 없다).';
	END IF;
	IF EXISTS (SELECT 1 FROM team WHERE description IS DISTINCT FROM 'loadtest:growth') THEN
		RAISE EXCEPTION 'growth 팀이 아닌 팀이 있다. 스트레스 시드와 섞지 않는다.';
	END IF;
	IF NOT EXISTS (SELECT 1 FROM question WHERE active) THEN
		RAISE EXCEPTION '활성 question이 없다. 앱을 기동해 Flyway(V13, V16)를 먼저 적용한다.';
	END IF;
END $$;

SELECT (SELECT max(created_at) FROM team_member) <= (:'base_ts')::timestamp AS base_ok \gset
\if :base_ok
\else
	\echo 'base_ts가 00_common.sql의 가입 시각보다 이르다. 00_common.sql과 같은 base_ts를 넘긴다.'
	\quit 3
\endif

SELECT format($fmt$
CREATE FUNCTION pg_temp.lt_r(k bigint, stream int) RETURNS double precision
LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $f$
	SELECT ((hashint8extended(k, %s::bigint * 100 + stream) >> 11) & 9007199254740991)::double precision
		/ 9007199254740992.0
$f$
$fmt$, (:salt)::int) \gexec

-- ---------------------------------------------------------------------------
-- place (10_feed.sql과 같은 지역 표)
-- ---------------------------------------------------------------------------
CREATE TEMP TABLE lt_region (idx int PRIMARY KEY, addr_prefix text, region text, lat numeric, lng numeric) ON COMMIT DROP;
INSERT INTO lt_region VALUES
	(0, '서울 강남구', '서울 강남구', 37.4979, 127.0276),
	(1, '서울 마포구', '서울 마포구', 37.5563, 126.9220),
	(2, '서울 성동구', '서울 성동구', 37.5446, 127.0557),
	(3, '서울 종로구', '서울 종로구', 37.5704, 126.9921),
	(4, '서울 용산구', '서울 용산구', 37.5345, 126.9946),
	(5, '서울 송파구', '서울 송파구', 37.5145, 127.1059),
	(6, '서울 서대문구', '서울 서대문구', 37.5591, 126.9425),
	(7, '서울 광진구', '서울 광진구', 37.5403, 127.0695),
	(8, '서울 관악구', '서울 관악구', 37.4813, 126.9527),
	(9, '서울 동대문구', '서울 동대문구', 37.5806, 127.0471),
	(10, '서울 성북구', '서울 성북구', 37.5894, 127.0167),
	(11, '서울 영등포구', '서울 영등포구', 37.5216, 126.9245),
	(12, '경기 성남시 분당구', '경기 성남시 분당구', 37.3826, 127.1189),
	(13, '경기 수원시 팔달구', '경기 수원시 팔달구', 37.2795, 127.0164),
	(14, '경기 수원시 영통구', '경기 수원시 영통구', 37.2511, 127.0710),
	(15, '경기 고양시 일산동구', '경기 고양시 일산동구', 37.6585, 126.7700),
	(16, '경기 용인시 수지구', '경기 용인시 수지구', 37.3221, 127.0977),
	(17, '경기 안양시 동안구', '경기 안양시 동안구', 37.3925, 126.9512),
	(18, '경기 부천시', '경기 부천시', 37.5034, 126.7660),
	(19, '경기 파주시', '경기 파주시', 37.7600, 126.7802),
	(20, '인천 연수구', '인천 연수구', 37.4100, 126.6783),
	(21, '인천 남동구', '인천 남동구', 37.4470, 126.7313),
	(22, '부산 해운대구', '부산 해운대구', 35.1631, 129.1636),
	(23, '부산 부산진구', '부산 부산진구', 35.1577, 129.0590),
	(24, '부산 수영구', '부산 수영구', 35.1455, 129.1131),
	(25, '대구 중구', '대구', 35.8694, 128.5938),
	(26, '대전 유성구', '대전 유성구', 36.3623, 127.3562),
	(27, '광주 동구', '광주 동구', 35.1460, 126.9230),
	(28, '세종특별자치시', '세종특별자치시', 36.4801, 127.2890),
	(29, '강원특별자치도 춘천시', '강원특별자치도 춘천시', 37.8813, 127.7298),
	(30, '강원특별자치도 강릉시', '강원특별자치도 강릉시', 37.7519, 128.8761),
	(31, '제주특별자치도 제주시', '제주특별자치도 제주시', 33.4996, 126.5312),
	(32, '제주특별자치도 서귀포시', '제주특별자치도 서귀포시', 33.2541, 126.5600),
	(33, '전북특별자치도 전주시 완산구', '전북특별자치도 전주시 완산구', 35.8120, 127.1200),
	(34, '충남 천안시 동남구', '충남 천안시 동남구', 36.8065, 127.1522),
	(35, '경북 경주시', '경북 경주시', 35.8562, 129.2247);

INSERT INTO place (id, name, latitude, longitude, address, created_at, external_place_id, region)
SELECT
	p.id,
	(ARRAY['카페', '식당', '스터디카페', '공원', '보드게임카페', '도서관', '술집', '노래방'])[1 + floor(8 * pg_temp.lt_r(p.id, 2))::int]
		|| ' 성장점 ' || p.id,
	round((r.lat + (pg_temp.lt_r(p.id, 3) - 0.5) * 0.04)::numeric, 7),
	round((r.lng + (pg_temp.lt_r(p.id, 4) - 0.5) * 0.04)::numeric, 7),
	r.addr_prefix || ' ' || (ARRAY['중앙로', '대학로', '역전로', '공원로', '시장길'])[1 + floor(5 * pg_temp.lt_r(p.id, 5))::int]
		|| ' ' || (1 + floor(300 * pg_temp.lt_r(p.id, 6))::int),
	date_trunc('day', (:'base_ts')::timestamp) - interval '1100 days' + make_interval(secs => p.id),
	(10000000 + p.id)::text,
	r.region
FROM generate_series(1, :places) AS p(id)
JOIN lt_region r ON r.idx = floor(36 * power(pg_temp.lt_r(p.id, 1), 1.6))::int
ORDER BY p.id;

-- ---------------------------------------------------------------------------
-- 팀 · 팀원 · 질문
-- ---------------------------------------------------------------------------
-- 팀원 slot(0부터, team_member.id 순. slot 0이 팀장)
CREATE TEMP TABLE lt_tm ON COMMIT DROP AS
SELECT
	tm.team_id,
	(row_number() OVER (PARTITION BY tm.team_id ORDER BY tm.id) - 1)::int AS idx,
	tm.id AS tm_id
FROM team_member tm
WHERE tm.status = 'ACTIVE';
CREATE INDEX ON lt_tm (team_id, idx);
ANALYZE lt_tm;

-- 팀별 월간 피드 비율과 피드 수
CREATE TEMP TABLE lt_team ON COMMIT DROP AS
WITH t AS (
	SELECT
		t.id AS team_id,
		t.created_at,
		t.created_at::date AS created_date,
		(SELECT count(*) FROM lt_tm m WHERE m.team_id = t.id)::int AS size,
		extract(epoch FROM ((:'base_ts')::timestamp - t.created_at)) / 86400.0 / 30.4375 AS age_months,
		-- Box-Muller 표준정규
		sqrt(-2 * ln(1 - pg_temp.lt_r(t.id, 10))) * cos(2 * pi() * pg_temp.lt_r(t.id, 11)) AS z
	FROM team t
),
r AS (
	SELECT t.*,
		least((:rate_cap)::double precision,
			exp(ln((:rate_median)::double precision) + (:rate_sigma) * t.z) * power(t.size / 7.0, (:size_exp))) AS rate
	FROM t
)
SELECT r.*,
	greatest(1, least(round(r.rate * r.age_months), floor((:rate_cap) * r.age_months)))::int AS n
FROM r;

DO $$
BEGIN
	IF (SELECT max(n) FROM lt_team) >= 1000 THEN
		RAISE EXCEPTION '팀당 피드가 1,000건 이상이다. key(team_id * 1000 + k) 전제가 깨진다.';
	END IF;
END $$;

-- 활성 질문(question_order 순, 0부터)
CREATE TEMP TABLE lt_question ON COMMIT DROP AS
SELECT (row_number() OVER (ORDER BY question_order) - 1)::int AS idx, id AS question_id
FROM question
WHERE active;
SELECT count(*) AS q_count, (SELECT max(effective_from) FROM question WHERE active) AS q_from FROM lt_question \gset

-- ---------------------------------------------------------------------------
-- feed
-- ---------------------------------------------------------------------------
-- 1단계: 팀 × k. key = team_id * 1000 + k (팀당 피드 1,000건 미만, 위에서 확인)
CREATE TEMP TABLE lt_feed_raw ON COMMIT DROP AS
SELECT
	s.*,
	(s.created_at::date >= :'q_from'::date AND pg_temp.lt_r(s.key, 22) < (:daily_p)) AS daily_candidate
FROM (
	SELECT
		t.team_id,
		t.size,
		t.created_date,
		t.team_id::bigint * 1000 + k AS key,
		floor(t.size * power(pg_temp.lt_r(t.team_id::bigint * 1000 + k, 21), 1.3))::int AS author_idx,
		t.created_at + ((:'base_ts')::timestamp - t.created_at) * pg_temp.lt_r(t.team_id::bigint * 1000 + k, 20) AS created_at
	FROM lt_team t
	CROSS JOIN LATERAL generate_series(1, t.n) AS k
) s;

-- 2단계: 데일리 답변 확정((팀, 작성자, 날짜)마다 첫 후보 1건)
CREATE TEMP TABLE lt_feed_daily ON COMMIT DROP AS
SELECT
	r.*,
	(r.daily_candidate AND row_number() OVER (
		PARTITION BY r.team_id, r.author_idx, r.created_at::date, r.daily_candidate
		ORDER BY r.created_at, r.key) = 1) AS is_daily
FROM lt_feed_raw r;

-- 3단계: 장소 여부(팀별 확률)
CREATE TEMP TABLE lt_feed_place ON COMMIT DROP AS
WITH d AS (
	SELECT
		x.*,
		least(1.0, (:place_ratio) * count(*) OVER w / nullif(count(*) FILTER (WHERE NOT x.is_daily) OVER w, 0)) AS place_p
	FROM lt_feed_daily x
	WINDOW w AS (PARTITION BY x.team_id)
)
SELECT d.*, (NOT d.is_daily AND pg_temp.lt_r(d.key, 23) < d.place_p) AS has_place
FROM d;

-- 4단계: 팀 장소 풀 크기 K, 피드 ID(created_at 순)와 나머지 속성
CREATE TEMP TABLE lt_feed ON COMMIT DROP AS
WITH k AS (
	SELECT x.*,
		greatest(1, ceil((:pool_ratio) * count(*) FILTER (WHERE x.has_place) OVER (PARTITION BY x.team_id)))::int AS pool_k
	FROM lt_feed_place x
),
a AS (
	SELECT
		row_number() OVER (ORDER BY k.created_at, k.key) AS id,
		k.key,
		k.team_id,
		k.size,
		k.author_idx,
		k.created_at,
		k.is_daily,
		CASE WHEN k.is_daily THEN k.created_at::date END AS answer_date,
		CASE WHEN k.has_place
			THEN 1 + floor((:places) * pg_temp.lt_r(
				k.team_id::bigint * 1000 + floor(k.pool_k * power(pg_temp.lt_r(k.key, 24), 2))::int, 25))::bigint
		END AS place_id,
		CASE WHEN k.is_daily THEN 1
			WHEN pg_temp.lt_r(k.key, 26) < 0.5 THEN 1
			WHEN pg_temp.lt_r(k.key, 26) < 0.8 THEN 2
			ELSE 3
		END AS img_count,
		CASE WHEN k.is_daily THEN 0
			ELSE least(k.size - 1, CASE
				WHEN pg_temp.lt_r(k.key, 27) < 0.5 THEN 0
				WHEN pg_temp.lt_r(k.key, 27) < 0.8 THEN 1
				ELSE 2
			END)
		END AS tag_extra,
		least(k.size, floor(5 * pg_temp.lt_r(k.key, 28))::int) AS react_count,
		floor(18 * power(pg_temp.lt_r(k.key, 31), 4))::int AS comment_count,
		k.created_date
	FROM k
)
SELECT
	a.*,
	m.tm_id AS author_tm_id,
	q.question_id,
	-- 이미지 ID를 피드 순서대로 연속 배정하기 위한 누적 합(이 피드 앞까지의 이미지 수)
	(sum(a.img_count) OVER (ORDER BY a.id ROWS UNBOUNDED PRECEDING) - a.img_count)::bigint AS img_base
FROM a
JOIN lt_tm m ON m.team_id = a.team_id AND m.idx = a.author_idx
LEFT JOIN lt_question q ON a.is_daily AND q.idx = ((a.answer_date - a.created_date) % (:q_count))::int;

DROP TABLE lt_feed_raw;
DROP TABLE lt_feed_daily;
DROP TABLE lt_feed_place;
CREATE INDEX ON lt_feed (id);
ANALYZE lt_feed;

INSERT INTO feed (
	id, team_member_id, team_id, place_id, question_id, content, meeting_date,
	created_at, updated_at, deleted_at, question_answer_date, daily_answer_active_key
)
SELECT
	f.id,
	f.author_tm_id,
	f.team_id,
	f.place_id,
	f.question_id,
	CASE WHEN f.is_daily
		THEN '오늘의 질문 답변 ' || f.id
		ELSE '성장 시드 피드 ' || f.id || ' 입니다.' || repeat(' 오늘 팀 활동 기록', floor(4 * pg_temp.lt_r(f.key, 33))::int)
	END,
	NULL,
	f.created_at,
	f.created_at,
	NULL,
	f.answer_date,
	CASE WHEN f.is_daily
		THEN f.team_id || ':' || f.author_tm_id || ':' || f.question_id || ':' || to_char(f.answer_date, 'YYYY-MM-DD')
	END
FROM lt_feed f
ORDER BY f.id;

-- ---------------------------------------------------------------------------
-- feed_image: id = img_base + order_index + 1 (피드 순서대로 연속)
-- ---------------------------------------------------------------------------
INSERT INTO feed_image (id, feed_id, img_url, order_index, width, height, created_at, media_id)
SELECT
	f.img_base + g + 1,
	f.id,
	'https://loadtest-media.invalid/media/'
		|| CASE WHEN f.is_daily THEN 'daily-question-image' ELSE 'feed-image' END
		|| '/' || md5('lt-img-' || (f.img_base + g + 1))::uuid || '.jpg',
	g,
	NULL,
	NULL,
	f.created_at,
	NULL
FROM lt_feed f
CROSS JOIN LATERAL generate_series(0, f.img_count - 1) AS g
ORDER BY f.id, g;

-- ---------------------------------------------------------------------------
-- feed_tag: 작성자(ord 0) + 다른 멤버 0~2명(10_feed.sql과 같은 오프셋 방식. tag_extra <= size - 1)
-- ---------------------------------------------------------------------------
INSERT INTO feed_tag (id, feed_id, team_member_id)
SELECT
	row_number() OVER (ORDER BY x.feed_id, x.ord),
	x.feed_id,
	m.tm_id
FROM (
	SELECT f.id AS feed_id, f.team_id, 0 AS ord, f.author_idx AS member_idx
	FROM lt_feed f
	UNION ALL
	SELECT f.id, f.team_id, 1,
		(f.author_idx + 1 + floor((f.size - 1) * pg_temp.lt_r(f.key, 40))::int) % f.size
	FROM lt_feed f
	WHERE f.tag_extra >= 1
	UNION ALL
	-- 두 번째 오프셋: 첫 오프셋 o1(1..size-1) 다음부터 size-2칸 안에서 고르므로 o1과 다르고 0도 아니다(tag_extra 2는 size >= 3)
	SELECT f.id, f.team_id, 2,
		(f.author_idx + 1
			+ (floor((f.size - 1) * pg_temp.lt_r(f.key, 40))::int + 1 + floor((f.size - 2) * pg_temp.lt_r(f.key, 41))::int)
				% (f.size - 1)) % f.size
	FROM lt_feed f
	WHERE f.tag_extra >= 2
) x
JOIN lt_tm m ON m.team_id = x.team_id AND m.idx = x.member_idx
ORDER BY x.feed_id, x.ord;

-- ---------------------------------------------------------------------------
-- feed_reaction: slot s, s+1, ... (react_count명, 서로 다름)
-- ---------------------------------------------------------------------------
INSERT INTO feed_reaction (id, feed_id, team_member_id, emoji, created_at, reaction_type)
SELECT
	row_number() OVER (ORDER BY x.feed_id, x.j),
	x.feed_id,
	m.tm_id,
	NULL,
	x.created_at,
	x.reaction_type
FROM (
	SELECT
		f.id AS feed_id,
		f.team_id,
		j,
		(floor(f.size * pg_temp.lt_r(f.key, 50))::int + j) % f.size AS member_idx,
		f.created_at + least(interval '2 days', (:'base_ts')::timestamp - f.created_at) * pg_temp.lt_r(f.key * 8 + j, 51) AS created_at,
		(ARRAY['TAK_LEADER', 'TAK_SPARK', 'TAK_BURNER', 'TAK_BUILDER', 'TAK_FREE', 'TAK_CARE'])
			[1 + floor(6 * pg_temp.lt_r(f.key * 8 + j, 52))::int] AS reaction_type
	FROM lt_feed f
	CROSS JOIN LATERAL generate_series(0, f.react_count - 1) AS j
	WHERE f.react_count > 0
) x
JOIN lt_tm m ON m.team_id = x.team_id AND m.idx = x.member_idx
ORDER BY x.feed_id, x.j;

-- ---------------------------------------------------------------------------
-- feed_comment: ID는 created_at 순. key = feed key * 32 + j (j <= 17)
-- ---------------------------------------------------------------------------
INSERT INTO feed_comment (
	id, feed_id, team_member_id, content, position_x, position_y, is_deleted, created_at, updated_at, feed_image_id
)
SELECT
	row_number() OVER (ORDER BY x.created_at, x.feed_id, x.j),
	x.feed_id,
	m.tm_id,
	'댓글 ' || x.j || ' 좋아요' || repeat('!', floor(5 * pg_temp.lt_r(x.ck, 63))::int),
	round(pg_temp.lt_r(x.ck, 64)::numeric, 6),
	round(pg_temp.lt_r(x.ck, 65)::numeric, 6),
	FALSE,
	x.created_at,
	x.created_at,
	x.feed_image_id
FROM (
	SELECT
		f.id AS feed_id,
		f.team_id,
		j,
		f.key * 32 + j AS ck,
		floor(f.size * pg_temp.lt_r(f.key * 32 + j, 60))::int AS member_idx,
		f.img_base + 1 + floor(f.img_count * pg_temp.lt_r(f.key * 32 + j, 61))::int AS feed_image_id,
		f.created_at + least(interval '3 days', (:'base_ts')::timestamp - f.created_at) * pg_temp.lt_r(f.key * 32 + j, 62) AS created_at
	FROM lt_feed f
	CROSS JOIN LATERAL generate_series(1, f.comment_count) AS j
	WHERE f.comment_count > 0
) x
JOIN lt_tm m ON m.team_id = x.team_id AND m.idx = x.member_idx
ORDER BY 1;

-- ID를 직접 넣었으므로 identity 시퀀스를 맞춘다(앱이 이후 INSERT할 때 충돌하지 않게).
SELECT setval(pg_get_serial_sequence('place', 'id'), (SELECT max(id) FROM place));
SELECT setval(pg_get_serial_sequence('feed', 'id'), (SELECT max(id) FROM feed));
SELECT setval(pg_get_serial_sequence('feed_image', 'id'), (SELECT max(id) FROM feed_image));
SELECT setval(pg_get_serial_sequence('feed_tag', 'id'), (SELECT max(id) FROM feed_tag));
SELECT setval(pg_get_serial_sequence('feed_reaction', 'id'), (SELECT max(id) FROM feed_reaction));
SELECT setval(pg_get_serial_sequence('feed_comment', 'id'), (SELECT max(id) FROM feed_comment));

COMMIT;

-- 통계 갱신과 visibility map 설정(10_feed.sql과 같은 이유).
VACUUM (ANALYZE) place;
VACUUM (ANALYZE) feed;
VACUUM (ANALYZE) feed_image;
VACUUM (ANALYZE) feed_tag;
VACUUM (ANALYZE) feed_reaction;
VACUUM (ANALYZE) feed_comment;

-- ---------------------------------------------------------------------------
-- 확인 쿼리 (계약 완료 조건 전체는 README 6-4의 확인 쿼리)
-- ---------------------------------------------------------------------------
\echo '== 건수'
SELECT
	(SELECT count(*) FROM place) AS places,
	(SELECT count(DISTINCT place_id) FROM feed) AS used_places,
	(SELECT count(*) FROM feed) AS feeds,
	(SELECT count(*) FROM feed_image) AS feed_images,
	(SELECT count(*) FROM feed_tag) AS feed_tags,
	(SELECT count(*) FROM feed_reaction) AS feed_reactions,
	(SELECT count(*) FROM feed_comment) AS feed_comments;

\echo '== 팀당 피드 수 (최소/p50/p90/최대), 나이 6개월 이상 팀의 피드 수/나이(월) (p50/p90/최대)'
WITH c AS (
	SELECT t.id, count(f.id) AS n,
		extract(epoch FROM ((:'base_ts')::timestamp - t.created_at)) / 86400.0 / 30.4375 AS age_months
	FROM team t LEFT JOIN feed f ON f.team_id = t.id
	GROUP BY t.id
)
SELECT
	min(n) AS min_feeds, percentile_cont(0.5) WITHIN GROUP (ORDER BY n) AS p50_feeds,
	percentile_cont(0.9) WITHIN GROUP (ORDER BY n) AS p90_feeds, max(n) AS max_feeds,
	round((percentile_cont(0.5) WITHIN GROUP (ORDER BY n / age_months) FILTER (WHERE age_months >= 6))::numeric, 2) AS p50_rate_6m,
	round((percentile_cont(0.9) WITHIN GROUP (ORDER BY n / age_months) FILTER (WHERE age_months >= 6))::numeric, 2) AS p90_rate_6m,
	round((max(n / age_months) FILTER (WHERE age_months >= 6))::numeric, 3) AS max_rate_6m
FROM c;

\echo '== 비율 (장소 있는 피드, 2025-01-01 이후 피드 중 데일리)'
SELECT
	round(avg((place_id IS NOT NULL)::int), 4) AS place_ratio,
	round((count(*) FILTER (WHERE question_id IS NOT NULL))::numeric
		/ nullif(count(*) FILTER (WHERE created_at >= :'q_from'::date), 0), 4) AS daily_ratio_since_q_from,
	min(created_at) AS min_created_at, max(created_at) AS max_created_at
FROM feed;

\echo '== FK 고아 검사 (모두 0이어야 한다. 시드 중 FK 트리거를 껐으므로 여기서 확인한다)'
SELECT
	(SELECT count(*) FROM feed f WHERE NOT EXISTS (SELECT 1 FROM team t WHERE t.id = f.team_id)) AS feed_team,
	(SELECT count(*) FROM feed f WHERE NOT EXISTS (SELECT 1 FROM team_member tm WHERE tm.id = f.team_member_id)) AS feed_team_member,
	(SELECT count(*) FROM feed f WHERE f.place_id IS NOT NULL
		AND NOT EXISTS (SELECT 1 FROM place p WHERE p.id = f.place_id)) AS feed_place,
	(SELECT count(*) FROM feed f WHERE f.question_id IS NOT NULL
		AND NOT EXISTS (SELECT 1 FROM question q WHERE q.id = f.question_id)) AS feed_question,
	(SELECT count(*) FROM feed_image i WHERE NOT EXISTS (SELECT 1 FROM feed f WHERE f.id = i.feed_id)) AS image_feed,
	(SELECT count(*) FROM feed_tag t WHERE NOT EXISTS (SELECT 1 FROM feed f WHERE f.id = t.feed_id)
		OR NOT EXISTS (SELECT 1 FROM team_member tm WHERE tm.id = t.team_member_id)) AS tag_fk,
	(SELECT count(*) FROM feed_reaction r WHERE NOT EXISTS (SELECT 1 FROM feed f WHERE f.id = r.feed_id)
		OR NOT EXISTS (SELECT 1 FROM team_member tm WHERE tm.id = r.team_member_id)) AS reaction_fk,
	(SELECT count(*) FROM feed_comment c WHERE NOT EXISTS (SELECT 1 FROM feed f WHERE f.id = c.feed_id)
		OR NOT EXISTS (SELECT 1 FROM team_member tm WHERE tm.id = c.team_member_id)
		OR NOT EXISTS (SELECT 1 FROM feed_image i WHERE i.id = c.feed_image_id)) AS comment_fk;

\echo '== 무결성 검사 (모두 0이어야 한다)'
SELECT
	(SELECT count(*) FROM feed f WHERE NOT EXISTS (
		SELECT 1 FROM feed_tag t WHERE t.feed_id = f.id AND t.team_member_id = f.team_member_id)) AS feeds_without_author_tag,
	(SELECT count(*) FROM (SELECT feed_id, order_index FROM feed_image GROUP BY 1, 2 HAVING count(*) > 1) x) AS dup_image_order,
	(SELECT count(*) FROM feed f WHERE NOT EXISTS (
		SELECT 1 FROM feed_image i WHERE i.feed_id = f.id AND i.order_index = 0)) AS feeds_without_image0,
	(SELECT count(*) FROM feed_tag t JOIN feed f ON f.id = t.feed_id JOIN team_member tm ON tm.id = t.team_member_id
		WHERE tm.team_id <> f.team_id) AS cross_team_tags,
	(SELECT count(*) FROM feed_reaction r JOIN feed f ON f.id = r.feed_id JOIN team_member tm ON tm.id = r.team_member_id
		WHERE tm.team_id <> f.team_id) AS cross_team_reactions,
	(SELECT count(*) FROM feed_comment c JOIN feed f ON f.id = c.feed_id JOIN team_member tm ON tm.id = c.team_member_id
		WHERE tm.team_id <> f.team_id) AS cross_team_comments,
	(SELECT count(*) FROM feed_comment c JOIN feed_image i ON i.id = c.feed_image_id WHERE i.feed_id <> c.feed_id) AS comment_image_mismatch,
	(SELECT count(*) FROM feed_comment c JOIN feed f ON f.id = c.feed_id
		WHERE c.created_at < f.created_at OR c.created_at > (:'base_ts')::timestamp) AS comment_time_out_of_range,
	(SELECT count(*) FROM feed f JOIN team t ON t.id = f.team_id
		WHERE f.created_at < t.created_at OR f.created_at > (:'base_ts')::timestamp) AS feed_time_out_of_range,
	(SELECT count(*) FROM feed WHERE deleted_at IS NOT NULL) AS soft_deleted_feeds;

\echo '== 데일리 답변 검사 (bad_*는 0이어야 한다)'
SELECT
	count(*) AS daily_answers,
	count(*) FILTER (WHERE daily_answer_active_key IS DISTINCT FROM
		team_id || ':' || team_member_id || ':' || question_id || ':' || to_char(question_answer_date, 'YYYY-MM-DD')) AS bad_key,
	count(*) FILTER (WHERE question_answer_date <> created_at::date OR question_answer_date < :'q_from'::date) AS bad_answer_date,
	count(*) FILTER (WHERE place_id IS NOT NULL) AS bad_with_place,
	count(*) FILTER (WHERE (SELECT count(*) FROM feed_image i WHERE i.feed_id = feed.id) <> 1) AS bad_image_count
FROM feed
WHERE question_id IS NOT NULL;

\echo '== 댓글 수 분포 (댓글 수, 피드 수)'
SELECT coalesce(c.cnt, 0) AS comments, count(*) AS feeds
FROM feed f
LEFT JOIN (SELECT feed_id, count(*) AS cnt FROM feed_comment GROUP BY feed_id) c ON c.feed_id = f.id
GROUP BY 1 ORDER BY 1;
