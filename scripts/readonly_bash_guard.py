"""읽기 전용 워커(researcher, verifier)의 Bash 명령을 허용 목록으로 검사하는 PreToolUse hook.

Claude Code가 Bash 실행 직전에 hook 입력(JSON)을 stdin으로 넘긴다.
- exit 0: 허용
- exit 2: 차단 (stderr 메시지가 워커에게 전달된다)

금지 목록이 아니라 허용 목록 방식이다. 목록에 없는 명령은 전부 막힌다.
판단이 애매하거나 입력을 해석하지 못하면 막는다 (fail-closed).

사용법: python readonly_bash_guard.py <researcher|verifier>
"""

import json
import re
import shlex
import sys

# 어느 워커든 허용하는 git 하위 명령 (조회 전용)
GIT_READ_SUBCOMMANDS = {
    "diff", "log", "show", "status", "grep", "rev-parse", "ls-files",
    "blame", "merge-base", "shortlog", "cat-file",
}
# git branch는 목록 조회 플래그만 허용 (-d, -D, -m 등 차단)
GIT_BRANCH_FLAGS = {"--show-current", "-a", "-r", "--list", "-v", "-vv", "--all", "--remotes"}

# 파일을 읽기만 하는 명령
READ_COMMANDS = {
    "ls", "cat", "head", "tail", "wc", "grep", "rg", "find", "sort",
    "uniq", "cut", "tr", "pwd", "echo", "cd", "true", "nl", "diff",
}
# find에서 파일을 바꾸거나 다른 명령을 실행할 수 있는 옵션
FIND_FORBIDDEN = {"-delete", "-exec", "-execdir", "-ok", "-okdir", "-fprint", "-fprint0", "-fprintf", "-fls"}

# verifier만: 빌드·테스트 실행 (build/ 산출물 외에는 소스를 바꾸지 않는 태스크)
GRADLE_TASKS = {"test", "integrationTest", "check", "spotlessCheck", "compileJava", "compileTestJava", "clean", "build"}
GRADLE_OPTIONS = {"--info", "--stacktrace", "-q", "--quiet", "--console=plain", "--no-daemon", "--rerun-tasks", "--continue"}

# 허용하는 리다이렉션 (출력을 버리거나 합치는 것만)
SAFE_REDIRECTS = re.compile(r"\s(2>&1|2>/dev/null|>/dev/null|1>/dev/null)(?=\s|$)")


def block(reason: str) -> None:
    print(
        f"[readonly-bash-guard] 차단됨: {reason}\n"
        "이 워커는 읽기 전용이다. 파일·브랜치를 바꾸는 명령은 실행할 수 없다. "
        "수정이 필요하면 직접 하지 말고 오케스트레이터에게 보고해라.",
        file=sys.stderr,
    )
    sys.exit(2)


def check_git(args: list[str]) -> str | None:
    i = 0
    # git -C <dir> / --no-pager 같은 전역 옵션 건너뛰기
    while i < len(args) and args[i].startswith("-"):
        if args[i] == "-C" and i + 1 < len(args):
            i += 2
        elif args[i] == "--no-pager":
            i += 1
        else:
            return f"git 전역 옵션 '{args[i]}'은 허용하지 않는다"
    if i >= len(args):
        return "git 하위 명령이 없다"
    sub, rest = args[i], args[i + 1:]
    if any(a.startswith("--output") or a.startswith("--ext-diff") for a in rest):
        return "git의 --output / --ext-diff 옵션은 파일을 쓰거나 외부 명령을 실행할 수 있다"
    if sub in GIT_READ_SUBCOMMANDS:
        return None
    if sub == "branch":
        if all(a in GIT_BRANCH_FLAGS for a in rest):
            return None
        return "git branch는 목록 조회(--show-current, -a, -r, -v 등)만 허용한다"
    return f"git {sub}은(는) 허용 목록에 없다 (조회 명령만 가능)"


def check_gradle(args: list[str]) -> str | None:
    i = 0
    while i < len(args):
        a = args[i]
        if a == "--tests":
            i += 2  # 다음 인자는 테스트 필터
            continue
        if a.startswith("--tests="):
            pass
        elif a not in GRADLE_TASKS and a not in GRADLE_OPTIONS:
            return f"gradle 인자 '{a}'는 허용 목록에 없다 (허용 태스크: {', '.join(sorted(GRADLE_TASKS))})"
        i += 1
    return None


def check_segment(tokens: list[str], role: str) -> str | None:
    if not tokens:
        return None
    if "=" in tokens[0] and not tokens[0].startswith("-"):
        return "환경 변수 지정(VAR=값 명령)은 허용하지 않는다"
    cmd, args = tokens[0], tokens[1:]
    name = cmd.rsplit("/", 1)[-1]

    if name == "git":
        return check_git(args)
    if name in ("gradlew", "gradlew.bat"):
        if role != "verifier":
            return "gradle 실행은 verifier만 할 수 있다"
        return check_gradle(args)
    if name in READ_COMMANDS:
        if name == "find" and any(a in FIND_FORBIDDEN for a in args):
            return "find의 -delete / -exec 류 옵션은 허용하지 않는다"
        if name == "sort" and any(a == "-o" or a.startswith("--output") for a in args):
            return "sort -o는 파일을 쓴다"
        if name == "uniq" and len([a for a in args if not a.startswith("-")]) > 1:
            return "uniq에 출력 파일을 지정할 수 없다"
        return None
    return f"'{name}' 명령은 허용 목록에 없다"


def main() -> None:
    role = sys.argv[1] if len(sys.argv) > 1 else ""
    if role not in ("researcher", "verifier"):
        block(f"알 수 없는 워커 역할 '{role}'")

    try:
        data = json.load(sys.stdin)
        command = data["tool_input"]["command"]
    except Exception as e:  # 해석 못 하면 막는다
        block(f"hook 입력을 해석하지 못했다 ({e})")

    # 안전한 리다이렉션만 제거한 뒤, 남은 위험 문법을 검사
    cleaned = SAFE_REDIRECTS.sub(" ", " " + command)
    for pattern, why in ((">", "출력 리다이렉션(>)은 파일을 쓴다"),
                         ("`", "백틱 명령 치환은 허용하지 않는다"),
                         ("$(", "$( ) 명령 치환은 허용하지 않는다"),
                         ("<(", "프로세스 치환은 허용하지 않는다")):
        if pattern in cleaned:
            block(why)

    # &&, ||, ;, |, 줄바꿈으로 나눈 각 명령이 모두 허용 목록에 있어야 한다
    for segment in re.split(r"&&|\|\||;|\||\n", cleaned):
        segment = segment.strip()
        if not segment:
            continue
        if segment.endswith("&"):
            block("백그라운드 실행(&)은 허용하지 않는다")
        try:
            tokens = shlex.split(segment)
        except ValueError as e:
            block(f"명령을 해석하지 못했다 ({e})")
        reason = check_segment(tokens, role)
        if reason:
            block(reason)

    sys.exit(0)


if __name__ == "__main__":
    main()
