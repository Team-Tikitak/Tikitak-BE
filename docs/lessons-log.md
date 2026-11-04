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
| 2026-11-04 | Spotless 일괄 포맷(#170): `leadingSpacesToTabs`가 text block 안 SQL/JSON 98줄의 정렬 공백을 버렸는데, 검증과 critic이 놓치고 CodeRabbit이 찾음. 결정 기록에는 "나머지 스페이스는 남고 그런 줄도 없다"고 확인 없이 적음 | "동작 변경 0" 증거로 쓴 `git diff -w`가 문자열 안의 공백 변경까지 숨김 | 미승격. 공백 포맷 검증 시 `-w` 결과와 별도로 들여쓰기 폭이 바뀐 줄(특히 `"""` 안)을 확인한다. 도구 동작은 문서에 쓰기 전에 실제 결과로 확인 |
| 2026-11-04 | #168: 계약의 "의도된 동작 변경"에 refresh·발급 경로만 적었는데, 같은 private 메서드(`sign`/`hash`)를 부르는 access 검증·로그아웃 경로와, 상위 `catch (Exception)`이 감싸던 loginCode 발급 경로(AUTH105→COMMON004)가 빠짐. verifier가 찾음 | 예외 타입을 바꿀 때 상위 호출부의 `catch (BusinessException) rethrow` / `catch (Exception) wrap` 구조로 최종 ErrorCode가 달라지는 경로를 다 나열하지 않았음 | 미승격. 예외 타입·private 메서드를 바꾸는 계약은 researcher가 호출부 전체와 각 경로의 최종 응답 코드를 표로 낸다 |
| 2026-11-04 | #168: "만료 토큰이면 AUTH007" 테스트가 만료 분기가 아니라 저장소 조회 분기에서 같은 코드로 통과할 수 있었음 | Mockito mock은 stub하지 않은 `Optional` 반환을 `Optional.empty()`로 돌려줘서, 뒤쪽 분기도 같은 에러 코드를 냄 | 미승격. 같은 에러 코드를 내는 분기가 여럿이면 뒤 단계 호출에 `verify(..., never())`를 같이 넣어 분기를 고정한다 |
| 2026-11-04 | #168: `@PostConstruct` 설정 검증을 새 빈으로 옮길 때, 메서드를 직접 부르는 단위 테스트만으로는 빈 등록·스캔이 빠져도 통과함 | 단위 테스트는 기동 시점 실행을 검증하지 않음 | 미승격. 기동 검증을 옮기면 `ApplicationContextRunner`로 컨텍스트 기동 실패/성공을 확인한다. Properties 기본값도 생성자 직접 호출 말고 바인딩 경로로 확인 (critic 지적) |
