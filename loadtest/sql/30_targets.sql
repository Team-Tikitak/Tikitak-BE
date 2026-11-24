-- =============================================================================
-- 30_targets.sql : k6 입력 JSON 추출 (읽기 전용) — #217
--
-- 전제: 00_common.sql, 10_feed.sql(, 20_notification.sql)을 실행한 뒤. DB를 바꾸지 않는다.
-- 실행:  stdout 전체가 JSON 하나다. -At(정렬 없음, 튜플만)로 실행해 파일로 저장한다.
--          mkdir -p loadtest/k6/data
--          docker compose -f loadtest/docker-compose.yml exec -T postgres \
--            psql -U tikitak -d tikitak -At -f - < loadtest/sql/30_targets.sql > loadtest/k6/data/targets.json
-- 변수:  big_top_places   큰 팀에서 내보낼 장소 수(피드 수 상위, 기본 100). 일반 팀은 장소를 모두 내보낸다.
--
-- JSON 구조
--   generatedAt        : 추출 시각(KST, 참고용)
--   teams[]            : 모든 팀. teamId, isBig, members[{memberId, teamMemberId}],
--                        places[{externalPlaceId, feedCount}] (그 팀에서 피드가 있는 장소, 피드 수 내림차순.
--                        GET /teams/{teamId}/feeds?placeId=<externalPlaceId> 에 쓴다)
--   commentHeavyFeeds[]: 활성 댓글이 500개 이상인 피드. teamId, feedId, commentCount, memberId(그 팀 멤버인 작성자의 회원 ID)
--   deepCursors[]      : 큰 팀별로 피드 목록(created_at desc, id desc)의 1만·5만·9만 번째 피드.
--                        teamId, position, cursor. cursor = FeedService.encodeCursor와 같은 `LocalDateTime.toString() + "_" + id`.
--                        이 cursor로 요청하면 position번째 다음 피드부터 나온다.
-- =============================================================================

\set ON_ERROR_STOP on
\set QUIET on

\if :{?big_top_places}
\else
	\set big_top_places 100
\endif

SET TIME ZONE 'Asia/Seoul';

DO $$
BEGIN
	IF NOT EXISTS (SELECT 1 FROM feed) THEN
		RAISE EXCEPTION '30_targets.sql: feed가 비어 있다. 10_feed.sql을 먼저 실행한다.';
	END IF;
END $$;

-- java.time.LocalDateTime.toString()과 같은 문자열.
--   초와 나노초가 모두 0이면 "yyyy-MM-ddTHH:mm", 나노초가 0이면 "...:ss",
--   밀리초 단위로 떨어지면 소수 3자리, 아니면 6자리(Postgres는 마이크로초까지라 9자리는 나오지 않는다).
CREATE FUNCTION pg_temp.lt_java_ldt(ts timestamp) RETURNS text
LANGUAGE sql IMMUTABLE AS $$
	SELECT to_char(ts, 'YYYY-MM-DD"T"HH24:MI')
		|| CASE
			WHEN to_char(ts, 'SS') = '00' AND to_char(ts, 'US')::int = 0 THEN ''
			WHEN to_char(ts, 'US')::int = 0 THEN ':' || to_char(ts, 'SS')
			WHEN to_char(ts, 'US')::int % 1000 = 0 THEN ':' || to_char(ts, 'SS') || '.' || to_char(ts, 'MS')
			ELSE ':' || to_char(ts, 'SS') || '.' || to_char(ts, 'US')
		END
$$;

WITH members AS (
	SELECT tm.team_id,
		json_agg(json_build_object('memberId', tm.member_id, 'teamMemberId', tm.id) ORDER BY tm.id) AS members
	FROM team_member tm
	WHERE tm.status = 'ACTIVE'
	GROUP BY tm.team_id
),
place_counts AS (
	SELECT f.team_id, p.external_place_id, count(*) AS feed_count,
		row_number() OVER (PARTITION BY f.team_id ORDER BY count(*) DESC, p.external_place_id) AS rk
	FROM feed f
	JOIN place p ON p.id = f.place_id
	WHERE f.deleted_at IS NULL AND p.external_place_id IS NOT NULL
	GROUP BY f.team_id, p.external_place_id
),
places AS (
	SELECT pc.team_id,
		json_agg(json_build_object('externalPlaceId', pc.external_place_id, 'feedCount', pc.feed_count)
			ORDER BY pc.rk) AS places
	FROM place_counts pc
	JOIN team t ON t.id = pc.team_id
	WHERE t.description <> 'loadtest:big' OR pc.rk <= (:big_top_places)
	GROUP BY pc.team_id
),
teams AS (
	SELECT json_agg(json_build_object(
			'teamId', t.id,
			'isBig', t.description = 'loadtest:big',
			'members', m.members,
			'places', coalesce(p.places, '[]'::json)
		) ORDER BY t.id) AS teams
	FROM team t
	JOIN members m ON m.team_id = t.id
	LEFT JOIN places p ON p.team_id = t.id
	WHERE t.status = 'ACTIVE'
),
heavy AS (
	SELECT json_agg(json_build_object(
			'teamId', f.team_id,
			'feedId', f.id,
			'commentCount', c.cnt,
			'memberId', tm.member_id
		) ORDER BY f.id) AS feeds
	FROM (
		SELECT feed_id, count(*) AS cnt FROM feed_comment WHERE NOT is_deleted GROUP BY feed_id HAVING count(*) >= 500
	) c
	JOIN feed f ON f.id = c.feed_id AND f.deleted_at IS NULL
	JOIN team_member tm ON tm.id = f.team_member_id
),
ranked AS (
	SELECT f.team_id, f.id, f.created_at,
		row_number() OVER (PARTITION BY f.team_id ORDER BY f.created_at DESC, f.id DESC) AS position
	FROM feed f
	WHERE f.deleted_at IS NULL
		AND f.team_id IN (SELECT id FROM team WHERE description = 'loadtest:big')
),
cursors AS (
	SELECT json_agg(json_build_object(
			'teamId', r.team_id,
			'position', r.position,
			'cursor', pg_temp.lt_java_ldt(r.created_at) || '_' || r.id
		) ORDER BY r.team_id, r.position) AS cursors
	FROM ranked r
	WHERE r.position IN (10000, 50000, 90000)
)
SELECT json_build_object(
	'generatedAt', pg_temp.lt_java_ldt(date_trunc('second', LOCALTIMESTAMP)),
	'teams', (SELECT teams FROM teams),
	'commentHeavyFeeds', coalesce((SELECT feeds FROM heavy), '[]'::json),
	'deepCursors', coalesce((SELECT cursors FROM cursors), '[]'::json)
);
