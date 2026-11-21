# 작업 계약: 피드 댓글 목록 커서 조회 QueryDSL 전환

- **관련 이슈**: #204
- **브랜치**: `Refactor/#204-feed-comment-querydsl` (develop에서 분기)
- **상태**: 완료 (2026-11-21, PR #206)

## 목표
`FeedCommentRepository`의 댓글 목록 커서 조회 `@Query` 4개(feedImageId 유무 × 커서 유무)를 QueryDSL 동적 쿼리 메서드 하나(`findActivePage(condition, limit)`)로 합친다. API 응답과 조회 결과는 바꾸지 않는다.

## 결정 사항 (2026-11-20 사람 선택)
- fetch 경로는 기존과 같게 `teamMember`, `feedImage` 두 개만 fetch join한다. `teamMember.member`는 추가하지 않는다. 작성자 member가 프로필 fallback(`DefaultProfileImageResolver.java:39-44`)에서 lazy 로딩되는 것은 지금과 같다.
- "네이티브 쿼리는 Testcontainers 통합 테스트로 검증한다" 규칙은 결정 기록 `2026-11-19-querydsl-feed-list.md`의 "프로젝트 쿼리 작성 규칙"에 한 줄로 추가한다. `CLAUDE.md`는 바꾸지 않는다.
- `TeamMemberRepository.countNewActivitiesByMemberTeams`의 `selectedTeamId = null` 경로 통합 테스트는 이번 범위가 아니다. 별도 이슈로 둔다.
- 아래는 조사 추천을 그대로 따른다(선례 `FeedListCondition`과 대칭).
  - 조건 객체 이름은 `FeedCommentListCondition`(record)이고 `repository.feed`에 둔다. service에 두면 ArchUnit L4 위반이다(`LayerDependencyArchTest.java:47-52`).
  - 커서는 평평한 두 필드 + `hasCursor()`로 표현한다.
  - limit은 `int limit`으로 받는다. 값은 서비스가 계산하는 `pageSize + 1`이다.
  - 결정 기록은 새로 만들지 않고 기존 기록을 갱신한다.

## 범위: 건드려도 되는 파일
- `src/main/java/kusitms/spin/tikitak/repository/feed/FeedCommentRepository.java`: 4개 메서드 제거, `FeedCommentRepositoryCustom` 상속, 쓰지 않게 된 import 정리
- (신규) `src/main/java/kusitms/spin/tikitak/repository/feed/FeedCommentListCondition.java`, `FeedCommentRepositoryCustom.java`, `FeedCommentRepositoryCustomImpl.java`
- `src/main/java/kusitms/spin/tikitak/service/feed/FeedCommentService.java`: `findCommentPage`(192-206)만. `parseCursor`/`encodeCursor`/private `Cursor`는 그대로 둔다.
- `src/test/java/kusitms/spin/tikitak/service/feed/FeedCommentServiceTest.java`
- (신규) `src/test/java/kusitms/spin/tikitak/repository/feed/FeedCommentListQueryIntegrationTest.java`
- `docs/decisions/2026-11-19-querydsl-feed-list.md`, `docs/decisions/README.md`(해당 행 요약만)

## 설계
- **`FeedCommentListCondition`**: `Long feedId`(필수), `Long feedImageId`(null이면 이미지 필터 없음), `LocalDateTime cursorCreatedAt`, `Long cursorCommentId`.
  - compact 생성자에서 두 가지를 검사한다. `feedId`는 `requireNonNull`이고, 커서 두 필드는 둘 다 null이거나 둘 다 non-null이어야 한다(아니면 `IllegalArgumentException`). `FeedListCondition`과 같은 방식이다.
  - `hasCursor()`를 둔다.
- **`findActivePage(condition, int limit)`**
  - 항상: `feed.id = feedId`, `deleted = false`
  - feedImageId가 있으면: `feedImage.id = feedImageId`
  - 커서가 있으면: `createdAt > c or (createdAt = c and id > id)`. **오름차순이라 `>`다.**
  - 정렬과 개수: `order by createdAt asc, id asc`, `limit`
  - fetch: `teamMember`, `feedImage`를 `.join().fetchJoin()`으로 가져온다. 두 FK 모두 `optional = false` / DB NOT NULL이다(`FeedComment.java:36-42`, `V9:5`, `V1:82`). 그래서 inner join으로 써도 결과 행이 같다.
- **서비스**: `findCommentPage`의 4갈래 분기를 없앤다. `new FeedCommentListCondition(feedId, feedImageId, cursor.createdAt(), cursor.commentId())`와 `pageSize + 1`로 한 번 호출한다. 커서 문자열 파싱, feedImage 사전 검증(COMMENT011), pageSize 정규화는 그대로 둔다.

## 의도된 동작 변경
- **없음.** 댓글 목록 API(`FeedCommentController.java:34-45`)의 items 순서·내용·`hasNext`·`nextCursor`는 피드 전체 / 이미지별 × 첫 페이지 / 커서 4개 조합 모두에서 이전과 같다.
- 에러 응답도 같다. 잘못된 커서는 COMMON001, 다른 피드의 이미지는 COMMENT011이고, 둘 다 쿼리 전에 던진다.
- SQL 문자열은 달라진다. 커밋 1(`c24e672`) 작업 중 implementer가 `SPRING_JPA_SHOW_SQL=true`로 integrationTest를 실행해 확인한 결과, 기존 `@EntityGraph`와 새 `fetchJoin` 모두 `team_member`, `feed_image`를 inner join으로 생성했다(Hibernate 7.2.12). 확인된 차이는 join 순서와 `is_deleted`가 리터럴인지 바인딩인지다. 이미지 필터는 새 SQL에서 join한 `feed_image`의 PK(`fi1_0.id=?`)로 나온다(커밋 2 SQL 로그). 기존 `@Query`가 FK 컬럼(`feed_image_id=?`)으로 냈는지는 미확인이다. 두 FK 모두 NOT NULL inner join이라 결과 행은 같다. PostgreSQL이 등가 추론으로 `idx_feed_comment_feed_image_created_id`를 그대로 쓸 것으로 보지만 EXPLAIN으로는 확인하지 않았다(미확인). 쿼리 수는 늘지 않는다.

## 완료 조건
- [ ] `./gradlew spotlessCheck`, `./gradlew test`, `./gradlew integrationTest`가 통과한다. ArchUnit 예외 상수는 추가하지 않는다.
- [ ] `FeedCommentRepository`와 `src/` 전체에서 다음 4개 메서드 이름이 grep 0건이다: `findActiveFirstPageByFeedId`, `findActiveCursorPageByFeedId`, `findActiveFirstPageByFeedImageId`, `findActiveCursorPageByFeedImageId`. 대상이 아닌 메서드(`findActiveByIdAndFeedId`, count/position 쿼리)는 그대로다.
- [ ] `FeedCommentRepositoryCustomImpl`이 공용 `JPAQueryFactory`를 생성자로 주입받는다. main에서 `new JPAQueryFactory`는 `QuerydslConfig`에서만 grep 1건이다.
- [ ] `FeedCommentListQueryIntegrationTest`가 `findActivePage`로 다음을 검증한다.
  - 피드 전체: 같은 피드의 다른 이미지 댓글이 모두 포함되고, 다른 피드 댓글은 제외된다.
  - 이미지별: 해당 이미지 댓글만 나온다.
  - 커서 다음 페이지: 피드 전체와 이미지별 각각 검증한다.
  - **같은 createdAt에서 id tie-breaker(오름차순, `>`)**: `persistWithCreatedAt`으로 createdAt을 명시적으로 같게 맞춘다. persist 순서와 기대 순서가 우연히 같아지지 않도록 시각을 섞는다(lessons-log 43행).
  - 삭제된 댓글(`deleted = true`)은 제외된다.
  - limit만큼만 반환된다.
  - `flushAndClear` 후 조회한 댓글의 `teamMember`, `feedImage`가 `Hibernate.isInitialized`로 초기화돼 있다.
- [ ] `FeedCommentListCondition`의 생성자 검증(커서 한쪽만 null, feedId null)을 단위 테스트나 통합 테스트로 확인한다.
- [ ] `FeedCommentServiceTest`의 기존 stub/verify(281, 301, 309, 396)가 `findActivePage`로 바뀐다. 조건 객체는 기대 객체 전체와 `eq(...)` 또는 캡처 후 `isEqualTo`로 비교한다(lessons-log 53행). limit은 `eq(pageSize + 1)`로 확인한다. 커서가 있는 경로를 검증하는 서비스 테스트를 1건 이상 추가한다.
- [ ] 결정 기록 `2026-11-19-querydsl-feed-list.md`
  - 상태 줄과 적용 범위에 #204 전환 완료를 적는다.
  - "후속 전환 후보"에서 `FeedCommentRepository`를 완료로 표시한다.
  - `TeamMemberRepository`는 네이티브 쿼리라 규칙("네이티브는 네이티브로")상 후보에서 뺀다고 정정한다.
  - "쿼리 작성 규칙"에 두 줄을 넣는다. 하나는 "네이티브 쿼리를 추가·수정할 때는 그 쿼리를 실행하는 Testcontainers 통합 테스트를 함께 둔다"이다. 다른 하나는 "조건 조합마다 메서드를 나눈 정적 쿼리도 동적 조건 쿼리로 본다"이다.
  - README 인덱스 해당 행 요약에 #204를 반영한다.

## 커밋 구성
1. `refactor`: `FeedCommentListCondition`, `FeedCommentRepositoryCustom`/`Impl`을 추가하고 `FeedCommentRepository`가 Custom을 상속한다. `FeedCommentListQueryIntegrationTest`를 추가한다. 기존 4개 메서드는 아직 남긴다.
2. `refactor`: `FeedCommentService`가 새 메서드를 쓰게 하고 기존 4개 `@Query`를 제거한다. `FeedCommentServiceTest`를 옮기고 커서 경로 테스트를 추가한다. 결정 기록과 README를 갱신한다.

## 하지 말 것
- `teamMember.member` fetch 추가 등 fetch 경로 변경, 응답 DTO 변경
- `FeedCommentRepository`의 다른 쿼리, `NotificationRepository`, `TeamMemberRepository` 수정
- `QuerydslConfig`, `IntegrationTest`, `build.gradle`, ArchUnit 테스트 수정
- `fetchResults()`/`fetchCount()`(deprecated) 사용, repository에 `@Transactional`(C2)·`@Value`(C10)
- `CLAUDE.md` 수정, 범위 밖 리팩터링, 손으로 하는 포맷 정리
- 사람 승인 없이 커밋/푸시

## 참고
- 대상: `FeedCommentRepository.java:57-123`, `FeedCommentService.java:44-77, 192-228, 251`
- 선례: `FeedListCondition.java`, `FeedRepositoryCustomImpl.java`, `FeedListQueryIntegrationTest.java`(297-354 tie-breaker·fetch 검증), `FeedServiceTest.java:679-682, 770-774`(조건 전체 eq)
- 시각 고정: `IntegrationTest.java:111-134` `persistWithCreatedAt`. FeedImage 생성 예: `FeedImageRepositoryIntegrationTest.java:105-113`
- 인덱스: `idx_feed_comment_feed_created_id (feed_id, created_at, id)`, `idx_feed_comment_feed_image_created_id (feed_image_id, created_at, id)` (V9:15-19)
- 확인됨: `QFeedComment`는 생성된다(커밋 1 `compileJava`). 기존 `@EntityGraph`는 Hibernate 7.2.12에서 `team_member`, `feed_image`를 inner join으로 낸다(커밋 1 작업 중 implementer가 `SPRING_JPA_SHOW_SQL=true` integrationTest SQL 로그로 확인).
