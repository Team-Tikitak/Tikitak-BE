#!/usr/bin/env bash
# 댓글 조회 대상 피드 추출 (읽기 전용) — #217
#
# k6 댓글 요청(api=comments)의 "일반 피드" 대상으로, 팀마다 피드 목록 첫 페이지에 보이는 최신 피드 N개(기본 20,
# 피드 목록 기본 size와 같다)의 ID를 뽑아 loadtest/k6/data/comment-feeds.json 으로 저장한다.
# targets.json(30_targets.sql)에는 댓글 많은 피드만 있고 일반 피드 ID가 없어서 따로 뽑는다.
# k6 setup()에서 API로 가져오지 않는 이유: solo 측정의 pg_stat_statements에 피드 목록 쿼리가 섞이지 않게 하려고.
#
# 사용: bash loadtest/scripts/extract-comment-feeds.sh [N]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

N="${1:-20}"
OUT="loadtest/k6/data/comment-feeds.json"
mkdir -p loadtest/k6/data

docker compose -f loadtest/docker-compose.yml exec -T postgres \
	psql -U tikitak -d tikitak -At -v ON_ERROR_STOP=1 -v n="$N" -f - > "$OUT.tmp" <<'SQL'
\set QUIET on
SELECT coalesce(json_agg(json_build_object('teamId', t.id, 'feedIds', f.ids) ORDER BY t.id), '[]'::json)
FROM team t
CROSS JOIN LATERAL (
	SELECT json_agg(s.id ORDER BY s.created_at DESC, s.id DESC) AS ids
	FROM (
		SELECT id, created_at FROM feed
		WHERE team_id = t.id AND deleted_at IS NULL
		ORDER BY created_at DESC, id DESC
		LIMIT :n
	) s
) f
WHERE t.status = 'ACTIVE' AND f.ids IS NOT NULL;
SQL

mv "$OUT.tmp" "$OUT"
echo "wrote $OUT ($(wc -c < "$OUT") bytes)"
