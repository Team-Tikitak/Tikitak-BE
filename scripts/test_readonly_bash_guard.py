"""readonly-bash-guard 회귀 테스트.

허용 목록을 고친 뒤에는 이 테스트를 돌려서, 위험한 명령이 실수로 열리지 않았는지 확인한다.
새로 막거나 허용한 명령이 있으면 아래 cases에 한 줄 추가한다.

실행: python scripts/test_readonly_bash_guard.py   (저장소 어디서든 가능)
"""

import json
import subprocess
import sys
from pathlib import Path

# 진입점(.sh)이 아니라 검사기(.py)를 현재 Python으로 직접 실행한다.
# Windows에서 subprocess의 "bash"는 Git Bash가 아니라 WSL bash로 잡힐 수 있어서 경로가 깨진다.
GUARD = Path(__file__).resolve().parent / "readonly_bash_guard.py"

# (워커 역할, 명령, 허용되어야 하면 True / 차단되어야 하면 False)
cases = [
 ("verifier","git diff --name-only develop...HEAD",True),
 ("verifier","git status --short",True),
 ("verifier","git log --oneline -5 | head -3",True),
 ("verifier","./gradlew test",True),
 ("verifier","./gradlew test --tests \"kusitms.spin.tikitak.service.feed.FeedServiceTest\" 2>&1 | tail -40",True),
 ("verifier","./gradlew integrationTest --info",True),
 ("verifier","./gradlew test spotlessCheck --console=plain",True),
 ("verifier","./gradlew spotlessApply",False),
 ("researcher","./gradlew spotlessCheck",False),
 ("verifier","git branch --show-current",True),
 ("researcher","grep -rn \"BusinessException\" src/main | wc -l",True),
 ("researcher","find src -name \"*.java\" | head",True),
 ("researcher","git -C ../wt log -1 && git show HEAD --stat",True),
 ("researcher","./gradlew test",False),
 ("verifier","rm -rf build",False),
 ("verifier","git reset --hard",False),
 ("verifier","git commit -m x",False),
 ("verifier","git checkout -- .",False),
 ("verifier","git stash",False),
 ("verifier","git branch -D develop",False),
 ("verifier","git status && rm a.txt",False),
 ("verifier","cat a > b.txt",False),
 ("verifier","echo hi >> CLAUDE.md",False),
 ("verifier","find . -name x -delete",False),
 ("verifier","find . -exec rm {} ;",False),
 ("verifier","sed -i s/a/b/ x.java",False),
 ("verifier","git diff --output=x.patch",False),
 ("verifier","ls $(rm x)",False),
 ("verifier","python -c 'import os'",False),
 ("verifier","FOO=1 ./gradlew test",False),
 ("verifier","./gradlew bootRun",False),
 ("verifier","git push",False),
]


def main() -> None:
    fails = 0
    for role, cmd, want in cases:
        p = subprocess.run(
            [sys.executable, str(GUARD), role],
            input=json.dumps({"tool_name": "Bash", "tool_input": {"command": cmd}}),
            capture_output=True, text=True, encoding="utf-8", timeout=20,
        )
        got = p.returncode == 0
        if got != want:
            fails += 1
            print(f"FAIL [{role}] {cmd!r}: 기대={'허용' if want else '차단'}, 실제={'허용' if got else '차단'} {p.stderr.strip()}")
    print(f"{len(cases) - fails}/{len(cases)} 통과")
    sys.exit(1 if fails else 0)


if __name__ == "__main__":
    main()
