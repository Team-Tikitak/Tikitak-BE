#!/usr/bin/env bash
# 오늘의 질문 공개 알림 배치 측정 한 번을 묶어서 실행한다 — #215
#   0) 환경 확인: compose 프로젝트 이름 = loadtest/.env의 LOADTEST_PROJECT, 그 프로젝트의 postgres 컨테이너가 떠 있음,
#      이 폴더 앱 포트(MANAGEMENT_SERVER_PORT)에 다른 앱이 떠 있지 않음, bootJar jar 있음
#   1) 40_bulk_notification.sql 실행(알림·디바이스 토큰 초기화와 재시드)
#   2) pg_stat_statements reset
#   3) Hikari 샘플러와 JVM 샘플러(process_cpu_usage, 힙, 스레드) 시작
#   4) 앱을 dev,loadtest 프로필로 백그라운드 기동. 기동 직후 LoadTestDailyQuestionOpenedRunner가 배치를 한 번 실행한다
#   5) 배치 종료 로그("Load test daily question opened batch run finished.")를 기다림
#   6) /actuator/prometheus 스냅샷 저장
#   7) 샘플러 종료  8) pg_stat_statements 덤프  9) 앱 종료
# 결과는 loadtest/results/raw/<RUN_ID>-batch[-<label>]-* 로 남는다(RUN_ID = 호스트 로컬 시각 yyyyMMdd-HHmmss).
# 측정 조건과 요약(recipientCount, elapsedMs 등)은 -summary.txt 하나에 모은다.
#
# 사용 (저장소 루트 또는 어디서든, Git Bash)
#   bash loadtest/scripts/run-batch-measure.sh                               # 지연 0ms, 실패율 0
#   bash loadtest/scripts/run-batch-measure.sh --delay-ms 50 --label 10k-50ms
#   bash loadtest/scripts/run-batch-measure.sh --delay-ms 0 --failure-rate 0.01 --invalid-token-rate 0.005 --seed 7
#
# 가짜 발송기 설정(우선순위: 인자 > 셸 env > loadtest/.env > 기본값)
#   --delay-ms N              / LOADTEST_PUSH_DELAY_MS            send() 호출(회원 한 명)마다 쉬는 ms. 기본 0
#   --failure-rate X          / LOADTEST_PUSH_FAILURE_RATE        토큰별 FAILED 비율 0~1. 기본 0
#   --invalid-token-rate X    / LOADTEST_PUSH_INVALID_TOKEN_RATE  토큰별 INVALID_TOKEN 비율 0~1. 기본 0
#   --seed N                  / LOADTEST_PUSH_SEED                토큰별 결과 해시 시드(정수). 기본 0
# 그 밖의 env
#   --label TEXT / RUN_LABEL   파일 이름에 붙일 이름(예: 10k-0ms). 영문·숫자·-·_ 만
#   TOKEN_SEED                 40_bulk_notification.sql의 -v seed(-1.0~1.0). 없으면 SQL 기본값(0.215)
#   BATCH_TIMEOUT              앱 기동부터 배치 종료 로그까지 기다리는 최대 초(기본 21600 = 6시간). 넘으면 앱을 끄고 실패
#   POLL_INTERVAL              배치 종료 로그를 확인하는 간격(초, 기본 2)
#   JVM_INTERVAL               JVM 샘플러 간격(초, 기본 5). 0이면 끈다(-jvm.csv는 헤더만 남는다)
#   JAR                        실행할 jar(기본 build/libs/ 아래 *-SNAPSHOT.jar 중 -plain이 아닌 것)
#   RUN_ID                     결과 파일 접두사(기본 호스트 로컬 시각)
#
# 앱 env는 loadtest/.env를 읽은 뒤 아래 값을 덮어쓴다.
#   SPRING_PROFILES_ACTIVE=dev,loadtest
#   LOADTEST_DAILY_QUESTION_OPENED_RUN_ON_STARTUP=true
#   DAILY_QUESTION_OPENED_NOTIFICATION_CRON=-   (스케줄 실행을 끈다. 측정 중 자정 cron이 겹치지 않게)
#   LOADTEST_PUSH_* (위 값)
#
# 종료 코드: 0 = 배치 종료 로그 확인, 1 = 실패(앱 기동 실패, 시간 초과 등), 2 = 사전 확인 실패.
# 어느 경우든 샘플러와 앱은 끄고 끝낸다.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

# PC 공용 측정 잠금(README 10-4) 안에서 다시 실행한다. 이미 잠금 안이면 measure-lock.sh가 다시 잡지 않고 바로 실행한다.
# --help는 잠금 없이 본다. 잠금은 이 스크립트가 끝날 때(앱 종료 뒤)까지 유지된다.
case " $* " in *" -h "* | *" --help "*) ;; *)
	if [ "${_RUN_BATCH_MEASURE_IN_LOCK:-}" != 1 ]; then
		_RUN_BATCH_MEASURE_IN_LOCK=1 exec bash "$ROOT/loadtest/scripts/measure-lock.sh" bash "$ROOT/loadtest/scripts/run-batch-measure.sh" "$@"
	fi
	unset _RUN_BATCH_MEASURE_IN_LOCK ;;
esac

usage() {
	awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "$0"
}

ARG_DELAY_MS=""
ARG_FAILURE_RATE=""
ARG_INVALID_TOKEN_RATE=""
ARG_SEED=""
RUN_LABEL="${RUN_LABEL:-}"
while [ $# -gt 0 ]; do
	case "$1" in
		--delay-ms) ARG_DELAY_MS="${2:?--delay-ms 값}"; shift 2 ;;
		--failure-rate) ARG_FAILURE_RATE="${2:?--failure-rate 값}"; shift 2 ;;
		--invalid-token-rate) ARG_INVALID_TOKEN_RATE="${2:?--invalid-token-rate 값}"; shift 2 ;;
		--seed) ARG_SEED="${2:?--seed 값}"; shift 2 ;;
		--label) RUN_LABEL="${2:?--label 값}"; shift 2 ;;
		-h | --help) usage; exit 0 ;;
		*) echo "알 수 없는 인자: $1 (--help 참고)" >&2; exit 2 ;;
	esac
done

ENV_FILE="loadtest/.env"
COMPOSE=(docker compose -f loadtest/docker-compose.yml)

# loadtest/.env에서 KEY=값 한 줄을 읽는다(없으면 빈 값)
env_file_value() {
	sed -n "s/^$1=//p" "$ENV_FILE" 2>/dev/null | tr -d '\r' | tail -n 1
}
# 인자 > 셸 env > loadtest/.env > 기본값
pick() {
	local arg="$1" name="$2" default="$3" file_value
	if [ -n "$arg" ]; then
		echo "$arg"
	elif [ -n "${!name:-}" ]; then
		echo "${!name}"
	else
		file_value="$(env_file_value "$name")"
		echo "${file_value:-$default}"
	fi
}
PUSH_DELAY_MS="$(pick "$ARG_DELAY_MS" LOADTEST_PUSH_DELAY_MS 0)"
PUSH_FAILURE_RATE="$(pick "$ARG_FAILURE_RATE" LOADTEST_PUSH_FAILURE_RATE 0)"
PUSH_INVALID_TOKEN_RATE="$(pick "$ARG_INVALID_TOKEN_RATE" LOADTEST_PUSH_INVALID_TOKEN_RATE 0)"
PUSH_SEED="$(pick "$ARG_SEED" LOADTEST_PUSH_SEED 0)"
BATCH_TIMEOUT="${BATCH_TIMEOUT:-21600}"
POLL_INTERVAL="${POLL_INTERVAL:-2}"
JVM_INTERVAL="${JVM_INTERVAL:-5}"
if ! [[ "$BATCH_TIMEOUT" =~ ^[0-9]+$ && "$POLL_INTERVAL" =~ ^[1-9][0-9]*$ && "$JVM_INTERVAL" =~ ^[0-9]+$ ]]; then
	echo "BATCH_TIMEOUT·POLL_INTERVAL·JVM_INTERVAL은 정수 초여야 한다(POLL_INTERVAL은 1 이상)" >&2
	exit 2
fi

if ! [[ "$PUSH_DELAY_MS" =~ ^[0-9]+$ ]]; then
	echo "delay-ms는 0 이상의 정수여야 한다: $PUSH_DELAY_MS" >&2
	exit 2
fi
for v in "$PUSH_FAILURE_RATE" "$PUSH_INVALID_TOKEN_RATE"; do
	if ! [[ "$v" =~ ^(0|1|0?\.[0-9]+|1\.0*)$ ]]; then
		echo "비율은 0~1 사이 소수여야 한다: $v" >&2
		exit 2
	fi
done
if ! [[ "$PUSH_SEED" =~ ^-?[0-9]+$ ]]; then
	echo "seed는 정수여야 한다: $PUSH_SEED" >&2
	exit 2
fi
if [ -n "$RUN_LABEL" ] && ! [[ "$RUN_LABEL" =~ ^[A-Za-z0-9_-]+$ ]]; then
	echo "label은 영문·숫자·-·_ 만 쓴다: $RUN_LABEL" >&2
	exit 2
fi
# failure-rate + invalid-token-rate <= 1 은 앱이 기동할 때 검사한다(LoadTestProperties). 넘으면 기동 실패로 끝난다.

# ---------------------------------------------------------------------------
# 0) 환경 확인
# ---------------------------------------------------------------------------
if [ ! -f "$ENV_FILE" ]; then
	echo "$ENV_FILE 가 없다. README 3번(env 준비)과 10-1(폴더별 이름·포트)을 먼저 한다." >&2
	exit 2
fi
if grep -q $'\r' "$ENV_FILE"; then
	echo "$ENV_FILE 줄 끝이 CRLF다. source가 값 끝에 \\r을 붙이므로 LF로 바꾼다(README 3번)." >&2
	exit 2
fi
PROJECT="$(env_file_value LOADTEST_PROJECT)"
PROJECT="${PROJECT:-tikitak-loadtest}"
CONFIG_NAME="$("${COMPOSE[@]}" config 2>/dev/null | tr -d '\r' | sed -n 's/^name: //p' | head -n 1)"
if [ -z "$CONFIG_NAME" ] || [ "$CONFIG_NAME" != "$PROJECT" ]; then
	echo "compose 프로젝트 이름($CONFIG_NAME)이 $ENV_FILE의 LOADTEST_PROJECT($PROJECT)와 다르다. 셸 env의 LOADTEST_PROJECT를 확인한다(README 10-1)." >&2
	exit 2
fi
PG_CONTAINER="$PROJECT-postgres"
if [ "$(docker inspect -f '{{.State.Running}}' "$PG_CONTAINER" 2>/dev/null | tr -d '\r')" != "true" ]; then
	echo "$PG_CONTAINER 컨테이너가 떠 있지 않다. docker compose -f loadtest/docker-compose.yml up -d --wait 를 먼저 한다." >&2
	exit 2
fi
# 그 컨테이너가 이 폴더의 compose로 만든 것인지 확인한다. .env에서 LOADTEST_PROJECT가 빠지면 .env 값과 config name이
# 둘 다 기본값(tikitak-loadtest)으로 풀려 위 확인을 통과하고, 다음 단계의 TRUNCATE가 다른 폴더(#217) DB에서 실행된다.
normalize_dir() { tr -d '\r' | tr '\\' '/' | sed 's#/*$##' | tr '[:upper:]' '[:lower:]'; }
PG_WORKDIR="$(docker inspect -f '{{index .Config.Labels "com.docker.compose.project.working_dir"}}' "$PG_CONTAINER" 2>/dev/null | normalize_dir)"
THIS_DIR="$( (pwd -W 2>/dev/null || pwd) | normalize_dir)/loadtest"
if [ -z "$PG_WORKDIR" ] || [ "$PG_WORKDIR" != "$THIS_DIR" ]; then
	echo "$PG_CONTAINER 는 다른 폴더의 compose로 만든 컨테이너다(working_dir=$PG_WORKDIR, 이 폴더=$THIS_DIR)." >&2
	echo "loadtest/.env의 LOADTEST_PROJECT를 이 폴더 전용 값으로 고친다(README 10-1, 10-3)." >&2
	exit 2
fi
APP_MANAGEMENT_PORT="$(env_file_value MANAGEMENT_SERVER_PORT)"
APP_MANAGEMENT_PORT="${APP_MANAGEMENT_PORT:-8081}"
export MANAGEMENT_URL="http://localhost:$APP_MANAGEMENT_PORT"
if curl -s --max-time 2 -o /dev/null "$MANAGEMENT_URL/actuator/health"; then
	echo "$MANAGEMENT_URL 에 이미 앱이 떠 있다. 그 앱을 끄고 다시 실행한다(배치 측정은 매 회차 새로 기동한다)." >&2
	exit 2
fi
if [ -z "${JAR:-}" ]; then
	JAR="$(ls -1 build/libs/*-SNAPSHOT.jar 2>/dev/null | grep -v -- '-plain' | head -n 1)"
fi
if [ -z "$JAR" ] || [ ! -f "$JAR" ]; then
	echo "실행할 jar가 없다. ./gradlew bootJar 를 먼저 한다(또는 JAR=경로)." >&2
	exit 2
fi

RAW="loadtest/results/raw"
mkdir -p "$RAW"
RUN_ID="${RUN_ID:-$(date '+%Y%m%d-%H%M%S')}"
LABEL="batch${RUN_LABEL:+-$RUN_LABEL}"
PREFIX="$RAW/$RUN_ID-$LABEL"
SUMMARY="$PREFIX-summary.txt"

echo "== run $RUN_ID ($LABEL) =="
echo "== project=$PROJECT management=$MANAGEMENT_URL jar=$JAR =="
echo "== push: delayMs=$PUSH_DELAY_MS failureRate=$PUSH_FAILURE_RATE invalidTokenRate=$PUSH_INVALID_TOKEN_RATE seed=$PUSH_SEED =="

# 다른 작업 폴더 환경이 떠 있는지 기록한다(README 10-2: 측정은 번갈아 한다). 막지는 않는다.
docker ps --format '{{.Names}}\t{{.Status}}' 2>/dev/null | tr -d '\r' > "$PREFIX-docker-ps.txt"
OTHER_CONTAINERS="$(cut -f1 "$PREFIX-docker-ps.txt" | grep -v "^$PROJECT-" || true)"
if [ -n "$OTHER_CONTAINERS" ]; then
	echo "경고: 이 프로젝트 밖 컨테이너가 떠 있다(측정이면 README 10-2대로 멈춘다):" >&2
	echo "$OTHER_CONTAINERS" | sed 's/^/  /' >&2
fi

APP_PID=""
HIKARI_PID=""
JVM_PID=""
stop_pid() {
	local pid="$1"
	if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
		kill "$pid" 2>/dev/null
		wait "$pid" 2>/dev/null
	fi
}
stop_app() {
	if [ -n "$APP_PID" ] && kill -0 "$APP_PID" 2>/dev/null; then
		kill "$APP_PID" 2>/dev/null
		local i
		for i in $(seq 1 30); do
			kill -0 "$APP_PID" 2>/dev/null || break
			sleep 1
		done
		if kill -0 "$APP_PID" 2>/dev/null; then
			kill -9 "$APP_PID" 2>/dev/null
		fi
		wait "$APP_PID" 2>/dev/null
	fi
	APP_PID=""
}
cleanup() {
	stop_pid "$HIKARI_PID"
	HIKARI_PID=""
	stop_pid "$JVM_PID"
	JVM_PID=""
	stop_app
}
trap 'cleanup' EXIT
trap 'echo "중단됨" >&2; exit 130' INT TERM

# ---------------------------------------------------------------------------
# 1) 토큰·알림 초기화
# ---------------------------------------------------------------------------
echo "== 1) 40_bulk_notification.sql =="
SEED_ARGS=()
if [ -n "${TOKEN_SEED:-}" ]; then
	SEED_ARGS=(-v "seed=$TOKEN_SEED")
fi
seed_start=$SECONDS
if ! "${COMPOSE[@]}" exec -T postgres psql -U tikitak -d tikitak "${SEED_ARGS[@]}" -f - \
	< loadtest/sql/40_bulk_notification.sql > "$PREFIX-seed.log" 2>&1; then
	echo "40_bulk_notification.sql 실패. $PREFIX-seed.log 를 본다." >&2
	exit 1
fi
SEED_SECONDS=$((SECONDS - seed_start))
echo "   ${SEED_SECONDS}초. 확인 쿼리 결과: $PREFIX-seed.log"

# ---------------------------------------------------------------------------
# 2) pg_stat_statements reset
# ---------------------------------------------------------------------------
bash loadtest/scripts/pgss.sh reset || { echo "pg_stat_statements 리셋 실패. 측정을 중단한다." >&2; exit 1; }

# ---------------------------------------------------------------------------
# 3) 샘플러 시작 (앱이 뜨기 전 샘플은 값이 빈 줄로 남는다)
# ---------------------------------------------------------------------------
bash loadtest/scripts/hikari-sampler.sh "$PREFIX-hikari.csv" &
HIKARI_PID=$!

# JVM 샘플러: JVM_INTERVAL초마다 process_cpu_usage(0~1, 앱 프로세스), 힙 사용량 합계(bytes), live 스레드 수.
# Prometheus(compose)도 1초마다 같은 값을 수집하므로 여기서는 간격을 넓게 둔다(샘플러 자체가 호스트 CPU를 쓴다).
echo "epoch_ms,time,process_cpu_usage,heap_used_bytes,live_threads" > "$PREFIX-jvm.csv"
if [ "$JVM_INTERVAL" != "0" ]; then
(
	trap 'exit 0' TERM INT
	while true; do
		t="${EPOCHREALTIME/[.,]/}"
		printf -v ts '%(%Y-%m-%dT%H:%M:%S)T' "${t:0:-6}"
		vals="$(curl -s --max-time 2 "$MANAGEMENT_URL/actuator/prometheus" 2>/dev/null | awk '
			/^process_cpu_usage[{ ]/                     { c = $NF }
			/^jvm_memory_used_bytes\{.*area="heap"/      { h += $NF; hs = 1 }
			/^jvm_threads_live_threads[{ ]/              { l = $NF }
			END { printf "%s,%s,%s", c, (hs ? sprintf("%.0f", h) : ""), l }')"
		echo "$((t / 1000)),$ts,$vals" >> "$PREFIX-jvm.csv"
		sleep "$JVM_INTERVAL" &
		wait $!
	done
) &
JVM_PID=$!
fi

# ---------------------------------------------------------------------------
# 4) 앱 기동
# ---------------------------------------------------------------------------
echo "== 4) 앱 기동 (dev,loadtest) =="
set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a
export SPRING_PROFILES_ACTIVE=dev,loadtest
export LOADTEST_DAILY_QUESTION_OPENED_RUN_ON_STARTUP=true
export DAILY_QUESTION_OPENED_NOTIFICATION_CRON=-
export LOADTEST_PUSH_DELAY_MS="$PUSH_DELAY_MS"
export LOADTEST_PUSH_FAILURE_RATE="$PUSH_FAILURE_RATE"
export LOADTEST_PUSH_INVALID_TOKEN_RATE="$PUSH_INVALID_TOKEN_RATE"
export LOADTEST_PUSH_SEED="$PUSH_SEED"
APP_LOG="$PREFIX-app.log"
APP_LAUNCHED_AT="$(date '+%Y-%m-%dT%H:%M:%S%z')"
nohup java -Duser.timezone=Asia/Seoul -jar "$JAR" > "$APP_LOG" 2>&1 &
APP_PID=$!
app_start=$SECONDS
echo "   pid=$APP_PID log=$APP_LOG"

# ---------------------------------------------------------------------------
# 5) 배치 종료 로그 대기
# ---------------------------------------------------------------------------
FINISHED_MARK="Load test daily question opened batch run finished."
SKIPPED_MARK="Load test daily question opened batch is not run on startup."
RESULT="ok"
echo "== 5) 배치 종료 로그를 기다린다 (최대 ${BATCH_TIMEOUT}초) =="
last_report=0
while true; do
	# Git Bash는 외부 명령 하나에 수십~수백 ms가 걸려 측정 호스트 CPU를 쓴다. 한 번에 grep 하나만 부른다.
	mark="$(grep -o -F -e "$FINISHED_MARK" -e "$SKIPPED_MARK" -e "APPLICATION FAILED TO START" "$APP_LOG" 2>/dev/null | head -n 1)"
	if [ "$mark" = "$FINISHED_MARK" ]; then
		break
	fi
	if [ "$mark" = "$SKIPPED_MARK" ]; then
		echo "Runner가 배치를 실행하지 않았다(run-on-startup=false로 떴다). env를 확인한다." >&2
		RESULT="skipped"
		break
	fi
	if [ -n "$mark" ] || ! kill -0 "$APP_PID" 2>/dev/null; then
		echo "앱이 기동 중에 멈췄다. $APP_LOG 를 본다." >&2
		RESULT="app-failed"
		break
	fi
	elapsed=$((SECONDS - app_start))
	if [ "$elapsed" -ge "$BATCH_TIMEOUT" ]; then
		echo "${BATCH_TIMEOUT}초 안에 배치가 끝나지 않았다." >&2
		RESULT="timeout"
		break
	fi
	if [ $((elapsed - last_report)) -ge 60 ]; then
		echo "   ${elapsed}초 경과"
		last_report=$elapsed
	fi
	sleep "$POLL_INTERVAL"
done
WAIT_SECONDS=$((SECONDS - app_start))
echo "   ${WAIT_SECONDS}초 ($RESULT)"

# ---------------------------------------------------------------------------
# 6) Prometheus 스냅샷
# ---------------------------------------------------------------------------
# 최대 3회 시도한다. 배치가 끝난 회차인데 notification_batch_ 지표가 끝내 없으면 산출물은 남기고 실패(종료 코드 1)로 바꾼다.
for attempt in 1 2 3; do
	curl -s --max-time 10 "$MANAGEMENT_URL/actuator/prometheus" > "$PREFIX-prometheus.txt" 2>/dev/null
	grep -q '^notification_batch_' "$PREFIX-prometheus.txt" 2>/dev/null && break
	[ "$attempt" -lt 3 ] && sleep 2
done
grep -E '^(notification_batch_|loadtest_push_results_|hikaricp_|process_cpu_usage|jvm_memory_used_bytes)' \
	"$PREFIX-prometheus.txt" > "$PREFIX-prometheus-batch.txt" 2>/dev/null || true
if [ "$RESULT" = "ok" ] && ! grep -q '^notification_batch_' "$PREFIX-prometheus-batch.txt"; then
	echo "Prometheus 스냅샷에 notification_batch_ 지표가 없다(3회 시도). 산출물은 남기고 실패로 끝낸다." >&2
	RESULT="snapshot-failed"
fi

# ---------------------------------------------------------------------------
# 7) 샘플러 종료  8) pg_stat_statements 덤프  9) 앱 종료
# ---------------------------------------------------------------------------
stop_pid "$HIKARI_PID"
HIKARI_PID=""
stop_pid "$JVM_PID"
JVM_PID=""
bash loadtest/scripts/pgss.sh dump "$PREFIX" || echo "pg_stat_statements 덤프 실패" >&2
stop_app
if curl -s --max-time 2 -o /dev/null "$MANAGEMENT_URL/actuator/health"; then
	echo "경고: 앱 종료 뒤에도 $MANAGEMENT_URL 이 응답한다. 프로세스를 직접 확인한다." >&2
fi

# ---------------------------------------------------------------------------
# 요약
# ---------------------------------------------------------------------------
# 예: hikaricp_connections_active{...} 3.0 / notification_batch_duration_seconds_sum{...} 1.23
prom_value() {
	awk -v pat="$1" '$0 ~ pat { s += $NF; f = 1 } END { if (f) printf "%s", s }' "$PREFIX-prometheus.txt" 2>/dev/null
}
started_line="$(grep -F "Daily question opened notification batch started." "$APP_LOG" | tail -n 1)"
completed_line="$(grep -F "Daily question opened notification batch completed." "$APP_LOG" | tail -n 1)"
app_started_line="$(grep -F "Started TikitakApplication" "$APP_LOG" | tail -n 1)"
log_field() {
	echo "$2" | sed -n "s/.*$1=\([0-9]*\).*/\1/p"
}
RECIPIENT_COUNT="$(log_field recipientCount "$completed_line")"
RECIPIENT_COUNT="${RECIPIENT_COUNT:-$(log_field recipientCount "$started_line")}"
SUCCESS_COUNT="$(log_field successCount "$completed_line")"
FAILURE_COUNT="$(log_field failureCount "$completed_line")"
ELAPSED_MS="$(log_field elapsedMs "$completed_line")"
PER_SEC=""
if [ -n "$RECIPIENT_COUNT" ] && [ -n "$ELAPSED_MS" ] && [ "$ELAPSED_MS" -gt 0 ]; then
	PER_SEC="$(awk -v n="$RECIPIENT_COUNT" -v ms="$ELAPSED_MS" 'BEGIN { printf "%.1f", n * 1000 / ms }')"
fi
HIKARI_MAX="$(awk -F, 'NR > 1 && $3 != "" { if ($3 > a) a = $3; if ($4 > p) p = $4; n++ }
	END { printf "samples=%d active_max=%s pending_max=%s", n, a + 0, p + 0 }' "$PREFIX-hikari.csv" 2>/dev/null)"
JVM_MAX="$(awk -F, 'NR > 1 && $3 != "" { if ($3 > c) c = $3; if ($4 > h) h = $4; if ($5 > t) t = $5; n++ }
	END { printf "samples=%d process_cpu_usage_max=%s heap_used_max_mb=%.0f live_threads_max=%s", n, c + 0, h / 1048576, t + 0 }' "$PREFIX-jvm.csv" 2>/dev/null)"
TOKEN_ROWS="$(sed -n '/== 건수/,/^(/p' "$PREFIX-seed.log" | sed -n '4p' | tr -s ' ')"
RECIPIENT_ROWS="$(sed -n '/== 배치가 받는 사람 수/,/^(/p' "$PREFIX-seed.log" | sed -n '4p' | tr -s ' ')"

{
	echo "run_id=$RUN_ID"
	echo "label=$LABEL"
	echo "result=$RESULT"
	echo "git_head=$(git rev-parse --short HEAD 2>/dev/null)$(git diff --quiet HEAD -- src 2>/dev/null || echo ' (src 미커밋 변경 있음)')"
	echo "jar=$JAR"
	echo "project=$PROJECT"
	echo "app_launched_at=$APP_LAUNCHED_AT"
	echo
	echo "[조건]"
	echo "push_delay_ms=$PUSH_DELAY_MS"
	echo "push_failure_rate=$PUSH_FAILURE_RATE"
	echo "push_invalid_token_rate=$PUSH_INVALID_TOKEN_RATE"
	echo "push_seed=$PUSH_SEED"
	echo "token_seed=${TOKEN_SEED:-(40번 SQL 기본값)}"
	echo "spring_profiles_active=$SPRING_PROFILES_ACTIVE"
	echo "hikari_maximum_pool_size=${SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE:-(기본)}"
	echo "poll_interval=$POLL_INTERVAL jvm_interval=$JVM_INTERVAL"
	echo "other_containers=$(echo "$OTHER_CONTAINERS" | tr '\n' ' ')"
	echo
	echo "[시드(40번)]"
	echo "seed_seconds=$SEED_SECONDS"
	echo "counts(members|active_members|tokens|notifications)=$TOKEN_ROWS"
	echo "batch_recipients(recipients|recipients_with_token|recipient_tokens)=$RECIPIENT_ROWS"
	echo
	echo "[배치 로그]"
	echo "app_started=${app_started_line##*: }"
	echo "recipientCount=$RECIPIENT_COUNT"
	echo "successCount=$SUCCESS_COUNT"
	echo "failureCount=$FAILURE_COUNT"
	echo "elapsedMs=$ELAPSED_MS"
	echo "recipients_per_sec=$PER_SEC"
	echo "wait_seconds_from_launch=$WAIT_SECONDS"
	echo "started_log=$started_line"
	echo "completed_log=$completed_line"
	echo
	echo "[Prometheus 스냅샷(배치 종료 직후)]"
	echo "batch_duration_seconds_sum=$(prom_value '^notification_batch_duration_seconds_sum')"
	echo "recipient_query_duration_seconds_sum=$(prom_value '^notification_batch_recipient_query_duration_seconds_sum')"
	echo "send_duration_seconds_sum=$(prom_value '^notification_batch_send_duration_seconds_sum')"
	echo "batch_sends_success=$(prom_value '^notification_batch_sends_total.*result="success"')"
	echo "batch_sends_failure=$(prom_value '^notification_batch_sends_total.*result="failure"')"
	echo "push_results_success=$(prom_value '^loadtest_push_results_total.*status="SUCCESS"')"
	echo "push_results_failed=$(prom_value '^loadtest_push_results_total.*status="FAILED"')"
	echo "push_results_invalid_token=$(prom_value '^loadtest_push_results_total.*status="INVALID_TOKEN"')"
	echo
	echo "[샘플러]"
	echo "hikari: $HIKARI_MAX"
	echo "jvm: $JVM_MAX"
} > "$SUMMARY"

echo "== summary =="
cat "$SUMMARY"
echo "== files =="
ls -1 "$RAW" | grep "^$RUN_ID-" | sed "s|^|$RAW/|"

if [ "$RESULT" != "ok" ]; then
	exit 1
fi
exit 0
