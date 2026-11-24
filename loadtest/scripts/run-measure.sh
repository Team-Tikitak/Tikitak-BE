#!/usr/bin/env bash
# 측정 한 번을 묶어서 실행한다 — #217
#   1) 앱 health 확인  2) pg_stat_statements reset  3) Hikari 샘플러 시작
#   4) k6 실행  5) 샘플러 종료  6) pg_stat_statements dump
# 결과는 모두 loadtest/results/raw/<RUN_ID>-* 로 남는다(RUN_ID = 호스트 로컬 시각. Git Bash의 date는 TZ=Asia/Seoul을 해석하지 못해 UTC로 떨어지므로 TZ를 지정하지 않는다).
#
# 사용 (저장소 루트 또는 어디서든)
#   bash loadtest/scripts/run-measure.sh mixed
#   bash loadtest/scripts/run-measure.sh ramp
#   bash loadtest/scripts/run-measure.sh solo <api>
# k6 env(WARMUP, DURATION, RATE, STAGE_DURATION, TRANSITION, MAX_VUS, BASE_URL 등)는 그대로 넘어간다. 예:
#   DURATION=10s bash loadtest/scripts/run-measure.sh solo map_pins
# JWT_SECRET이 없으면 loadtest/.env 에서 읽는다.
#
# 종료 코드는 k6의 것을 돌려준다(threshold 실패면 99). 실패해도 샘플러 종료와 덤프는 한다.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

MODE="${1:-mixed}"
API="${2:-}"
if [ "$MODE" = "solo" ] && [ -z "$API" ]; then
	echo "usage: $0 solo <api>" >&2
	exit 2
fi
export MODE API
export BASE_URL="${BASE_URL:-http://localhost:8080}"
if [ -z "${JWT_SECRET:-}" ]; then
	JWT_SECRET="$(sed -n 's/^JWT_SECRET=//p' loadtest/.env | tr -d '\r')"
	export JWT_SECRET
fi
if [ -z "$JWT_SECRET" ]; then
	echo "JWT_SECRET이 없다(loadtest/.env 확인)" >&2
	exit 2
fi
for f in loadtest/k6/data/targets.json loadtest/k6/data/comment-feeds.json; do
	if [ ! -s "$f" ]; then
		echo "$f 가 없다. README 6-2(targets.json)와 8-1(comment-feeds.json)을 먼저 실행한다." >&2
		exit 2
	fi
done
if ! curl -sf --max-time 5 "$BASE_URL/actuator/health" > /dev/null; then
	echo "앱이 응답하지 않는다: $BASE_URL/actuator/health" >&2
	exit 2
fi

RAW="loadtest/results/raw"
mkdir -p "$RAW"
RUN_ID="${RUN_ID:-$(date '+%Y%m%d-%H%M%S')}"
export RUN_ID
LABEL="$MODE${API:+-$API}"
PREFIX="$RAW/$RUN_ID-$LABEL"

echo "== run $RUN_ID ($LABEL) =="
bash loadtest/scripts/pgss.sh reset || { echo "pg_stat_statements 리셋 실패. DB 컨테이너 상태를 확인한다. 측정을 중단한다." >&2; exit 1; }

bash loadtest/scripts/hikari-sampler.sh "$PREFIX-hikari.csv" &
SAMPLER_PID=$!
stop_sampler() {
	if kill -0 "$SAMPLER_PID" 2>/dev/null; then
		kill "$SAMPLER_PID" 2>/dev/null
		wait "$SAMPLER_PID" 2>/dev/null
	fi
}
trap 'stop_sampler; exit 130' INT TERM

K6_ARGS=()
if [ "$MODE" = "ramp" ]; then
	# 단계별 dropped_iterations와 시계열을 나중에 보려고 원시 지표를 남긴다(gzip CSV).
	K6_ARGS+=(--out "csv=$PREFIX-k6-metrics.csv.gz")
fi

k6 run --quiet "${K6_ARGS[@]}" loadtest/k6/read-api-baseline.js 2>&1 | tee "$PREFIX-k6.log"
K6_STATUS=${PIPESTATUS[0]}

stop_sampler
trap - INT TERM
bash loadtest/scripts/pgss.sh dump "$PREFIX"

echo "== hikari max (active / pending) =="
awk -F, 'NR > 1 && $3 != "" { if ($3 > a) a = $3; if ($4 > p) p = $4; n++ }
	END { printf "samples=%d active_max=%s pending_max=%s\n", n, a + 0, p + 0 }' "$PREFIX-hikari.csv"
awk -F, 'NR > 1 && $3 == "" { n++ } END { if (n) printf "empty samples=%d (actuator가 2초 안에 응답하지 않음)\n", n }' "$PREFIX-hikari.csv"
if [ "$MODE" = "ramp" ] && [ -s "$PREFIX-stages.csv" ]; then
	echo "== hikari max by stage (stages.csv 구간, 빈 샘플 제외) =="
	awk -F, '
		FNR == NR { if (FNR > 1) { st[++k] = $1; from[k] = $3; to[k] = $4 } next }
		FNR > 1 && $3 != "" {
			for (i = 1; i <= k; i++) if ($1 >= from[i] && $1 < to[i]) {
				c[i]++; if ($3 > a[i]) a[i] = $3; if ($4 > p[i]) p[i] = $4
			}
		}
		END { for (i = 1; i <= k; i++) printf "%-11s samples=%-4d active_max=%-4s pending_max=%s\n", st[i], c[i], a[i] + 0, p[i] + 0 }
	' "$PREFIX-stages.csv" "$PREFIX-hikari.csv"
fi
echo "== files =="
ls -1 "$RAW" | grep "^$RUN_ID-" | sed "s|^|$RAW/|"
echo "k6 exit code: $K6_STATUS"
exit "$K6_STATUS"
