# Spotless는 레이아웃 포맷터 없이 공백·import 최소 규칙만 적용한다

- **날짜**: 2026-11-04
- **관련 이슈**: 없음 (작업 하네스 후속, 계약 `docs/contracts/2026-11-04-spotless-minimal-format.md`)
- **상태**: 결정됨, 적용 완료

## 상황 (Context)
포맷 규칙이 도구로 고정돼 있지 않아서, 기능 PR에 범위 밖 포맷 변경(들여쓰기, import 순서)이 섞이고 리뷰 diff가 커졌다. 코드의 다수 스타일은 탭 4칸 들여쓰기와 IntelliJ 기본 import 레이아웃이지만, 스페이스 들여쓰기 파일 47개, 줄 끝 공백 파일 3개, 파일 끝 개행이 없는 파일 5개, import 순서가 다른 파일이 섞여 있었다. 포맷을 Spotless(`com.diffplug.spotless` 8.11.0)로 고정하고 CI에서 `spotlessCheck`로 막기로 했다.

## 검토한 선택지 (Options considered)
레이아웃 포맷터 후보는 Spotless 기본 설정으로 현재 코드(255개 파일)에 적용해 diff를 쟀다.

| 후보 | 변경 파일 | 변경 줄(+/-) | 공백 무시(`-w --ignore-blank-lines`) 후 남는 줄(+/-) |
|---|---|---|---|
| google-java-format | 255 | +20,396 / -19,987 | 225개 파일, +4,929 / -4,532 |
| google-java-format AOSP | 250 | +19,188 / -17,938 | 225개 파일, +5,710 / -4,472 |
| palantir-java-format | 250 | +16,150 / -16,856 | 222개 파일, +2,739 / -3,471 |
| eclipse (기본 프로필) | 199 | +4,686 / -8,050 | 180개 파일, +2,533 / -5,897 |
| **최소 규칙 (채택)** | **77** | **+3,121 / -3,127** | **29개 파일, +61 / -66 (전부 import 줄)** |

최소 규칙은 다음 다섯 가지다 (`build.gradle`의 `spotless { java { ... } }`).
- `removeUnusedImports()`: 미사용 import 제거
- `importOrder('', 'java|javax', '\\#')`: 일반 → `java`/`javax` → static
- `leadingSpacesToTabs(4)`: 앞쪽 스페이스 4칸을 탭으로
- `trimTrailingWhitespace()`: 줄 끝 공백 제거
- `endWithNewline()`: 파일 끝 개행

## 결정 (Decision)
최소 규칙만 적용한다. 레이아웃 포맷터(google-java-format, palantir, eclipse)는 쓰지 않는다. 적용 범위는 `src/main/java/**/*.java`, `src/test/java/**/*.java`이고, CI(`ci.yaml`)에서 `spotlessCheck`를 돌린다. 일괄 포맷은 설정 커밋과 분리된 포맷 커밋 하나로 넣는다.

## 이유 (Rationale)
- **diff가 공백뿐이다.** 레이아웃 포맷터는 공백을 무시해도 2,500~5,700줄이 바뀐다. 줄바꿈과 정렬을 다시 계산하기 때문이다. 이 규모는 "동작 변경 0"을 리뷰로 확인할 수 없다. 최소 규칙은 공백을 무시하면 import 줄만 남아서, 코드 구조가 바뀌지 않았다는 것을 `git diff -w`로 기계적으로 확인할 수 있다. 단, `-w`는 문자열(text block) 안의 공백 변경도 숨기므로 text block은 따로 확인했다(아래 남은 리스크 참고).
- **다수 스타일을 유지한다.** google-java-format과 palantir는 스페이스 들여쓰기라 탭을 쓰는 다수 파일이 전부 바뀐다. eclipse는 탭을 쓰지만 줄바꿈 규칙이 달라 180개 파일이 공백 외로도 바뀐다.
- **미사용 import 제거는 레이아웃을 건드리지 않는다.** `removeUnusedImports()`는 내부적으로 google-java-format의 import 제거 기능만 쓰고 레이아웃 포맷은 하지 않는다. 일괄 적용에서 지워진 import는 4줄이었고, 전부 코드에서 참조하지 않는 import였다(정규화된 이름으로만 참조하는 경우 포함). 컴파일과 테스트는 그대로 통과했다.

## 기각: `ratchetFrom`
`ratchetFrom 'origin/develop'`은 기준 브랜치 이후 바뀐 파일만 검사하므로 일괄 포맷 없이 도입할 수 있다. 다음 이유로 기각했다.
- **파일 단위라 기능 PR이 오염된다.** 기능 PR에서 한 줄을 고친 파일은 그 파일 전체가 검사 대상이 되므로, 기존 스페이스 들여쓰기 수백 줄이 그 PR의 diff에 섞인다. 막으려던 문제가 그대로 남는다.
- **CI에 fetch-depth가 필요하다.** 기준 브랜치와 비교하려면 `actions/checkout`에 `fetch-depth: 0` 같은 설정과 기준 ref fetch가 필요하다. 설정이 빠지면 검사가 실패하거나 조용히 건너뛴다.
- **미정리 목록이 보이지 않는다.** 아직 정리되지 않은 파일이 어디에도 적혀 있지 않다. ArchUnit에서 동결 store를 기각한 이유([결정 기록](2026-11-02-archunit-explicit-exceptions.md))와 같다.

## 트레이드오프 / 남은 리스크
- **남는 불일치**: 와일드카드 import(4개)와 연속 들여쓰기(줄이 이어질 때의 들여쓰기 폭) 불일치는 이번 규칙 밖이라 그대로 남는다. 레이아웃 포맷터 없이는 연속 들여쓰기를 고정할 방법이 없다.
- **IntelliJ를 Spotless 순서에 맞춘다**: `java|javax`를 한 그룹으로 묶고 그룹 안은 사전순이라, IntelliJ 기본(`javax` → `java`)과 달리 `java`가 `javax`보다 앞에 온다. Spotless `importOrder`로는 IntelliJ 기본(두 패키지를 빈 줄 없이 `javax` → `java`)을 표현할 수 없어서(그룹을 나누면 사이에 빈 줄이 생긴다), 반대로 `.editorconfig`의 `ij_java_imports_layout = *,|,java.**,javax.**,|,$*`로 IntelliJ의 Optimize Imports가 Spotless와 같은 순서를 쓰게 했다. 같은 파일에서 `ij_java_class_count_to_use_import_on_demand`와 `ij_java_names_count_to_use_static_import_on_demand`를 999로 두어, IntelliJ가 import를 와일드카드(`.*`)로 합치지 않게 했다. Spotless는 와일드카드를 펼치지 않기 때문이다. 2026-11-04 IntelliJ에서 `TokenService`(javax 사용)와 `TeamMember`(같은 패키지 import 13개)에 Optimize Imports를 돌린 뒤 `spotlessCheck`가 통과하는 것을 확인했다. 이 설정은 IntelliJ의 EditorConfig 지원(기본값 켜짐)이 켜져 있어야 적용된다. 최종 기준은 CI의 `spotlessCheck`다.
- **`leadingSpacesToTabs(4)`는 4칸 단위로 내림하고, text block 안도 바꾼다.** 앞쪽 공백을 4칸 단위 탭으로 바꾸면서 4로 나누어떨어지지 않는 나머지 스페이스는 버린다(예: 탭 3개 + 스페이스 2칸 → 탭 3개, 스페이스 14칸 → 탭 3개). javadoc의 ` *` 정렬은 바뀌지 않는다. 이 변환은 Java 문법을 보지 않고 줄 단위로 하므로 text block(`"""`) 안의 줄에도 적용된다. 일괄 적용에서 20개 파일 98줄의 들여쓰기 폭이 줄었고, 전부 text block 안의 SQL/JPQL 쿼리(`FeedRepository`, `FeedTagRepository`, `MediaRepository` 등)와 테스트 요청 JSON이었다. 둘 다 앞쪽 공백에 의미가 없어 동작은 바뀌지 않았고(`test`, `integrationTest` 통과), `where` 아래 `and`처럼 스페이스로 맞춘 정렬만 사라졌다. 앞으로도 text block 안의 정렬은 탭 단위로만 할 수 있다.
- **공백에 의미가 있는 text block**(YAML, Markdown, 고정 폭 텍스트 등)은 Spotless가 내용을 바꿀 수 있다. 현재 코드에는 없다. 그런 text block이 생기면 `toggleOffOn()`을 켜고 해당 구간을 `// spotless:off` / `// spotless:on`으로 감싸거나, 해당 파일을 Spotless 대상에서 제외한다. 결정 기록을 갱신한다.
- `git blame`에 포맷 커밋이 나타난다. 머지 후 develop에 생긴 포맷 커밋 SHA를 `.git-blame-ignore-revs`에 후속 커밋으로 추가한다(squash 머지라 PR 안의 SHA는 쓸 수 없다).
