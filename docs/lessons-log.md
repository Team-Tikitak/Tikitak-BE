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
