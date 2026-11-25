#!/usr/bin/env bash
# PC 공용 측정 잠금 — #217
#
# 같은 PC의 여러 작업 폴더(worktree)가 시드와 측정을 동시에 돌리면 서로의 CPU·디스크를 나눠 써서 결과가 섞인다.
# 이 래퍼는 PC 전체에서 한 번에 하나의 명령만 돌게 한다. 코드 작업과 gradle은 막지 않는다(README 10-4).
#
# 사용
#   bash loadtest/scripts/measure-lock.sh <명령...>
#   bash loadtest/scripts/measure-lock.sh bash -c '...개선 전 측정...; ...개선 후 측정...'   # 잠금 한 번 안에서 연달아
#   bash loadtest/scripts/measure-lock.sh --status                                          # 누가 잡고 있는지만 본다
#
# 동작
#   - 잠금은 디렉터리 하나다. 기본 $HOME/.tikitak-loadtest-lock (LOADTEST_LOCK_DIR로 바꾼다). 모든 작업 폴더가 같은 경로를 봐야
#     하므로 저장소 밖에 둔다. mkdir로 원자적으로 잡고, 안에 owner 파일(작업 폴더, LOADTEST_PROJECT, PID, Windows PID,
#     시작 시각, 명령)을 둔다.
#   - 이미 잡혀 있으면 LOADTEST_LOCK_POLL초(기본 15)마다 다시 시도하고, 기다리는 동안 누가 잡고 있는지 출력한다.
#     LOADTEST_LOCK_TIMEOUT초(기본 7200 = 2시간, 0이면 무제한)를 넘으면 종료 코드 75로 끝난다.
#   - 명령은 이 스크립트의 자식으로 돌고, 명령이 끝나야 잠금을 푼다. 정상 종료·에러·INT·TERM·HUP 모두 EXIT trap에서 푼다.
#     INT·TERM을 받아도 자식 명령이 끝날 때까지는 잠금을 쥐고 있다(명령이 아직 돌고 있으면 측정 중이기 때문이다).
#   - 중첩: 잠금을 잡은 뒤 자식에게 LOADTEST_LOCK_HELD=<토큰>과 LOADTEST_LOCK_DIR을 넘긴다. 자식이 이 스크립트를 다시 부르면
#     토큰이 owner 파일과 같은지 보고, 같으면 다시 잡지 않고 바로 실행한다. 토큰이 다르면(env가 새어 나온 경우) 경고하고 새로 잡는다.
#   - 남은 잠금: owner의 Windows PID(Git Bash의 PID가 아니라 /proc/<pid>/winpid)를 tasklist로 확인해, 그 프로세스가 없다고
#     확실히 나올 때만 지운다. tasklist가 실패하거나 owner 파일이 없거나 형식을 모르면 지우지 않고 사람이 지우는 방법을 출력한다.
#     지우는 일은 "<잠금>.reap" 디렉터리를 mkdir로 잡은 한 프로세스만 하고, 잡은 뒤 owner를 다시 확인한다.
#
# env: LOADTEST_LOCK_DIR, LOADTEST_LOCK_POLL, LOADTEST_LOCK_TIMEOUT, LOADTEST_PROJECT(owner 기록용. 없으면 loadtest/.env 값)
# 종료 코드: 명령의 종료 코드. 사용법 오류 2, 대기 시간 초과 75, INT 130, TERM 143, HUP 129.
set -uo pipefail

LOCK_DIR="${LOADTEST_LOCK_DIR:-$HOME/.tikitak-loadtest-lock}"
LOCK_DIR="${LOCK_DIR%/}"
POLL="${LOADTEST_LOCK_POLL:-15}"
TIMEOUT="${LOADTEST_LOCK_TIMEOUT:-7200}"
OWNER="$LOCK_DIR/owner"
REAP="$LOCK_DIR.reap"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

log() {
	printf '[measure-lock %(%H:%M:%S)T] %s\n' -1 "$*" >&2
}

if [ $# -lt 1 ]; then
	echo "usage: $0 <명령...> | --status" >&2
	exit 2
fi

# owner 파일에서 key=값 하나를 읽는다(없으면 빈 값)
owner_field() {
	sed -n "s/^$1=//p" "$OWNER" 2>/dev/null | head -n 1
}

# owner 프로세스가 살아 있는지: alive | dead | unknown. dead는 "그 프로세스가 없다"가 확실할 때만 낸다.
owner_alive() {
	local winpid pid host out rc
	winpid="$(owner_field winpid)"
	pid="$(owner_field pid)"
	host="$(owner_field host)"
	if [ -n "$host" ] && [ "$host" != "$(hostname)" ]; then
		echo unknown
		return
	fi
	if [ -n "$winpid" ]; then
		# Git Bash의 PID는 Git Bash 안에서만 통한다. Windows PID를 Windows 도구로 본다.
		case "$winpid" in *[!0-9]*) echo unknown; return ;; esac
		command -v tasklist.exe > /dev/null 2>&1 || { echo unknown; return; }
		out="$(MSYS_NO_PATHCONV=1 tasklist.exe /FI "PID eq $winpid" /NH /FO CSV 2> /dev/null)"
		rc=$?
		if [ "$rc" -ne 0 ] || [ -z "$out" ]; then
			echo unknown
		elif [[ "$out" == *"\"$winpid\""* ]]; then
			echo alive
		elif [[ "$out" != *'"'* ]]; then
			# 맞는 프로세스가 없으면 CSV 줄 없이 안내문(INFO: ...)만 나온다
			echo dead
		else
			echo unknown
		fi
		return
	fi
	if [ -n "$pid" ]; then
		case "$pid" in *[!0-9]*) echo unknown; return ;; esac
		if kill -0 "$pid" 2> /dev/null; then
			echo alive
		elif ps -p "$pid" > /dev/null 2>&1; then
			echo alive
		else
			echo dead
		fi
		return
	fi
	echo unknown
}

describe_owner() {
	if [ -f "$OWNER" ]; then
		printf '%s(%s) pid=%s winpid=%s 시작=%s 명령=%s' \
			"$(owner_field worktree)" "$(owner_field project)" "$(owner_field pid)" "$(owner_field winpid)" \
			"$(owner_field started)" "$(owner_field command)"
	else
		printf 'owner 파일 없음(%s)' "$OWNER"
	fi
}

manual_hint() {
	log "남은 잠금으로 보이면 사람이 확인하고 지운다:"
	log "  1) cat \"$OWNER\"   # winpid 확인"
	log "  2) tasklist //FI \"PID eq <winpid>\"   # 프로세스가 없고, 그 작업 폴더에서 측정·시드가 돌고 있지 않으면"
	log "  3) rm -rf \"$LOCK_DIR\" \"$REAP\""
}

if [ "$1" = "--status" ]; then
	if [ -d "$LOCK_DIR" ]; then
		echo "잠금: $LOCK_DIR"
		echo "잡은 쪽: $(describe_owner)"
		echo "owner 프로세스: $(owner_alive)"
	else
		echo "잠금 없음: $LOCK_DIR"
	fi
	[ -d "$REAP" ] && echo "정리 잠금이 남아 있다: $REAP"
	exit 0
fi

# 중첩 호출: 부모가 잡은 잠금의 토큰이 owner와 같으면 다시 잡지 않는다
if [ -n "${LOADTEST_LOCK_HELD:-}" ]; then
	if [ "$(owner_field token)" = "$LOADTEST_LOCK_HELD" ]; then
		log "이미 잡은 잠금 안이다(중첩). 다시 잡지 않고 실행한다: $*"
		exec "$@"
	fi
	log "경고: LOADTEST_LOCK_HELD가 있지만 $LOCK_DIR 의 owner와 맞지 않는다. 새로 잡는다"
	unset LOADTEST_LOCK_HELD
fi

ACQUIRED=0
REAPING=0
TOKEN=""
cleanup() {
	if [ "$REAPING" = 1 ]; then
		rmdir "$REAP" 2> /dev/null
		REAPING=0
	fi
	if [ "$ACQUIRED" = 1 ]; then
		if [ "$(owner_field token)" = "$TOKEN" ]; then
			rm -rf "$LOCK_DIR"
			log "잠금 해제: $LOCK_DIR"
		else
			log "경고: $LOCK_DIR 의 owner가 이 프로세스가 아니다. 지우지 않는다"
		fi
		ACQUIRED=0
	fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
trap 'exit 129' HUP

# owner가 죽은 것이 확실한 잠금만 지운다. 지웠으면 0
reap_stale() {
	local tok moved
	[ -f "$OWNER" ] || return 1
	[ "$(owner_alive)" = dead ] || return 1
	mkdir "$REAP" 2> /dev/null || return 1
	REAPING=1
	tok="$(owner_field token)"
	# 정리 잠금을 잡은 뒤 다시 확인한다(그사이 다른 정리 프로세스가 지우고 새 owner가 잡았을 수 있다)
	if [ -n "$tok" ] && [ "$(owner_alive)" = dead ]; then
		log "남은 잠금을 지운다. owner 프로세스가 없다: $(describe_owner)"
		moved="$LOCK_DIR.stale.$$"
		if mv "$LOCK_DIR" "$moved" 2> /dev/null; then
			if [ "$(sed -n 's/^token=//p' "$moved/owner" 2> /dev/null | head -n 1)" = "$tok" ]; then
				rm -rf "$moved"
			else
				# 확인과 이동 사이에 잠금이 바뀌었다(사람이 지우고 다른 쪽이 잡은 경우). 되돌린다
				mv "$moved" "$LOCK_DIR" 2> /dev/null || log "경고: 이동한 잠금을 되돌리지 못했다: $moved"
			fi
		fi
	fi
	rmdir "$REAP" 2> /dev/null
	REAPING=0
	return 0
}

mkdir -p "$(dirname "$LOCK_DIR")"
start=$SECONDS
last_token="-"
no_owner_since=""
while ! mkdir "$LOCK_DIR" 2> /dev/null; do
	if [ ! -d "$LOCK_DIR" ]; then
		# 방금 풀렸으면 바로 다시 시도한다. 디렉터리가 없는데도 mkdir이 실패하면 경로 문제다
		mkdir "$LOCK_DIR" 2> /dev/null && break
		[ -d "$LOCK_DIR" ] && continue
		log "잠금 디렉터리를 만들 수 없다: $LOCK_DIR"
		exit 2
	fi
	if reap_stale; then
		continue
	fi
	waited=$((SECONDS - start))
	tok="$(owner_field token)"
	if [ "$tok" != "$last_token" ]; then
		log "다른 쪽이 잠금을 잡고 있다. ${POLL}초마다 다시 시도한다(시간 제한 ${TIMEOUT}초, 0 = 무제한)"
		log "  잡은 쪽: $(describe_owner)"
		last_token="$tok"
	else
		log "대기 ${waited}초: $(owner_field worktree)($(owner_field project)) $(owner_field command)"
	fi
	if [ ! -f "$OWNER" ]; then
		[ -z "$no_owner_since" ] && no_owner_since=$SECONDS
		if [ $((SECONDS - no_owner_since)) -ge 60 ]; then
			log "잠금 디렉터리에 owner 파일이 60초 넘게 없다. 자동으로 지우지 않는다"
			manual_hint
			no_owner_since=$SECONDS
		fi
	else
		no_owner_since=""
		[ "$(owner_alive)" = unknown ] && [ "$waited" -ge 60 ] && [ $((waited % 300)) -lt "$POLL" ] && manual_hint
	fi
	if [ -d "$REAP" ] && [ $(($(date +%s) - $(stat -c %Y "$REAP" 2> /dev/null || date +%s))) -ge 120 ]; then
		log "정리 잠금($REAP)이 120초 넘게 남아 있다. 남은 잠금을 자동으로 지우지 못한다"
		manual_hint
	fi
	if [ "$TIMEOUT" -gt 0 ] && [ "$waited" -ge "$TIMEOUT" ]; then
		log "${TIMEOUT}초를 기다렸지만 잠금을 얻지 못했다. 끝낸다. 잡은 쪽: $(describe_owner)"
		exit 75
	fi
	# 백그라운드 sleep + wait: 기다리는 중에 INT·TERM을 받으면 바로 끝난다
	sleep "$POLL" &
	wait $! 2> /dev/null
done
ACQUIRED=1

if [ -r "/proc/$$/winpid" ]; then
	WINPID="$(cat "/proc/$$/winpid")"
else
	WINPID=""
fi
TOKEN="$(hostname)-${WINPID:-$$}-${EPOCHREALTIME/[.,]/}-$RANDOM"
PROJECT="${LOADTEST_PROJECT:-$(sed -n 's/^LOADTEST_PROJECT=//p' "$ROOT/loadtest/.env" 2> /dev/null | tr -d '\r' | tail -n 1)}"
CMD="$*"
CMD="${CMD//$'\n'/ }"
CMD="${CMD//$'\r'/}"
{
	echo "token=$TOKEN"
	echo "worktree=$ROOT"
	echo "project=${PROJECT:-tikitak-loadtest}"
	echo "pid=$$"
	echo "winpid=$WINPID"
	echo "host=$(hostname)"
	printf 'started=%(%Y-%m-%dT%H:%M:%S%z)T\n' -1
	echo "started_epoch=$(date +%s)"
	echo "command=$CMD"
} > "$LOCK_DIR/owner.tmp" && mv "$LOCK_DIR/owner.tmp" "$OWNER"
log "잠금 획득: $LOCK_DIR (대기 $((SECONDS - start))초) 명령: $CMD"

export LOADTEST_LOCK_HELD="$TOKEN"
export LOADTEST_LOCK_DIR="$LOCK_DIR"
"$@"
STATUS=$?
exit "$STATUS"
