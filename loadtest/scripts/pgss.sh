#!/usr/bin/env bash
# pg_stat_statements 초기화와 덤프 (loadtest 컨테이너 전용) — #217
#
# 사용
#   bash loadtest/scripts/pgss.sh reset
#   bash loadtest/scripts/pgss.sh dump <파일 접두사> [N]
#     -> <접두사>-pgss.csv  : 상위 N개(기본 50, total_exec_time 순) 쿼리. calls, rows, 총·평균·최대 시간(ms), 공유 버퍼 hit/read, query 전문
#        <접두사>-pgss.txt  : 사람이 읽는 표(쿼리는 앞 160자) + 전체 합계
#   접두사 예: loadtest/results/raw/20261124-190000-solo-map_pins
#
# 이 DB(tikitak)의 문장만 보고, pg_stat_statements 자체를 다루는 문장은 뺀다.
# 덤프 시점의 누적값이므로 reset 직후부터 측정이 끝날 때까지의 합이다. 요청당 쿼리 수 = calls / k6 요청 수.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

psql_c() {
	docker compose -f loadtest/docker-compose.yml exec -T postgres psql -U tikitak -d tikitak -v ON_ERROR_STOP=1 "$@"
}

cmd="${1:-}"
case "$cmd" in
	reset)
		psql_c -Atc "SELECT pg_stat_statements_reset()" > /dev/null
		echo "pg_stat_statements reset at $(date '+%Y-%m-%dT%H:%M:%S%z')"
		;;
	dump)
		prefix="${2:?dump <파일 접두사> [N]}"
		n="${3:-50}"
		mkdir -p "$(dirname "$prefix")"
		filter="s.dbid = (SELECT oid FROM pg_database WHERE datname = current_database())
			AND s.query NOT ILIKE '%pg_stat_statements%'"
		psql_c -c "COPY (
			SELECT s.calls, s.rows,
				round(s.total_exec_time::numeric, 2) AS total_ms,
				round(s.mean_exec_time::numeric, 3) AS mean_ms,
				round(s.max_exec_time::numeric, 2) AS max_ms,
				s.shared_blks_hit, s.shared_blks_read,
				s.queryid, s.query
			FROM pg_stat_statements s
			WHERE $filter
			ORDER BY s.total_exec_time DESC
			LIMIT $n
		) TO STDOUT WITH (FORMAT csv, HEADER)" > "$prefix-pgss.csv"
		psql_c -P pager=off > "$prefix-pgss.txt" <<SQL
\echo '== total (since reset) =='
SELECT count(*) AS statements, sum(calls) AS calls, round(sum(total_exec_time)::numeric, 1) AS total_ms,
	sum(rows) AS rows, sum(shared_blks_read) AS shared_blks_read
FROM pg_stat_statements s WHERE $filter;
\echo '== top $n by total_exec_time =='
SELECT s.calls, s.rows,
	round(s.total_exec_time::numeric, 1) AS total_ms,
	round(s.mean_exec_time::numeric, 3) AS mean_ms,
	round(s.max_exec_time::numeric, 1) AS max_ms,
	s.shared_blks_read AS blks_read,
	left(regexp_replace(s.query, '\s+', ' ', 'g'), 160) AS query
FROM pg_stat_statements s
WHERE $filter
ORDER BY s.total_exec_time DESC
LIMIT $n;
SQL
		echo "wrote $prefix-pgss.csv, $prefix-pgss.txt"
		;;
	*)
		echo "usage: $0 reset | dump <파일 접두사> [N]" >&2
		exit 2
		;;
esac
