#!/usr/bin/env bash
# 비평 워커: 현재 브랜치의 변경을 Codex(다른 벤더 모델)에게 읽기 전용으로 리뷰받는다.
#
# 사용법:
#   scripts/codex-critic.sh [기준 브랜치] [계약 파일]
#   예) scripts/codex-critic.sh develop docs/contracts/2026-11-05-feed-like.md
#
# 리뷰 대상은 "기준 브랜치...HEAD"의 커밋된 변경만이다. 커밋 안 된 파일은 보지 않는다.
# 결과는 표준 출력으로 나오고, .codex-reviews/ 아래에도 저장된다.
#
# 참고: Windows에서 Codex read-only 샌드박스는 셸 명령 실행에 실패한다
# (CreateProcessWithLogonW failed: 267). 그래서 Codex가 직접 git diff를 돌리게 하지 않고,
# 이 스크립트가 규칙 문서와 diff를 모아 stdin으로 넘긴다.
set -euo pipefail

BASE="${1:-develop}"
CONTRACT="${2:-}"

cd "$(git rev-parse --show-toplevel)"

if [[ -n "$CONTRACT" && ! -f "$CONTRACT" ]]; then
  echo "계약 파일이 없음: $CONTRACT" >&2
  exit 1
fi

if git diff --quiet "$BASE...HEAD"; then
  echo "$BASE 대비 커밋된 변경이 없음. 리뷰할 게 없다." >&2
  exit 1
fi

# Codex에게 넘길 자료 묶음
section() { printf '\n===== %s =====\n' "$1"; cat "$2"; }
BUNDLE=$(mktemp)
{
  section "AGENTS.md" AGENTS.md
  section "CLAUDE.md" CLAUDE.md
  section "docs/lessons-log.md" docs/lessons-log.md
  if [[ -n "$CONTRACT" ]]; then section "작업 계약: $CONTRACT" "$CONTRACT"; fi
  printf '\n===== 변경 파일 목록 (git diff --stat %s...HEAD) =====\n' "$BASE"
  git diff --stat "$BASE...HEAD"
  printf '\n===== 변경 내용 (git diff -U20 %s...HEAD) =====\n' "$BASE"
  git diff -U20 "$BASE...HEAD"
} > "$BUNDLE"

CONTRACT_LINE="작업 계약은 없다. 변경 내용 자체만 보고 판단해라."
[[ -n "$CONTRACT" ]] && CONTRACT_LINE="작업 계약이 함께 주어졌다. 변경이 계약의 목표·범위·완료 조건에 맞는지도 판단해라."

PROMPT=$(cat <<PROMPT
너는 티키탁 백엔드의 비평 워커(코드 리뷰어)다. 구현한 모델과 다른 모델로서, 구현자가 놓쳤을 문제를 찾는 게 목적이다.

필요한 자료(AGENTS.md, CLAUDE.md, docs/lessons-log.md, 작업 계약, ${BASE} 대비 diff)는 아래 <stdin>에 모두 들어 있다.
셸 명령은 실행하지 마라 (이 환경에서는 실패한다). 주어진 자료만으로 판단하고, diff 바깥 코드를 봐야 확신할 수 있는 건 "확인 필요"로 표시해라.
${CONTRACT_LINE}

찾을 것 (중요한 순서):
1. 버그: 잘못된 동작, 빠진 예외 처리, 트랜잭션/동시성 문제, N+1 같은 쿼리 문제
2. 데이터: 엔티티 변경인데 Flyway 마이그레이션 누락, 운영 DB에서 위험한 마이그레이션(락, 데드락)
3. 보안: 인증/인가 누락, SecurityConfig 화이트리스트 오용, 다른 팀 데이터 접근
4. 프로젝트 규칙 위반: BusinessException/ErrorCode, CommonResponse, PatchField, *Properties
5. 테스트 공백: 새 동작이나 버그 수정에 대응하는 테스트가 없음
6. docs/lessons-log.md의 과거 실수가 반복됨

하지 말 것: 파일 수정, 커밋, 포맷/네이밍 같은 취향 지적.

한국어로, 발견마다 "[심각도: 높음/중간/낮음] file:line — 문제 — 왜 문제인지 — 제안" 형식으로 써라.
확신이 낮은 건 "확신 낮음"이라고 표시해라. 문제가 없으면 "발견 없음"이라고만 써라.
PROMPT
)

mkdir -p .codex-reviews
OUT=".codex-reviews/$(date +%Y%m%d-%H%M%S)-$(git branch --show-current | tr '/#' '__').md"

# 진행 로그는 stderr로, 최종 리뷰만 $OUT에 저장하고 출력한다.
codex exec --sandbox read-only --color never -o "$OUT" "$PROMPT" < "$BUNDLE" >&2
rm -f "$BUNDLE"

cat "$OUT"
echo
echo "저장됨: $OUT" >&2
