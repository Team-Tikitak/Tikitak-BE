# Lessons Log (실수 기록장)

사람이든 AI 워커든, 작업 중 생긴 **실수·실패·놓칠 뻔한 것**을 한 줄씩 남기는 곳.
AI는 세션이 끝나면 잊어버리므로, 다음 작업자(Claude, Codex, 사람)가 읽을 수 있게 레포 안에 둔다.

> `docs/decisions/`와의 차이: decisions는 "왜 이렇게 설계했나"(판단 기록), lessons-log는 "뭘 잘못했나 / 뭘 놓칠 뻔했나"(실수 기록).
> 자세한 설명이 필요하면 decisions에 파일을 따로 쓰고 여기서는 링크만 건다.

## 규칙

1. 실수가 생기면 아래 표에 **한 줄** 추가한다. 길게 쓰지 않는다.
2. 같은 종류의 실수가 **2번째** 나오면 "승격"한다. 아래 중 기계적으로 막을 수 있는 쪽을 우선한다.
   - 린터 / ArchUnit 규칙 (가장 강함: 어기면 빌드 실패)
   - 회귀 테스트
   - 설정 변경
   - `CLAUDE.md` 규칙 (가장 약함: 글로 된 부탁)
3. 승격하면 `승격` 칸에 어디로 옮겼는지 적는다.

## 기록

| 날짜 | 무슨 일이 있었나 | 원인 | 승격 |
|---|---|---|---|
| 2026-09-17 | Claude가 사용자의 개인 WIP(`DevController`/`DevService`, 미커밋)를 develop에 push하자고 제안함 | 코드가 완성돼 보이면 공유해도 된다고 판단함. 개인 테스트용이라 일부러 안 올린 코드였음 | 미승격 (Claude 개인 메모리에만 있음). `CLAUDE.md` 후보: "작업과 무관한 미커밋 파일은 건드리지 않는다" |
| 2026-09-19 | 피드 목록 조회 N+1 (#153). 부하 테스트에서 발견 | 페이지 단위 조회 후 피드별 연관 데이터를 개별 쿼리로 가져옴 | 미승격. 후보: 쿼리 수를 검증하는 회귀 테스트 |
| 2026-09-21 | 테스트 통과 후 사용자 확인 없이 커밋으로 넘어가려 함 | "테스트 통과 = 완료"로 간주함 | ✅ `CLAUDE.md` Git workflow 섹션 |
| 2026-09-21 | `CREATE INDEX CONCURRENTLY` 마이그레이션이 Flyway 자체 락과 데드락. 운영 배포 시 앱 부팅이 무한 대기할 뻔함 (#154) | Flyway 기본 설정이 트랜잭션 안에서 schema-history 락을 잡고 있음 | ✅ 설정 `spring.flyway.postgresql.transactional-lock: false` ([결정 기록](decisions/2026-09-21-flyway-concurrent-index-deadlock.md)). 후보: "CONCURRENTLY 마이그레이션은 integrationTest 필수"를 `CLAUDE.md`에 |
| 2026-11-02 | 하네스 리허설(#164): `Agent`의 `isolation: worktree`가 develop이 아니라 main(`origin/HEAD`)에서 분기함. worktree에 하네스 문서가 없었고, 테스트 수 보고도 어긋남(264 vs 269) | 자동 worktree는 현재 브랜치가 아니라 원격 기본 브랜치를 기준으로 만든다 | 미승격. 운영 방식: 오케스트레이터가 `git worktree add -b <브랜치> <경로> develop`으로 직접 만들고 경로를 워커에 넘긴다 |
| 2026-11-02 | 하네스 리허설(#164): Codex critic 지적 3건 중 2건이 사실과 다름 (이미 있는 단언을 "없다"고 지적) | critic은 stdin으로 받은 diff 텍스트만 보고, 근거 인용을 요구하지 않았음 | ✅ `scripts/codex-critic.sh` 프롬프트에 코드 줄 인용·"없다" 지적 전 재확인 규칙 추가 (같은 diff로 재실행 시 사실 오류 0건, 가정 기반 지적 1건은 남음). 오케스트레이터는 지적마다 코드와 대조 후 보고 |
| 2026-11-02 | 하네스 리허설(#164): critic을 verifier 직후(커밋 전)에 돌리려다 막힘 | `codex-critic.sh`는 `develop...HEAD`의 커밋된 변경만 리뷰함 | 미승격. 순서를 스크립트 주석에 적음: verifier → 승인 후 로컬 커밋 → critic → push |
| 2026-11-02 | `git worktree add -b fix/#164-...`로 만든 브랜치가 실제로는 `Fix/#164-...` ref로 저장돼 push 실패(`cannot be resolved to branch`) | Windows 파일시스템은 대소문자를 구분하지 않아서, 기존 `refs/heads/Fix/` 디렉터리 안에 ref가 생김 | 미승격. 브랜치를 만든 뒤 `git for-each-ref refs/heads`로 실제 ref 이름을 확인. 후보: 브랜치 접두사 대소문자 규칙 통일 |
| 2026-11-04 | Spotless 일괄 포맷(#170): `leadingSpacesToTabs`가 text block 안 SQL/JSON 98줄의 정렬 공백을 버렸는데, 검증과 critic이 놓치고 CodeRabbit이 찾음. 결정 기록에는 "나머지 스페이스는 남고 그런 줄도 없다"고 확인 없이 적음 | "동작 변경 0" 증거로 쓴 `git diff -w`가 문자열 안의 공백 변경까지 숨김 | 미승격. 공백 포맷 검증 시 `-w` 결과와 별도로 들여쓰기 폭이 바뀐 줄(특히 `"""` 안)을 확인한다. 도구 동작은 문서에 쓰기 전에 실제 결과로 확인. (확인 없이 적은 문서 주장은 2026-11-05 #162 항목에서 승격) |
| 2026-11-04 | #168: 계약의 "의도된 동작 변경"에 refresh·발급 경로만 적었는데, 같은 private 메서드(`sign`/`hash`)를 부르는 access 검증·로그아웃 경로와, 상위 `catch (Exception)`이 감싸던 loginCode 발급 경로(AUTH105→COMMON004)가 빠짐. verifier가 찾음 | 예외 타입을 바꿀 때 상위 호출부의 `catch (BusinessException) rethrow` / `catch (Exception) wrap` 구조로 최종 ErrorCode가 달라지는 경로를 다 나열하지 않았음 | 미승격. 예외 타입·private 메서드를 바꾸는 계약은 researcher가 호출부 전체와 각 경로의 최종 응답 코드를 표로 낸다. (2026-11-06 #161 항목에서 승격) |
| 2026-11-04 | #168: "만료 토큰이면 AUTH007" 테스트가 만료 분기가 아니라 저장소 조회 분기에서 같은 코드로 통과할 수 있었음 | Mockito mock은 stub하지 않은 `Optional` 반환을 `Optional.empty()`로 돌려줘서, 뒤쪽 분기도 같은 에러 코드를 냄 | 미승격. 같은 에러 코드를 내는 분기가 여럿이면 뒤 단계 호출에 `verify(..., never())`를 같이 넣어 분기를 고정한다 |
| 2026-11-04 | #168: `@PostConstruct` 설정 검증을 새 빈으로 옮길 때, 메서드를 직접 부르는 단위 테스트만으로는 빈 등록·스캔이 빠져도 통과함 | 단위 테스트는 기동 시점 실행을 검증하지 않음 | 미승격. 기동 검증을 옮기면 `ApplicationContextRunner`로 컨텍스트 기동 실패/성공을 확인한다. Properties 기본값도 생성자 직접 호출 말고 바인딩 경로로 확인 (critic 지적) |
| 2026-11-05 | #162: 결정 기록에 "엔티티 `@PrePersist` 약 50곳"이라고 적었는데 실제는 13곳(50곳은 엔티티 안 `LocalDateTime.now()` 53곳). verifier가 찾음. #170에 이어 "문서에 확인 없이 쓴 사실"이 두 번째 | 조사 보고의 숫자를 다시 세지 않고 옮겨 적음 | ✅ `.claude/agents/verifier.md` 확인 순서 5번: 문서의 수치·전칭 주장을 grep으로 다시 세고 근거 명령을 보고 |
| 2026-11-05 | #162: `KstDateProvider`에 시스템 zone을 주입하도록 바꾸자, 기존 픽스처의 `LocalDateTime`(예: `TeamFixture` 2026-03-04T20:30)을 UTC로 해석하면 테스트가 다른 이유(경과일 < 0)로 깨짐 | 기존 테스트 픽스처의 시각 값이 암묵적으로 "시스템 zone = KST"를 전제로 함 | 미승격. 시각 해석 기준을 주입식으로 바꾸면 기존 테스트에는 그 전제에 맞는 zone을 명시하고, 새 경계 테스트에서만 UTC를 쓴다 |
| 2026-11-05 | #162: 응답 `month`만 단언하는 경계 테스트(고정 Clock = 12월)는 실제 12월에 돌리면 변경 전 코드(`YearMonth.now()`)로도 통과함 | 고정 Clock의 달이 실행 시점의 실제 달과 겹칠 수 있음 | 미승격. 경계 테스트는 범위 인자 `verify`처럼 실행 시점과 무관하게 판별되는 단언을 같이 넣거나, 고정 시각을 실제와 겹치지 않는 해로 고른다 |
| 2026-11-06 | #161: OAuth 로그인의 `@Transactional`을 별도 빈으로 옮기자 트랜잭션이 `AuthService`의 `catch (Exception)` 안으로 들어가, 트랜잭션 시작·커밋 실패 응답이 COMMON004→AUTH105로 바뀜. 계약 표에 없었고 implementer가 찾음. #168에 이어 "상위 catch 구조로 최종 응답이 바뀌는 경로 누락"이 두 번째 | 트랜잭션 경계를 옮기면 프록시가 던지던 시작·커밋 예외의 위치도 바뀌는데, 경로 표에 그 경로를 넣지 않았음 | ✅ `.claude/skills/harness-workflow/SKILL.md` 1단계(조사)·2단계(계약): 트랜잭션 경계를 옮기면 시작·커밋 실패 경로를 응답 표에 따로 넣는다 |
| 2026-11-06 | #161: 테스트용 H2(`create-drop`)에서 `key`가 예약어라 `media` 테이블 생성이 경고 로그만 남기고 실패함. media를 쓰는 `@SpringBootTest`가 원인을 알기 어려운 이유로 깨지고, 정리 단계에서 같은 클래스의 다른 테스트까지 실패시킴 | 공용 테스트 datasource URL에 `NON_KEYWORDS=KEY`가 없음. DDL 실패가 기동 실패로 이어지지 않음 | 미승격. 해당 테스트 클래스 전용 URL에만 `NON_KEYWORDS=KEY`를 붙임. 후보: 공용 테스트 URL에 추가 |
| 2026-11-06 | #161: 계약에 적은 "락 없이 조회"용 리포지토리 메서드가 실제로 없었고 그 파일이 범위에도 없어 구현이 한 번 멈춤. 앱 로그인 경로도 "토큰 발급·refresh token 저장"으로 적었지만 실제로는 발급하지 않는 경로였음(implementer가 기존 동작에 맞춤) | 계약을 쓸 때 쓸 메서드와 경로별 실제 호출을 코드에서 확인하지 않고 가정함 | 미승격. 후보: researcher가 계약에 쓸 메서드의 존재와 경로별 실제 호출 목록을 코드 인용으로 보고 |
| 2026-11-06 | #161: verifier의 `./gradlew test`가 UP-TO-DATE로 끝나 이전 실행 결과를 재사용한 것을 통과로 볼 뻔함. `--rerun-tasks`는 가드에 막혀 `clean test`로 우회함 | Gradle은 입력이 같으면 테스트를 다시 돌리지 않음. 가드 허용 목록에 강제 재실행 옵션이 없음 | 미승격. verifier는 UP-TO-DATE면 `clean test`로 다시 돌린다. 후보: 가드에 `--rerun-tasks` 허용 |
| 2026-11-06 | #183: implementer가 "테스트가 실제로 잡아내는지" 확인하려고 코드를 임시로 깨뜨린 뒤 `git checkout --`로 되돌려, 아직 커밋하지 않은 Feed.java·FeedComment.java의 작업 내용까지 지움. 미리 만든 백업으로 복구함 | `git checkout --`는 커밋된 상태로 되돌리므로, 미커밋 작업 위에서 한 실험을 되돌리는 데 쓰면 작업도 사라짐 | ✅ `.claude/agents/implementer.md` 지킬 것: 미커밋 파일로 실험할 때는 먼저 백업하고, 백업 복사로 되돌린 뒤 `cmp`로 확인한다. `git checkout --`/`git restore` 금지 |
| 2026-11-06 | #183: Feed 시각 고정 헬퍼를 넣을 때, FeedRegionFilter 커서 테스트는 시각이 모두 같아도 id 순서가 우연히 입력 순서와 맞아 통과함. 헬퍼 효과를 "기존 테스트 통과"로만 보면 헬퍼가 빠져도 알 수 없음 | 시각이 같으면 `created_at desc, id desc` 정렬이 id 순서로 떨어지고, 픽스처의 persist 순서가 그 id 순서와 같았음 | 미승격. 시각 고정 헬퍼·픽스처가 실제로 작동하는지는 헬퍼를 끈 상태에서 실패하는 테스트가 있는지로 확인한다 (#183에서는 9개 실패로 확인) |
