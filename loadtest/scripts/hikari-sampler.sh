#!/usr/bin/env bash
# Hikari 커넥션 풀 샘플러 — #217
#
# 1초마다 /actuator/prometheus 에서 hikaricp_connections_active/pending/idle(과 max)을 읽어 CSV에 한 줄씩 쓴다.
# SIGINT/SIGTERM(Ctrl+C, kill)을 받으면 멈춘다.
# 샘플 시각은 시작 시각 + n × INTERVAL에 맞춘다(처리 시간만큼 간격이 밀리지 않게). 응답이 2초 안에 없으면 그 줄은
# 값이 비어 있고 놓친 시각은 건너뛴다. 실제 시각은 epoch_ms 열로 본다.
# Git Bash는 프로세스 생성이 느려서(외부 명령 하나에 수십~수백 ms) 시각은 bash 내장($EPOCHREALTIME, printf %T)으로 얻는다.
#
# 사용
#   bash loadtest/scripts/hikari-sampler.sh <출력 CSV> &      # 백그라운드
#   kill <pid>                                                  # 종료
# env: MANAGEMENT_URL(actuator 주소, 기본 http://localhost:8081 = loadtest/.env의 MANAGEMENT_SERVER_PORT), INTERVAL(정수 초, 기본 1)
#
# CSV 열: epoch_ms, time(호스트 로컬 시각), active, pending, idle, max
set -uo pipefail

OUT="${1:?usage: $0 <출력 CSV>}"
MANAGEMENT_URL="${MANAGEMENT_URL:-http://localhost:8081}"
INTERVAL="${INTERVAL:-1}"
mkdir -p "$(dirname "$OUT")"

running=1
trap 'running=0' INT TERM

now_us() {
	local t="${EPOCHREALTIME/[.,]/}"
	echo "$t"
}

echo "epoch_ms,time,active,pending,idle,max" > "$OUT"
step_us=$((INTERVAL * 1000000))
next_us=$(now_us)
while [ "$running" -eq 1 ]; do
	ts_us=$(now_us)
	printf -v ts '%(%Y-%m-%dT%H:%M:%S)T' "${ts_us:0:-6}"
	# 예: hikaricp_connections_active{pool="HikariPool-1"} 3.0  -> 마지막 필드
	vals="$(curl -s --max-time 2 "$MANAGEMENT_URL/actuator/prometheus" 2>/dev/null | awk '
		/^hikaricp_connections_active[{ ]/  { a = $NF }
		/^hikaricp_connections_pending[{ ]/ { p = $NF }
		/^hikaricp_connections_idle[{ ]/    { i = $NF }
		/^hikaricp_connections_max[{ ]/     { m = $NF }
		END { printf "%s,%s,%s,%s", a, p, i, m }')"
	echo "$((ts_us / 1000)),$ts,$vals" >> "$OUT"

	next_us=$((next_us + step_us))
	now=$(now_us)
	while [ "$next_us" -le "$now" ]; do
		next_us=$((next_us + step_us))
	done
	wait_us=$((next_us - now))
	# sleep을 백그라운드로 두고 wait해야 시그널을 받자마자 trap이 돈다.
	sleep "$((wait_us / 1000000)).$(printf '%06d' $((wait_us % 1000000)))" &
	wait $! 2>/dev/null || true
done
echo "hikari sampler stopped: $OUT ($(($(wc -l < "$OUT") - 1)) samples)" >&2
