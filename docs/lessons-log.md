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
