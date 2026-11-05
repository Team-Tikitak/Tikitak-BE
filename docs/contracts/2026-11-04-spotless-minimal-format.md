# 작업 계약: Spotless 최소 포맷 규칙 도입 및 일괄 포맷

- **관련 이슈**: 없음 (하네스 Step 5)
- **브랜치**: `chore/spotless-minimal-format` (develop 20f3e63에서 분기)
- **상태**: 승인됨 (2026-11-04)

## 목표
- 포맷을 도구로 고정한다. 범위 밖 포맷 변경이 기능 PR에 섞이지 않게 하고, CI에서 포맷 불일치를 막는다.
- 현재 다수 스타일(탭 4칸, IntelliJ 기본 import 레이아웃)을 유지한다. 줄바꿈·정렬을 다시 계산하는 포맷터(google-java-format, palantir, eclipse)는 쓰지 않는다.
- 동작 변경은 0이다.

## 규칙 (조사: 2026-11-04 researcher, HEAD 20f3e63)

| 규칙 | 현재 불일치 (추정) |
|---|---|
| 앞쪽 스페이스 4칸 → 탭 (`leadingSpacesToTabs(4)` 또는 해당 버전의 동등 기능) | 스페이스 파일 44개, 혼용 3개, 약 2,650줄 |
| 줄 끝 공백 제거 | 3개 파일 |
| 파일 끝 개행 | 5개 파일 |
| 미사용 import 제거 | 약 3줄 |
| import 순서: 일반 → `java|javax` → static | 약 20~25개 파일 |

대상은 `src/main/java/**/*.java`, `src/test/java/**/*.java`다. 와일드카드 import 4개와 연속 들여쓰기 불일치는 이번 규칙 밖이라 그대로 둔다.

## 범위: 건드려도 되는 파일
- `build.gradle`: `com.diffplug.spotless` 플러그인과 `spotless { java { ... } }` 블록만 추가한다.
- `.github/workflows/ci.yaml`: gradle 태스크에 `spotlessCheck`를 추가한다. `cd.yml`은 건드리지 않는다.
- `.editorconfig` (신규): `*.java`는 tab, indent_size 4, insert_final_newline, trim_trailing_whitespace. IntelliJ import 레이아웃(`ij_java_imports_layout`)을 Spotless 순서에 맞추고, 와일드카드로 합치지 않게 한다(`ij_java_*_on_demand = 999`). (2026-11-04 사용자 요청으로 추가)
- `.gitattributes`: `*.java text eol=lf` 한 줄을 추가한다.
- `src/main/java/**`, `src/test/java/**`: **`spotlessApply` 결과만** 넣는다. 손으로 고치지 않는다.
- `CLAUDE.md`: Commands에 `./gradlew spotlessApply` / `spotlessCheck` 안내를 넣는다. Conventions에 "커밋 전 spotlessApply" 한 줄을 넣는다.
- `.claude/agents/implementer.md`, `docs/contracts/TEMPLATE.md`: "포맷 정리 금지" 문구를 다음 뜻으로 조정한다. 손 포맷 정리는 금지하고, 수정한 파일에 `spotlessApply`를 돌리는 것은 허용한다.
- `docs/decisions/2026-11-04-spotless-minimal-rules.md` (신규), `docs/decisions/README.md` (인덱스 한 줄)

## 커밋 구성 (한 PR, 커밋 2개)
1. **설정 커밋**: 위 파일 중 `src/**`를 제외한 전부
2. **포맷 커밋**: `./gradlew spotlessApply` 결과만 넣는다.

`.git-blame-ignore-revs`는 머지 후 develop에 생긴 포맷 커밋 SHA로 후속 커밋에서 추가한다. 이 레포는 squash 머지를 쓰기 때문이다.

## 완료 조건
- [ ] Spotless 플러그인 버전은 Gradle 9.4.1과 Java 21에서 실제로 동작하는 최신 안정 버전으로 하고, 보고한다.
- [ ] 포맷 커밋을 `git diff -w --ignore-blank-lines HEAD~1 HEAD -- src`로 봤을 때 import 줄 변경과 파일 끝 개행 외에는 비어 있다. 공백 외 변경이 없다는 증거다.
- [ ] 포맷 커밋 뒤 `./gradlew spotlessCheck`가 통과한다. 다시 `spotlessApply`를 돌려도 변경이 0이다.
- [ ] `./gradlew test` 통과 (ArchUnit 포함)
- [ ] 실제 변경 규모(`git diff --stat` 파일 수·줄 수)를 보고하고, 추정치(50~70개 파일)와 크게 다르면 이유를 적는다.
- [ ] 결정 기록에 다음을 적는다.
  - 최소 규칙을 고른 이유. 포맷터 후보별 예상 diff를 근거로 한다.
  - `ratchetFrom`을 기각한 이유. 파일 단위라 기능 PR이 오염되고, CI에 fetch-depth가 필요하며, 미정리 목록이 보이지 않는다.
  - 남는 불일치(와일드카드 import, 연속 들여쓰기)

## 하지 말 것
- 포맷터가 아닌 손 수정, 리팩터링, 줄바꿈 재배치
- `removeUnusedImports`가 사용 중인 import(예: javadoc 참조)를 지워 컴파일이나 테스트가 깨지면, 그 import를 손으로 되살리지 말고 멈추고 보고한다.
- google-java-format, palantir, eclipse 등 레이아웃 포맷터 사용
- `cd.yml` 수정, `check` 태스크 의존 관계 변경
- 사람 승인 없이 커밋/푸시 (커밋 2개 모두 각각 승인)

## 참고
- 조사 시점에 열린 PR은 0건이고, 최근 2주 내 머지되지 않은 활동 브랜치도 없다. 일괄 포맷의 충돌 비용이 가장 낮은 시점이다.
- ArchUnit 결정(`docs/decisions/2026-11-02-archunit-explicit-exceptions.md`)에서 동결 store를 기각한 논리가 `ratchetFrom` 기각에도 그대로 적용된다.
