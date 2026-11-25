#!/usr/bin/env bash
# 외부 부하 샘플러 — #217
#
# 측정 중에 이 측정과 무관한 부하(다른 작업 폴더의 gradle·앱·시드, 다른 compose 프로젝트 컨테이너, 백신 검사 등)가
# 있었는지 기록하고, 기준을 넘으면 "CONTAMINATION: POSSIBLE"을 낸다. run-measure.sh가 측정 구간(웜업 포함)에 돌린다.
#
# 두 가지를 따로 잰다
#   host   : external-load-sampler.ps1(PowerShell 1개를 계속 띄움)이 LOAD_HOST_INTERVAL_MS(기본 2000)마다 프로세스별 CPU를 읽는다.
#            측정 앱(SERVER_PORT를 listen하는 PID), k6, docker CLI, Docker VM 프로세스(vmmem*, com.docker.*), 샘플러 자신을 뺀
#            나머지 합계와 상위 프로세스. 단위는 논리 CPU 개수(1.00 = 1개를 다 씀).
#   docker : `docker stats`(스트림 1개를 계속 띄움, 약 1초마다 갱신)로 LOADTEST_PROJECT가 아닌 모든 실행 중 컨테이너의 CPU% 합계.
#            100% = Docker VM의 CPU 1개. 이 프로젝트 컨테이너는 compose 라벨로 골라 뺀다.
# 프로세스를 매 샘플마다 새로 띄우지 않는다(Git Bash·PowerShell은 띄울 때 비싸다). 샘플러 자신의 CPU는 요약에 적는다.
#
# 사용
#   bash loadtest/scripts/external-load-sampler.sh start <접두사>     # 백그라운드로 시작하고 바로 돌아온다
#   bash loadtest/scripts/external-load-sampler.sh stop <접두사>      # 멈추고 요약
#   bash loadtest/scripts/external-load-sampler.sh summary <접두사>   # 요약만 다시
#   bash loadtest/scripts/external-load-sampler.sh run <접두사> [초]  # 단독 실행(초를 안 주면 Ctrl+C까지). 확인용
# 파일: <접두사>-load-host.csv, -load-docker.csv, -load-meta.txt, -contamination.txt(요약)
#
# env
#   LOADTEST_PROJECT  이 측정의 compose 프로젝트(없으면 loadtest/.env, 그다음 tikitak-loadtest)
#   SERVER_PORT       측정 앱 API 포트(없으면 loadtest/.env, 그다음 8080). 이 포트를 listen하는 프로세스를 뺀다
#   LOAD_HOST_INTERVAL_MS   호스트 샘플 간격(기본 2000)
#   기준값(README 10-5에 근거)
#   LOAD_HOST_AVG_CPU    외부 프로세스 평균이 이 값 이상이면 오염 가능(기본 2.5 = 평소 데스크톱 약 1.5 + 논리 CPU 1개)
#   LOAD_HOST_PEAK_CPU   이 값 이상인 샘플을 "기준을 넘은 구간"으로 센다(기본 4.0 = 논리 CPU 8개의 절반)
#   LOAD_DOCKER_AVG_PCT  다른 컨테이너 CPU% 합계 평균이 이 값 이상이면 오염 가능(기본 25)
#   LOAD_DOCKER_PEAK_PCT 이 값 이상인 docker 샘플을 "기준을 넘은 구간"으로 센다(기본 100)
#   LOAD_PEAK_RATIO      기준을 넘은 구간이 전체 샘플의 이 비율 이상이면 오염 가능(기본 0.05)
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SCRIPT_DIR="$ROOT/loadtest/scripts"
CMD="${1:-}"
PREFIX="${2:-}"
if [ -z "$CMD" ] || [ -z "$PREFIX" ]; then
	echo "usage: $0 start|stop|summary|run <접두사> [초]" >&2
	exit 2
fi
HOST_CSV="$PREFIX-load-host.csv"
DOCKER_CSV="$PREFIX-load-docker.csv"
META="$PREFIX-load-meta.txt"
SUMMARY="$PREFIX-contamination.txt"
STATE="$PREFIX-load.state"
STOP_FILE="$PREFIX-load.stop"

env_file_value() {
	sed -n "s/^$1=//p" "$ROOT/loadtest/.env" 2> /dev/null | tr -d '\r' | tail -n 1
}
PROJECT="${LOADTEST_PROJECT:-$(env_file_value LOADTEST_PROJECT)}"
PROJECT="${PROJECT:-tikitak-loadtest}"
APP_PORT="${SERVER_PORT:-$(env_file_value SERVER_PORT)}"
APP_PORT="${APP_PORT:-8080}"

state_value() {
	sed -n "s/^$1=//p" "$STATE" 2> /dev/null | tail -n 1
}

ts() {
	printf '%(%Y-%m-%dT%H:%M:%S)T' -1
}

# 이 프로젝트가 아닌 실행 중 컨테이너 목록(이름, 프로젝트, 상태)
other_containers() {
	docker ps --format '{{.Names}}|{{.Label "com.docker.compose.project"}}|{{.Status}}' 2> /dev/null |
		awk -F'|' -v p="$PROJECT" '$2 != p { printf "%s%s(%s, %s)", (n++ ? "; " : ""), $1, ($2 == "" ? "-" : $2), $3 } END { if (!n) printf "없음" }'
}

start() {
	mkdir -p "$(dirname "$PREFIX")"
	rm -f "$STOP_FILE" "$STATE" "$STATE.docker" "$HOST_CSV" "$DOCKER_CSV" "$SUMMARY" "$META.err"
	: > "$META"
	local own parent_msys parent_win
	own="$(docker ps --filter "label=com.docker.compose.project=$PROJECT" --format '{{.Names}}' 2> /dev/null | paste -sd, -)"
	parent_msys="${LOAD_SAMPLER_PARENT:-$PPID}"
	parent_win="$(cat "/proc/$parent_msys/winpid" 2> /dev/null || echo 0)"
	{
		echo "start=$(ts)"
		echo "project=$PROJECT"
		echo "app_port=$APP_PORT"
		echo "own_containers=${own:-없음}"
		echo "other_containers_start=$(other_containers)"
	} >> "$META"

	# docker: 스트림 하나. 화면 제어 문자(ESC[H 등)로 프레임이 나뉜다. 같은 값이 두 번씩 나오므로 바로 앞 프레임과 같으면 버린다.
	echo "epoch_s,time,other_cpu_pct,other_count,top_other" > "$DOCKER_CSV"
	(
		docker stats --format '{{.Name}},{{.CPUPerc}}' 2> /dev/null &
		echo "$!" > "$STATE.docker"
		wait
	) | awk -v own="$own" -v out="$DOCKER_CSV" '
		BEGIN { n = split(own, a, ","); for (i = 1; i <= n; i++) mine[a[i]] = 1 }
		function flush(   key, i, j, t, top, sum, cnt) {
			if (fn == 0) return
			key = ""
			for (i = 1; i <= fn; i++) key = key fname[i] "=" fcpu[i] ";"
			if (key == last) { fn = 0; return }
			last = key; sum = 0; cnt = 0; top = ""
			# CPU 큰 순서로 상위 3개
			for (i = 1; i <= fn; i++) if (!(fname[i] in mine)) { sum += fcpu[i]; cnt++; ord[cnt] = i }
			for (i = 1; i <= cnt; i++) for (j = i + 1; j <= cnt; j++) if (fcpu[ord[j]] > fcpu[ord[i]]) { t = ord[i]; ord[i] = ord[j]; ord[j] = t }
			for (i = 1; i <= cnt && i <= 3; i++) top = top (i > 1 ? ";" : "") fname[ord[i]] "=" fcpu[ord[i]]
			printf "%d,%s,%.2f,%d,%s\n", systime(), strftime("%Y-%m-%dT%H:%M:%S"), sum, cnt, top >> out
			fflush(out)
			fn = 0
		}
		{
			line = $0
			if (index(line, "\033[H")) flush()
			gsub(/\033\[[0-9;]*[A-Za-z]/, "", line)
			gsub(/\r/, "", line)
			if (split(line, f, ",") == 2 && f[2] ~ /%$/) {
				sub(/%$/, "", f[2]); fn++; fname[fn] = f[1]; fcpu[fn] = f[2] + 0
			}
		}' &
	echo "docker_awk=$!" >> "$STATE"

	powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "$(cygpath -w "$SCRIPT_DIR/external-load-sampler.ps1")" \
		-Out "$(cygpath -w "$HOST_CSV")" -Meta "$(cygpath -w "$META")" -StopFile "$(cygpath -w "$STOP_FILE")" \
		-IntervalMs "${LOAD_HOST_INTERVAL_MS:-2000}" -AppPort "$APP_PORT" -ParentPid "$parent_win" > /dev/null 2>> "$META.err" &
	echo "ps=$!" >> "$STATE"

	# 부른 쪽이 비정상으로 죽으면 docker stats 스트림도 멈춘다(PowerShell은 ParentPid로 스스로 멈춘다)
	(
		while [ ! -f "$STOP_FILE" ] && kill -0 "$parent_msys" 2> /dev/null; do
			sleep 2
		done
		kill "$(cat "$STATE.docker" 2> /dev/null)" 2> /dev/null
	) > /dev/null 2>&1 &
	echo "watchdog=$!" >> "$STATE"

	# PowerShell 기동과 카운터 초기화(합계 10초 안팎, CPU 약 4초)가 측정 구간에 들어가지 않도록, 헤더가 생길 때까지 기다린다
	local ps_pid waited=0
	ps_pid="$(state_value ps)"
	while [ ! -s "$HOST_CSV" ] && [ "$waited" -lt "${LOAD_SAMPLER_READY_TIMEOUT:-60}" ]; do
		kill -0 "$ps_pid" 2> /dev/null || break
		sleep 1
		waited=$((waited + 1))
	done
	if [ ! -s "$HOST_CSV" ]; then
		echo "경고: 호스트 샘플러가 ${waited}초 안에 준비되지 않았다($META.err 확인). docker 쪽만 기록될 수 있다." >&2
	fi
	echo "== 외부 부하 샘플러 시작(준비 ${waited}초): 프로젝트 $PROJECT 제외, 앱 포트 $APP_PORT 제외 ($PREFIX-load-*.csv) ==" >&2
}

stop() {
	local ps_pid awk_pid docker_pid i
	if [ ! -f "$STATE" ]; then
		echo "샘플러 상태 파일이 없다: $STATE" >&2
		return 1
	fi
	touch "$STOP_FILE"
	ps_pid="$(state_value ps)"
	awk_pid="$(state_value docker_awk)"
	docker_pid="$(cat "$STATE.docker" 2> /dev/null)"
	[ -n "$docker_pid" ] && kill "$docker_pid" 2> /dev/null
	for i in $(seq 1 20); do
		kill -0 "$ps_pid" 2> /dev/null || break
		sleep 0.5
	done
	if kill -0 "$ps_pid" 2> /dev/null; then
		kill "$ps_pid" 2> /dev/null
		echo "stopped_by=kill" >> "$META"
	fi
	for i in $(seq 1 10); do
		kill -0 "$awk_pid" 2> /dev/null || break
		sleep 0.5
	done
	kill "$(state_value watchdog)" 2> /dev/null
	{
		echo "end=$(ts)"
		echo "other_containers_end=$(other_containers)"
	} >> "$META"
	rm -f "$STOP_FILE" "$STATE" "$STATE.docker"
	[ -s "$META.err" ] || rm -f "$META.err"
	summary
}

meta_value() {
	sed -n "s/^$1=//p" "$META" 2> /dev/null | tail -n 1
}

summary() {
	local host_avg="${LOAD_HOST_AVG_CPU:-2.5}" host_peak="${LOAD_HOST_PEAK_CPU:-4.0}"
	local docker_avg="${LOAD_DOCKER_AVG_PCT:-25}" docker_peak="${LOAD_DOCKER_PEAK_PCT:-100}"
	local ratio="${LOAD_PEAK_RATIO:-0.05}"
	local host_line docker_line host_top docker_top reasons verdict
	# host: 평균·최대·기준을 넘은 샘플 수, 앱 평균, 상위 프로세스(각 샘플 상위 5개에 든 값만 더한 근사)
	host_line="$(awk -F, -v pk="$host_peak" 'NR > 1 && $5 != "" { n++; s += $5; if ($5 > m) m = $5; if ($5 >= pk) c++; a += $6 }
		END { if (n) printf "%d %.2f %.2f %d %.2f", n, s / n, m, c, a / n; else print "0 0 0 0 0" }' "$HOST_CSV" 2> /dev/null)"
	# 같은 이름(예: bash 여러 개)은 샘플 안에서 먼저 더한 뒤 평균·최대를 낸다
	host_top="$(awk -F, 'NR > 1 && $5 != "" { n++; split("", cur); k = split($9, t, ";"); for (i = 1; i <= k; i++) { split(t[i], kv, "="); nm = kv[1]; sub(/\([0-9]+\)$/, "", nm); cur[nm] += kv[2] }
		for (x in cur) { s[x] += cur[x]; if (cur[x] > mx[x]) mx[x] = cur[x] } }
		END { for (x in s) printf "%.3f %s 평균=%.2f 최대=%.2f\n", s[x] / n, x, s[x] / n, mx[x] }' "$HOST_CSV" 2> /dev/null |
		sort -rn | head -n 5 | cut -d' ' -f2-)"
	docker_line="$(awk -F, -v pk="$docker_peak" 'NR > 1 && $3 != "" { n++; s += $3; if ($3 > m) m = $3; if ($3 >= pk) c++ }
		END { if (n) printf "%d %.1f %.1f %d", n, s / n, m, c; else print "0 0 0 0" }' "$DOCKER_CSV" 2> /dev/null)"
	docker_top="$(awk -F, 'NR > 1 { n++; k = split($5, t, ";"); for (i = 1; i <= k; i++) { split(t[i], kv, "="); s[kv[1]] += kv[2]; if (kv[2] > mx[kv[1]]) mx[kv[1]] = kv[2] } }
		END { for (x in s) if (x != "") printf "%.3f %s 평균=%.1f%% 최대=%.1f%%\n", s[x] / n, x, s[x] / n, mx[x] }' "$DOCKER_CSV" 2> /dev/null |
		sort -rn | head -n 3 | cut -d' ' -f2-)"
	read -r hn havg hmax hcnt happ <<< "${host_line:-0 0 0 0 0}"
	read -r dn davg dmax dcnt <<< "${docker_line:-0 0 0 0}"

	reasons=()
	if [ "$hn" -eq 0 ] || [ "$dn" -eq 0 ]; then
		verdict="UNKNOWN"
		[ "$hn" -eq 0 ] && reasons+=("호스트 샘플 없음($HOST_CSV)")
		[ "$dn" -eq 0 ] && reasons+=("docker 샘플 없음($DOCKER_CSV)")
	fi
	ge() { awk -v a="$1" -v b="$2" 'BEGIN { exit !(a + 0 >= b + 0) }'; }
	[ "$hn" -gt 0 ] && ge "$havg" "$host_avg" && reasons+=("외부 프로세스 평균 $havg CPU >= $host_avg")
	[ "$hn" -gt 0 ] && ge "$(awk -v c="$hcnt" -v n="$hn" 'BEGIN { print c / n }')" "$ratio" &&
		reasons+=("외부 프로세스 $host_peak CPU 이상 샘플 $hcnt/$hn >= 비율 $ratio")
	[ "$dn" -gt 0 ] && ge "$davg" "$docker_avg" && reasons+=("다른 컨테이너 평균 $davg% >= $docker_avg%")
	[ "$dn" -gt 0 ] && ge "$(awk -v c="$dcnt" -v n="$dn" 'BEGIN { print c / n }')" "$ratio" &&
		reasons+=("다른 컨테이너 $docker_peak% 이상 샘플 $dcnt/$dn >= 비율 $ratio")
	if [ "${verdict:-}" != "UNKNOWN" ]; then
		if [ "${#reasons[@]}" -gt 0 ]; then verdict="POSSIBLE"; else verdict="NONE"; fi
	fi

	{
		echo "== 외부 부하 요약 ($PREFIX) =="
		echo "구간: $(meta_value start) ~ $(meta_value end)"
		echo "이 측정: 프로젝트 $(meta_value project), 앱 포트 $(meta_value app_port)(PID 시작 $(meta_value app_pid_start), 끝 $(meta_value app_pid_end)), 논리 CPU $(meta_value ncpu)개"
		echo "호스트 여유 메모리: 시작 $(meta_value mem_start), 끝 $(meta_value mem_end)"
		echo "다른 컨테이너(시작): $(meta_value other_containers_start)"
		echo "다른 컨테이너(끝): $(meta_value other_containers_end)"
		echo
		echo "[호스트 외부 프로세스] 샘플 ${hn}개(간격 ${LOAD_HOST_INTERVAL_MS:-2000}ms), 단위 논리 CPU"
		echo "  평균 $havg, 최대 $hmax, $host_peak 이상 샘플 $hcnt개 (기준: 평균 >= $host_avg 또는 $host_peak 이상 샘플 비율 >= $ratio)"
		echo "  측정 앱 평균 $happ"
		echo "  주요 프로세스(각 샘플 상위 5개 기준 근사):"
		if [ -n "$host_top" ]; then printf '%s\n' "$host_top" | sed 's/^/    /'; else echo "    없음"; fi
		echo "[다른 컨테이너] 샘플 ${dn}개(약 1초), 단위 % (100 = CPU 1개)"
		echo "  합계 평균 $davg%, 최대 $dmax%, $docker_peak% 이상 샘플 $dcnt개 (기준: 평균 >= $docker_avg% 또는 $docker_peak% 이상 샘플 비율 >= $ratio)"
		echo "  주요 컨테이너:"
		if [ -n "$docker_top" ]; then printf '%s\n' "$docker_top" | sed 's/^/    /'; else echo "    없음"; fi
		echo "샘플러 자신: PowerShell CPU $(meta_value sampler_cpu_seconds)초 / $(meta_value sampler_wall_seconds)초 (시작 시 카운터 초기화 포함)"
		[ -n "$(meta_value stopped_by)" ] && echo "샘플러 종료 사유: $(meta_value stopped_by)"
		echo
		if [ "$verdict" = "NONE" ]; then
			echo "CONTAMINATION: NONE"
		else
			echo "################################################################"
			echo "CONTAMINATION: $verdict"
			printf '  - %s\n' "${reasons[@]}"
			echo "  이 측정만 다시 한다(README 10-5)."
			echo "################################################################"
		fi
	} | tee "$SUMMARY"
}

case "$CMD" in
	start) start ;;
	stop) stop ;;
	summary) summary ;;
	run)
		LOAD_SAMPLER_PARENT=$$ start
		trap 'stop; exit 130' INT TERM
		if [ -n "${3:-}" ]; then
			sleep "$3" &
		else
			sleep 2147483647 &
		fi
		wait $!
		trap - INT TERM
		stop
		;;
	*)
		echo "usage: $0 start|stop|summary|run <접두사> [초]" >&2
		exit 2
		;;
esac
