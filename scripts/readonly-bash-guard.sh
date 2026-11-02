#!/usr/bin/env bash
# researcher / verifier 워커 전용 PreToolUse(Bash) hook 진입점.
# 실제 검사는 readonly_bash_guard.py가 한다. 여기서는 동작하는 Python만 찾는다.
# (Windows의 python3는 스토어 바로가기라 실행이 안 되는 경우가 있어서 실제로 돌려보고 고른다.)
# Python을 못 찾으면 명령을 막는다 (fail-closed).
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
for py in python3 python; do
  if "$py" -c "import sys; sys.exit(0 if sys.version_info >= (3, 10) else 1)" >/dev/null 2>&1; then
    exec "$py" "$DIR/readonly_bash_guard.py" "$@"
  fi
done
echo "[readonly-bash-guard] 차단됨: Python 3.10+을 찾지 못해 명령을 검사할 수 없다." >&2
exit 2
