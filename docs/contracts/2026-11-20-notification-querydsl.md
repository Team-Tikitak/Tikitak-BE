# 작업 계약: 알림 목록·개수·일괄 읽음 동적 쿼리 QueryDSL 전환

- **관련 이슈**: #205
- **브랜치**: `Refactor/#205-notification-querydsl` (develop에서 분기, `origin/develop` 17e9ff9)
- **상태**: 완료 (2026-11-21, PR #207)

## 목표
`NotificationRepository`에서 `(:teamId is null or n.teamId = :teamId)`를 쓰는 `@Query` 5개(목록 2, count 2, 일괄 update 1)를 QueryDSL `NotificationRepositoryCustom`/`Impl`로 옮긴다. API 응답과 조회·갱신 결과는 바꾸지 않는다.

## 결정 사항 (2026-11-20 사람 선택)
- **일괄 update도 QueryDSL `update`로 옮긴다.** 동적 조건이 있는 쿼리는 QueryDSL로 쓴다는 규칙(`docs/decisions/2026-11-19-querydsl-feed-list.md:36`)에 그대로 맞춘다. 옮기고 나면 `src/main/java`의 JPQL `:x is null or`가 0건이 된다.
  - flush/clear 동작은 지금과 같다. 지금 `@Modifying`은 옵션이 없어 `clearAutomatically = false`, `flushAutomatically = false`이다(spring-data-jpa 4.0.5 `Modifying`의 AnnotationDefault, javap 확인). `JPAUpdateClause.execute()`도 flush·clear를 호출하지 않는다(querydsl-jpa 5.1.0 javap 확인).
  - update 뒤 같은 트랜잭션에서 Notification을 다시 읽는 경로는 없다. `markAllAsRead`(`NotificationService.java:157-160`)는 update 한 줄뿐이고, 호출자는 컨트롤러 하나이며 `open-in-view: false`(`application.yaml:27`)이다. 그래서 clear를 추가하지 않는다.
- **범위 조건과 커서를 나눈다.**
  - `NotificationScope(Long memberId, Long teamId)`: count 2개와 update가 받는다. 커서가 count나 update에 들어갈 수 없다.
  - `NotificationListCondition(Long memberId, Long teamId, LocalDateTime cursorCreatedAt, Long cursorId)`: 목록만 받는다. 범위 술어는 `NotificationScope`와 같은 빌더를 쓴다.
- **결정 기록은 새로 쓰지 않는다.** 기존 `2026-11-19-querydsl-feed-list.md`를 갱신한다(아래 완료 조건).
- **"네이티브 쿼리마다 Testcontainers 통합 테스트" 규칙은 이번에 추가하지 않는다.** 후속 전환 후보에서 `TeamMemberRepository`만 뺀다.
- SQL 동등성 기준은 #158과 같다. 결과, 정렬, 커서가 같으면 SQL 문자열 차이는 허용한다. 예를 들어 teamId가 null이면 술어가 SQL에서 빠진다.

## 범위: 건드려도 되는 파일
- `src/main/java/kusitms/spin/tikitak/repository/notification/NotificationRepository.java`
- (신규) `src/main/java/kusitms/spin/tikitak/repository/notification/NotificationRepositoryCustom.java`, `NotificationRepositoryCustomImpl.java`, `NotificationScope.java`, `NotificationListCondition.java`
- `src/main/java/kusitms/spin/tikitak/service/notification/NotificationService.java`: `listNotifications`, `getUnreadCount`, `markAllAsRead`(107-160)만
- `src/test/java/kusitms/spin/tikitak/service/notification/NotificationServiceTest.java`
- (신규) `src/test/java/kusitms/spin/tikitak/repository/notification/NotificationQueryIntegrationTest.java`
- `docs/decisions/2026-11-19-querydsl-feed-list.md`, `docs/decisions/README.md`(인덱스 문구가 바뀔 때만)

## 설계
- **Custom 메서드**
  - `List<Notification> findPage(NotificationListCondition condition, int limit)`: 정렬은 `createdAt desc, id desc`이다. 커서가 있으면 `createdAt < c or (createdAt = c and id < cid)`를 붙인다.
  - `long countAll(NotificationScope scope)`, `long countUnread(NotificationScope scope)`: `countUnread`는 `isRead = false`를 더한다. `fetchOne()`으로 받고 null이면 0L이다.
  - `long markAllAsRead(NotificationScope scope, LocalDateTime readAt)`: `set isRead = true, readAt = :readAt`, 조건은 범위 술어와 `isRead = false`이다. 반환값은 갱신 건수이고, 서비스는 쓰지 않는다.
- **범위 술어**: `member.id = memberId`는 항상 붙인다. teamId가 non-null일 때만 `teamId = teamId`를 붙인다.
- **조건 record**
  - `memberId`는 `requireNonNull`로 확인한다.
  - `NotificationListCondition`의 커서 두 필드는 둘 다 null이거나 둘 다 non-null이어야 한다. `hasCursor()`와 `scope()`를 둔다.
  - 둘 다 repository 패키지에 둔다. ArchUnit L4 때문에 서비스의 private `Cursor`를 참조하지 않는다.
- **Impl**: #158 `FeedRepositoryCustomImpl` 패턴을 따른다. Q 상수는 static final이고, 공용 `JPAQueryFactory`를 생성자로 주입받는다. `@Transactional`은 붙이지 않는다(C2). update의 트랜잭션은 서비스의 `@Transactional`(`:157`)이 맡는다.
- **서비스**
  - `findFirstPage`/`findCursorPage` 분기를 없애고 `findPage(condition, pageSize + 1)` 하나로 호출한다.
  - count와 update는 `new NotificationScope(memberId, teamId)`로 호출한다.
  - `parseCursor`, `normalizePageSize`, 응답 조립은 그대로 둔다.

## 의도된 동작 변경
- **없음.** 목록(items 순서와 내용, `nextCursor`, `hasNext`, `totalCount`), 안 읽은 개수, 일괄 읽음 처리 결과가 teamId 지정·미지정 모두 이전과 같다.
- 실패 경로도 바뀌지 않는다. 커서 형식 오류(COMMON001)는 서비스 `parseCursor`에서 나며, 이 코드는 바꾸지 않는다.
- 근거는 아래 통합 테스트와 서비스 테스트다.

## 완료 조건
- [ ] `NotificationRepository`에 `findFirstPage`, `findCursorPage`, `countByMemberIdAndTeamId`, `countByMemberIdAndTeamIdAndIsReadFalse`, `updateAllAsReadByMemberId`가 없다(`src` grep 0건). `findByIdAndMemberId`는 그대로다. `NotificationRepository`가 `NotificationRepositoryCustom`을 상속한다.
- [ ] `git grep -nE ":[A-Za-z]+ is null or" -- src/main/java` 결과가 `TeamMemberRepository.java`(네이티브) 1건뿐이다.
- [ ] `NotificationRepositoryCustomImpl`은 `JPAQueryFactory`를 생성자로 주입받고, `@Transactional`·`EntityManager` 주입·`new JPAQueryFactory`·`fetchCount`/`fetchResults`를 쓰지 않는다.
- [ ] `NotificationQueryIntegrationTest`(`IntegrationTest` 상속)가 다음을 검증한다.
  - 목록: teamId 지정이면 그 팀만, null이면 그 회원의 모든 팀(teamId가 null인 알림 포함)이 나온다. 다른 회원 알림은 빠진다. 정렬은 `createdAt desc, id desc`, limit이 적용된다.
  - 커서: **같은 createdAt**을 `persistWithCreatedAt`/`overrideCreatedAt`으로 명시하고, "id는 작지만 더 최신", "id는 크지만 더 과거" 반례를 넣어 tie-breaker를 검증한다(lessons-log 43행).
  - `countAll`, `countUnread`: teamId 지정·null 각각, 다른 회원 제외, 읽은 알림 제외(unread)
  - `markAllAsRead`: 대상(그 회원, 그 팀, 안 읽음)만 `isRead = true`와 `readAt`이 채워진다. 다른 회원, 다른 팀(teamId 지정 시), 이미 읽은 알림의 기존 `readAt`은 바뀌지 않는다. 반환 건수가 맞다. 확인은 `flushAndClear` 후 다시 조회해서 한다. teamId null이면 그 회원의 안 읽은 알림 전체가 바뀐다.
- [ ] `NotificationServiceTest`
  - 기존 stub/verify(목록 226-267, unread 289-300, 일괄 읽음 336-346)를 새 메서드로 바꾼다.
  - 인자는 기대 객체 전체를 `eq(new NotificationListCondition(...))`/`eq(new NotificationScope(...))` 또는 captor `isEqualTo`로 비교한다(lessons-log 53행). 일부 필드만 보는 `argThat`은 쓰지 않는다. limit은 `pageSize + 1`을 `eq`로 확인한다.
  - **커서 경로 테스트를 추가한다.** 유효한 커서 문자열을 넘기면 조건 객체에 디코딩된 `cursorCreatedAt`/`cursorId`가 들어간다. count는 커서 없는 `NotificationScope`로 호출된다.
- [ ] `./gradlew spotlessCheck`, `./gradlew test`(ArchUnit, 전체 컨텍스트 `TikitakApplicationTests`·`ExternalCallConnectionTest` 포함), `./gradlew integrationTest` 통과
- [ ] 결정 기록 `2026-11-19-querydsl-feed-list.md` 갱신
  - 상태 줄과 "적용 범위"에 #205 알림 전환 완료를 적는다.
  - "후속 전환 후보": `NotificationRepository`는 완료로 표시하고, `TeamMemberRepository`는 네이티브 쿼리라 후보에서 뺀다고 적는다.
  - 리스크 `:51`의 "이 범위를 벗어난 기능은 아직 쓰지 않았다"를 고친다. update DML(`JPAUpdateClause`)을 쓰기 시작했고, 통합 테스트로 확인한 결과를 적는다.
  - update가 flush/clear를 하지 않는다는 점과 그 근거를 적는다. 앞으로 update 뒤에 다시 읽는 경로가 생기면 clear가 필요하다는 주의도 적는다.

## 커밋 구성
1. `refactor`: `NotificationScope`, `NotificationListCondition`, `NotificationRepositoryCustom`/`Impl` 추가, `NotificationRepository`가 Custom을 상속, `NotificationQueryIntegrationTest` 추가. 기존 `@Query` 5개는 아직 남긴다.
2. `refactor`: `NotificationService`가 새 메서드를 쓰도록 바꾸고, 기존 `@Query` 5개를 제거한다. `NotificationServiceTest`를 옮기고 커서 테스트를 추가한다. 결정 기록과 계약 상태를 갱신한다.

## 하지 말 것
- 범위 밖 리팩터링, 손으로 하는 포맷 정리(Spotless 결과는 허용)
- `TeamMemberRepository` 등 다른 리포지토리 전환, 인덱스·마이그레이션 추가, API 응답 변경
- Impl에 `@Transactional`이나 EntityManager 주입, clear/flush 추가
- `QuerydslConfig`, `IntegrationTest`, `build.gradle`, ArchUnit 수정(이미 갖춰져 있음)
- 사람 승인 없이 커밋/푸시

## 참고
- 조사: `NotificationRepository.java:14-75`, `NotificationService.java:107-160, 222-251`, `Notification.java:30-62`(`teamId`는 `Long` 컬럼, `member`는 LAZY 연관), `V22__create_notification.sql:14-15`(인덱스 `member_id, created_at DESC, id DESC` / `member_id, is_read`)
- #158 패턴: `FeedListCondition.java`, `FeedRepositoryCustomImpl.java`, `FeedListQueryIntegrationTest.java`, `FeedServiceTest.java:538-540, 679-680`
- 확인된 것 (구현 중):
  - `QNotification`의 boolean 경로 이름은 `isRead`이다(생성된 `QNotification.java`의 `BooleanPath isRead`).
  - QueryDSL update는 Hibernate 7.2.12에서 오류 없이 아래 SQL을 만든다. `member.id`는 join 없이 FK로 비교한다. integrationTest `NotificationQueryIntegrationTest` 일괄 읽음 3건의 로그에서 확인했다. 전환 전 JPQL이 만든 SQL 로그와 직접 대조하지는 않았다.
    - `update notification n1_0 set is_read=?,read_at=? where n1_0.member_id=? [and n1_0.team_id=?] and n1_0.is_read=?`
- 미확인:
  - Hibernate가 `executeUpdate` 전에 auto-flush하는지. 기존과 같은 `createQuery().executeUpdate()` 경로라 차이는 없을 것으로 본다(추정).
