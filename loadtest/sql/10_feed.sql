-- =============================================================================
-- 10_feed.sql : 피드 시드 (place, feed, feed_image, feed_tag, feed_reaction, feed_comment) — #217
--
-- 전제: 00_common.sql을 실행한 직후. place/feed/feed_image/feed_tag/feed_reaction/feed_comment가 모두 비어 있어야 한다.
--       하나라도 행이 있으면 아무것도 넣지 않고 에러로 멈춘다. 다시 넣으려면 README 7번 초기화(pgdata 볼륨만 삭제) 절차로 비운다.
-- 실행:  docker compose -f loadtest/docker-compose.yml exec -T postgres \
--          psql -U tikitak -d tikitak -f - < loadtest/sql/10_feed.sql
-- 변수:  salt          난수 salt 정수 (기본 217). 같은 값이면 건수·팀·작성자는 같다. 시각은 실행 시점 LOCALTIMESTAMP 기준이라 날짜 경계에 걸리는 데일리 수 등은 조금 달라질 수 있다.
--        places        place 수 (기본 5000)
--        normal_avg    일반 팀 팀당 평균 피드 수 (기본 350)
--        big_feeds     큰 팀 팀당 피드 수 (기본 100000)
--
-- 난수: random()은 실행 계획(조인·정렬 순서)에 따라 호출 순서가 바뀔 수 있어서 쓰지 않는다.
--       대신 pg_temp.lt_r(key, stream) = hashint8extended(key, salt*100+stream)를 [0,1)로 바꾼 값을 쓴다.
--       같은 key·stream이면 항상 같은 값이라 계획과 관계없이 결과가 같다.
-- 시각: TIMESTAMP(타임존 없음) 컬럼에 LOCALTIMESTAMP(세션 타임존 Asia/Seoul) 기준 값을 넣는다. 마이크로초까지 들어간다.
--
-- 만드는 것
--   place         : id 1~N. external_place_id = (10000000 + id) 문자열(유니크).
--                   address는 카카오 지번 주소 형식("서울 강남구 대학로 12")이고, region은 FeedService.extractRegion(address)
--                   결과와 같은 값이다(예: "서울 강남구", "경기 성남시 분당구", "세종특별자치시", "대구"). 서울 쪽에 쏠린다.
--   feed          : 큰 팀(1~3) 팀당 big_feeds건, 일반 팀은 팀당 평균 normal_avg건(지수분포로 쏠림, 최소 10건).
--                   기간은 최근 730일. 팀별 피드의 5%(최소 1건)는 이번 달(KST 1일 00:00 ~ 지금), 나머지는 그 이전.
--                   ID는 created_at 순서(실제처럼 ID와 시각이 같이 증가). soft delete 없음.
--   데일리 답변   : 피드 21%를 후보로 뽑고, (팀, 작성자, 날짜)마다 첫 후보 1건만 답변으로 만든다(daily_answer_active_key 유니크).
--                   답변 날짜는 활성 질문의 effective_from 최댓값 이후만(그 전에는 질문이 없음).
--                   question_id = 활성 질문(question_order 순)[(답변일 - 팀 생성일) % 질문 수] (DailyQuestionService.resolveTodayQuestion과 같음).
--                   key = team_id:team_member_id:question_id:yyyy-MM-dd (DailyQuestionService.dailyAnswerActiveKey와 같음).
--                   실제 생성 경로처럼 이미지 1장, 작성자 태그만, place 없음.
--                   큰 팀은 작성자 6명 × 날짜 수가 상한이라 20%를 못 채운다(약 4%).
--   place 지정    : 전체 피드의 약 60%가 되게 팀마다 일반 피드 중 일부에 place를 붙인다.
--                   팀마다 후보 장소 K개(큰 팀 300, 일반 팀 5~30)를 두고 순위 = floor(K * u^3)로 골라 소수 장소에 몰린다.
--   feed_image    : 일반 피드 1~3장(50/30/20%). order_index 0부터. media_id·width·height NULL(실제 생성 경로와 같음).
--                   img_url = R2_PUBLIC_BASE_URL(https://loadtest-media.invalid) + "/media/<purpose>/<uuid>.jpg" (ImageUrlResolver가 preset을 붙이는 형식)
--   feed_tag      : 작성자 반드시 + 일반 피드는 같은 팀 다른 멤버 0~2명.
--   feed_reaction : 0~4개, (feed, 멤버) 유니크, reaction_type 6종. emoji NULL(실제 upsert 경로와 같음).
--   feed_comment  : 피드 80%에 0~3개. 피드 0.1%는 500~2,000개. 작성자는 같은 팀 멤버, feed_image_id는 그 피드의 이미지,
--                   position 0~1(소수 6자리), is_deleted false, created_at은 피드 이후(지금을 넘지 않음). 대댓글 컬럼은 없다.
-- =============================================================================

\set ON_ERROR_STOP on

\if :{?salt}
\else
	\set salt 217
\endif
\if :{?places}
\else
	\set places 5000
\endif
\if :{?normal_avg}
\else
	\set normal_avg 350
\endif
\if :{?big_feeds}
\else
	\set big_feeds 100000
\endif

\echo '10_feed.sql: salt=' :salt ', places=' :places ', normal_avg=' :normal_avg ', big_feeds=' :big_feeds

SET TIME ZONE 'Asia/Seoul';

BEGIN;

-- 대량 정렬(피드 ID를 시각 순으로 매기는 window 정렬 등)을 메모리에서 끝내기 위해 이 트랜잭션에서만 올린다.
SET LOCAL work_mem = '128MB';
-- FK 트리거를 이 트랜잭션에서만 끈다. 켜 두면 행마다 부모 행 잠금 검사가 돌아 INSERT가 크게 느려진다(소규모 실측: 반응 7.2초→0.8초, 댓글 15초→1.9초).
-- 대신 커밋 뒤 확인 쿼리의 "FK 고아 검사"로 FK 컬럼이 실제 행을 가리키는지 확인한다(feed_image.media_id는 전부 NULL이라 검사하지 않는다).
-- (superuser 필요. compose의 POSTGRES_USER는 superuser다.)
SET LOCAL session_replication_role = replica;

DO $$
BEGIN
	IF EXISTS (SELECT 1 FROM place)
		OR EXISTS (SELECT 1 FROM feed)
		OR EXISTS (SELECT 1 FROM feed_image)
		OR EXISTS (SELECT 1 FROM feed_tag)
		OR EXISTS (SELECT 1 FROM feed_reaction)
		OR EXISTS (SELECT 1 FROM feed_comment) THEN
		RAISE EXCEPTION '10_feed.sql은 피드 테이블이 빈 상태 전제다. place/feed/feed_image/feed_tag/feed_reaction/feed_comment에 이미 행이 있다. README 7번 초기화(pgdata 볼륨만 삭제) 절차로 비운 뒤 다시 실행한다.';
	END IF;
	IF NOT EXISTS (SELECT 1 FROM team WHERE description = 'loadtest:big') THEN
		RAISE EXCEPTION '00_common.sql을 먼저 실행해야 한다(큰 팀이 없다).';
	END IF;
	IF NOT EXISTS (SELECT 1 FROM question WHERE active) THEN
		RAISE EXCEPTION '활성 question이 없다. 앱을 기동해 Flyway(V13, V16)를 먼저 적용한다.';
	END IF;
END $$;

-- 결정적 난수 [0,1). key와 stream이 같으면 항상 같은 값.
-- psql 변수는 $$ 안에서 치환되지 않으므로 format으로 salt를 넣어 \gexec로 만든다.
SELECT format($fmt$
CREATE FUNCTION pg_temp.lt_r(k bigint, stream int) RETURNS double precision
LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $f$
	SELECT ((hashint8extended(k, %s::bigint * 100 + stream) >> 11) & 9007199254740991)::double precision
		/ 9007199254740992.0
$f$
$fmt$, (:salt)::int) \gexec

-- ---------------------------------------------------------------------------
-- place
-- ---------------------------------------------------------------------------
-- region은 FeedService.extractRegion("<region> <도로명> <번지>")의 결과와 같아야 한다.
-- "대구 중구 ..."는 "대구"가 '구'로 끝나서 extractRegion 결과가 "대구"다(앱 동작 그대로 둔다).
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
		|| ' 부하점 ' || p.id,
	round((r.lat + (pg_temp.lt_r(p.id, 3) - 0.5) * 0.04)::numeric, 7),
	round((r.lng + (pg_temp.lt_r(p.id, 4) - 0.5) * 0.04)::numeric, 7),
	r.addr_prefix || ' ' || (ARRAY['중앙로', '대학로', '역전로', '공원로', '시장길'])[1 + floor(5 * pg_temp.lt_r(p.id, 5))::int]
		|| ' ' || (1 + floor(300 * pg_temp.lt_r(p.id, 6))::int),
	date_trunc('day', LOCALTIMESTAMP) - interval '740 days' + make_interval(secs => p.id),
	(10000000 + p.id)::text,
	r.region
FROM generate_series(1, :places) AS p(id)
JOIN lt_region r ON r.idx = floor(36 * power(pg_temp.lt_r(p.id, 1), 1.6))::int
ORDER BY p.id;

-- ---------------------------------------------------------------------------
-- 팀 · 팀원 · 질문
-- ---------------------------------------------------------------------------
-- 팀원 slot(0부터, team_member.id 순)
CREATE TEMP TABLE lt_tm ON COMMIT DROP AS
SELECT
	tm.team_id,
	(row_number() OVER (PARTITION BY tm.team_id ORDER BY tm.id) - 1)::int AS idx,
	tm.id AS tm_id
FROM team_member tm
WHERE tm.status = 'ACTIVE';
CREATE INDEX ON lt_tm (team_id, idx);
ANALYZE lt_tm;

-- 팀별 피드 수. 일반 팀은 지수분포 가중치를 평균 normal_avg에 맞춰 정규화(최소 10건).
CREATE TEMP TABLE lt_team ON COMMIT DROP AS
WITH t AS (
	SELECT
		t.id AS team_id,
		t.description = 'loadtest:big' AS is_big,
		t.created_at::date AS created_date,
		(SELECT count(*) FROM lt_tm m WHERE m.team_id = t.id)::int AS size,
		-ln(1 - pg_temp.lt_r(t.id, 10)) AS w
	FROM team t
),
n AS (
	SELECT t.*,
		CASE WHEN t.is_big THEN (:big_feeds)::int
			ELSE greatest(10, round((:normal_avg)::numeric * count(*) FILTER (WHERE NOT t.is_big) OVER ()
				* t.w / sum(t.w) FILTER (WHERE NOT t.is_big) OVER ()))::int
		END AS n
	FROM t
)
SELECT n.*,
	greatest(1, round(n.n * 0.05))::int AS n_month,
	CASE WHEN n.is_big THEN 300 ELSE 5 + floor(26 * pg_temp.lt_r(n.team_id, 11))::int END AS place_k
FROM n;

-- 활성 질문(question_order 순, 0부터). 앱은 오늘 날짜 기준 effective_from <= today인 활성 질문을 순서대로 쓴다.
CREATE TEMP TABLE lt_question ON COMMIT DROP AS
SELECT (row_number() OVER (ORDER BY question_order) - 1)::int AS idx, id AS question_id
FROM question
WHERE active;
SELECT count(*) AS q_count, (SELECT max(effective_from) FROM question WHERE active) AS q_from FROM lt_question \gset

-- ---------------------------------------------------------------------------
-- feed (임시 테이블에서 속성을 다 정한 뒤 넣는다)
-- ---------------------------------------------------------------------------
-- 1단계: 팀 × k. key = team_id * 1,000,000 + k (팀당 피드 100만 건 미만 전제)
CREATE TEMP TABLE lt_feed_raw ON COMMIT DROP AS
SELECT
	s.*,
	(s.created_at::date >= :'q_from'::date AND pg_temp.lt_r(s.key, 22) < 0.21) AS daily_candidate
FROM (
	SELECT
		t.team_id,
		t.size,
		t.created_date,
		t.place_k,
		t.team_id::bigint * 1000000 + k AS key,
		floor(t.size * power(pg_temp.lt_r(t.team_id::bigint * 1000000 + k, 21), 1.3))::int AS author_idx,
		CASE WHEN k <= t.n_month
			THEN date_trunc('month', LOCALTIMESTAMP)
				+ (LOCALTIMESTAMP - date_trunc('month', LOCALTIMESTAMP)) * pg_temp.lt_r(t.team_id::bigint * 1000000 + k, 20)
			ELSE (LOCALTIMESTAMP - interval '730 days')
				+ (date_trunc('month', LOCALTIMESTAMP) - (LOCALTIMESTAMP - interval '730 days'))
					* pg_temp.lt_r(t.team_id::bigint * 1000000 + k, 20)
		END AS created_at
	FROM lt_team t
	CROSS JOIN LATERAL generate_series(1, t.n) AS k
) s;

-- 2단계: 데일리 답변 확정((팀, 작성자, 날짜)마다 첫 후보 1건), 팀별 place 비율 계산
CREATE TEMP TABLE lt_feed_daily ON COMMIT DROP AS
SELECT
	r.*,
	(r.daily_candidate AND row_number() OVER (
		PARTITION BY r.team_id, r.author_idx, r.created_at::date, r.daily_candidate
		ORDER BY r.created_at, r.key) = 1) AS is_daily
FROM lt_feed_raw r;

-- 3단계: 피드 ID(created_at 순)와 나머지 속성
CREATE TEMP TABLE lt_feed ON COMMIT DROP AS
WITH d AS (
	SELECT
		x.*,
		least(1.0, 0.6 * count(*) OVER w / nullif(count(*) FILTER (WHERE NOT x.is_daily) OVER w, 0)) AS place_p
	FROM lt_feed_daily x
	WINDOW w AS (PARTITION BY x.team_id)
),
a AS (
	SELECT
		row_number() OVER (ORDER BY d.created_at, d.key) AS id,
		d.key,
		d.team_id,
		d.size,
		d.author_idx,
		d.created_at,
		d.is_daily,
		CASE WHEN d.is_daily THEN d.created_at::date END AS answer_date,
		CASE WHEN NOT d.is_daily AND pg_temp.lt_r(d.key, 23) < d.place_p
			THEN 1 + floor((:places) * power(pg_temp.lt_r(
				d.team_id::bigint * 1000 + floor(d.place_k * power(pg_temp.lt_r(d.key, 24), 3))::int, 25), 2))::bigint
		END AS place_id,
		CASE WHEN d.is_daily THEN 1
			WHEN pg_temp.lt_r(d.key, 26) < 0.5 THEN 1
			WHEN pg_temp.lt_r(d.key, 26) < 0.8 THEN 2
			ELSE 3
		END AS img_count,
		CASE WHEN d.is_daily THEN 0
			WHEN pg_temp.lt_r(d.key, 27) < 0.5 THEN 0
			WHEN pg_temp.lt_r(d.key, 27) < 0.8 THEN 1
			ELSE 2
		END AS tag_extra,
		least(d.size, floor(5 * pg_temp.lt_r(d.key, 28))::int) AS react_count,
		CASE WHEN pg_temp.lt_r(d.key, 29) < 0.001 THEN 500 + floor(1501 * pg_temp.lt_r(d.key, 30))::int
			WHEN pg_temp.lt_r(d.key, 31) < 0.8 THEN floor(4 * pg_temp.lt_r(d.key, 32))::int
			ELSE 0
		END AS comment_count,
		pg_temp.lt_r(d.key, 29) < 0.001 AS heavy_comments,
		d.created_date
	FROM d
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
		ELSE '부하 테스트 피드 ' || f.id || ' 입니다.' || repeat(' 오늘 팀 활동 기록', floor(4 * pg_temp.lt_r(f.key, 33))::int)
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
-- feed_tag: 작성자(ord 0) + 다른 멤버 0~2명. 다른 멤버는 작성자 slot에서 서로 다른 오프셋(1..size-1)만큼 떨어진 slot.
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
	-- 두 번째 오프셋: 첫 오프셋 o1(1..size-1) 다음부터 size-2칸 안에서 고르므로 o1과 다르고 0도 아니다(size >= 3)
	SELECT f.id, f.team_id, 2,
		(f.author_idx + 1
			+ (floor((f.size - 1) * pg_temp.lt_r(f.key, 40))::int + 1 + floor((f.size - 2) * pg_temp.lt_r(f.key, 41))::int)
				% (f.size - 1)) % f.size
	FROM lt_feed f
	WHERE f.tag_extra >= 2 AND f.size >= 3
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
		f.created_at + least(interval '2 days', LOCALTIMESTAMP - f.created_at) * pg_temp.lt_r(f.key * 8 + j, 51) AS created_at,
		(ARRAY['TAK_LEADER', 'TAK_SPARK', 'TAK_BURNER', 'TAK_BUILDER', 'TAK_FREE', 'TAK_CARE'])
			[1 + floor(6 * pg_temp.lt_r(f.key * 8 + j, 52))::int] AS reaction_type
	FROM lt_feed f
	CROSS JOIN LATERAL generate_series(0, f.react_count - 1) AS j
	WHERE f.react_count > 0
) x
JOIN lt_tm m ON m.team_id = x.team_id AND m.idx = x.member_idx
ORDER BY x.feed_id, x.j;

-- ---------------------------------------------------------------------------
-- feed_comment: ID는 created_at 순(실제처럼 시간 순으로 쌓임)
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
		f.key * 4096 + j AS ck,
		floor(f.size * pg_temp.lt_r(f.key * 4096 + j, 60))::int AS member_idx,
		f.img_base + 1 + floor(f.img_count * pg_temp.lt_r(f.key * 4096 + j, 61))::int AS feed_image_id,
		f.created_at
			+ least(CASE WHEN f.heavy_comments THEN interval '30 days' ELSE interval '3 days' END, LOCALTIMESTAMP - f.created_at)
				* pg_temp.lt_r(f.key * 4096 + j, 62) AS created_at
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

-- 통계 갱신과 visibility map 설정. 측정 도중 autovacuum이 돌아 결과가 흔들리지 않게 시드 직후 한 번 끝내 둔다.
VACUUM (ANALYZE) place;
VACUUM (ANALYZE) feed;
VACUUM (ANALYZE) feed_image;
VACUUM (ANALYZE) feed_tag;
VACUUM (ANALYZE) feed_reaction;
VACUUM (ANALYZE) feed_comment;

-- ---------------------------------------------------------------------------
-- 확인 쿼리
-- ---------------------------------------------------------------------------
\echo '== 건수'
SELECT
	(SELECT count(*) FROM place) AS places,
	(SELECT count(*) FROM feed) AS feeds,
	(SELECT count(*) FROM feed_image) AS feed_images,
	(SELECT count(*) FROM feed_tag) AS feed_tags,
	(SELECT count(*) FROM feed_reaction) AS feed_reactions,
	(SELECT count(*) FROM feed_comment) AS feed_comments;

\echo '== 팀 종류별 피드 (kind, 팀 수, 피드 합, 팀당 최소/평균/최대, 데일리 비율, place 비율, 이번 달 비율)'
SELECT
	CASE WHEN t.description = 'loadtest:big' THEN 'big' ELSE 'normal' END AS kind,
	count(*) AS teams,
	sum(c.n) AS feeds,
	min(c.n) AS min_per_team,
	round(avg(c.n)) AS avg_per_team,
	max(c.n) AS max_per_team,
	round(sum(c.daily)::numeric / sum(c.n), 3) AS daily_ratio,
	round(sum(c.with_place)::numeric / sum(c.n), 3) AS place_ratio,
	round(sum(c.this_month)::numeric / sum(c.n), 3) AS this_month_ratio
FROM team t
JOIN (
	SELECT team_id, count(*) AS n,
		count(*) FILTER (WHERE question_id IS NOT NULL) AS daily,
		count(*) FILTER (WHERE place_id IS NOT NULL) AS with_place,
		count(*) FILTER (WHERE created_at >= date_trunc('month', LOCALTIMESTAMP)) AS this_month
	FROM feed GROUP BY team_id
) c ON c.team_id = t.id
GROUP BY 1
ORDER BY 1;

\echo '== 큰 팀별 피드 (team_id, 피드 수, 이번 달 피드, 데일리, place 있음, 최다 장소 피드 수)'
SELECT f.team_id,
	count(*) AS feeds,
	count(*) FILTER (WHERE f.created_at >= date_trunc('month', LOCALTIMESTAMP)) AS this_month,
	count(*) FILTER (WHERE f.question_id IS NOT NULL) AS daily,
	count(*) FILTER (WHERE f.place_id IS NOT NULL) AS with_place,
	(SELECT max(cnt) FROM (SELECT count(*) AS cnt FROM feed x WHERE x.team_id = f.team_id AND x.place_id IS NOT NULL
		GROUP BY x.place_id) p) AS top_place_feeds,
	(SELECT count(DISTINCT x.place_id) FROM feed x WHERE x.team_id = f.team_id) AS distinct_places
FROM feed f
WHERE f.team_id IN (SELECT id FROM team WHERE description = 'loadtest:big')
GROUP BY f.team_id
ORDER BY f.team_id;

\echo '== 이번 달 피드가 없는 팀 수 (0이어야 한다), 기간 (최소/최대 created_at)'
SELECT
	(SELECT count(*) FROM team t WHERE NOT EXISTS (
		SELECT 1 FROM feed f WHERE f.team_id = t.id AND f.created_at >= date_trunc('month', LOCALTIMESTAMP))) AS teams_without_this_month,
	(SELECT min(created_at) FROM feed) AS min_created_at,
	(SELECT max(created_at) FROM feed) AS max_created_at,
	LOCALTIMESTAMP AS now_kst;

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
		WHERE c.created_at < f.created_at OR c.created_at > LOCALTIMESTAMP) AS comment_time_out_of_range,
	(SELECT count(*) FROM feed WHERE deleted_at IS NOT NULL) AS soft_deleted_feeds,
	(SELECT count(*) FROM feed WHERE created_at > LOCALTIMESTAMP) AS future_feeds;

\echo '== 데일리 답변 검사 (bad_*는 0이어야 한다)'
SELECT
	count(*) AS daily_answers,
	count(*) FILTER (WHERE daily_answer_active_key IS DISTINCT FROM
		team_id || ':' || team_member_id || ':' || question_id || ':' || to_char(question_answer_date, 'YYYY-MM-DD')) AS bad_key,
	count(*) FILTER (WHERE question_answer_date <> created_at::date) AS bad_answer_date,
	count(*) FILTER (WHERE place_id IS NOT NULL) AS bad_with_place,
	count(*) FILTER (WHERE (SELECT count(*) FROM feed_image i WHERE i.feed_id = feed.id) <> 1) AS bad_image_count
FROM feed
WHERE question_id IS NOT NULL;

\echo '== 이미지 수 분포 (장 수, 피드 수)'
SELECT img_count, count(*) AS feeds
FROM (SELECT feed_id, count(*) AS img_count FROM feed_image GROUP BY feed_id) x
GROUP BY 1 ORDER BY 1;

\echo '== 댓글 수 분포 (구간, 피드 수, 큰 팀 피드 수)'
SELECT bucket, count(*) AS feeds, count(*) FILTER (WHERE team_id <= 3) AS big_team_feeds
FROM (
	SELECT f.team_id,
		CASE WHEN coalesce(c.cnt, 0) = 0 THEN '0'
			WHEN c.cnt <= 3 THEN '1-3'
			WHEN c.cnt < 500 THEN '4-499'
			ELSE '500+'
		END AS bucket
	FROM feed f
	LEFT JOIN (SELECT feed_id, count(*) AS cnt FROM feed_comment GROUP BY feed_id) c ON c.feed_id = f.id
) x
GROUP BY 1 ORDER BY 1;

\echo '== 지역별 place 수 상위 10'
SELECT region, count(*) AS places FROM place GROUP BY region ORDER BY 2 DESC, 1 LIMIT 10;
