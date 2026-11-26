-- #220: 지도 핀 조회(FeedRepository.findMapPinsByTeamId)가 장소별 개수를 index-only scan으로 세고,
-- 장소마다 최신 피드 1건(created_at DESC, id DESC)을 인덱스 탐색으로 찾게 한다.
CREATE INDEX idx_feed_team_place_latest
    ON feed (team_id, place_id, created_at DESC, id DESC)
    WHERE deleted_at IS NULL AND place_id IS NOT NULL;
