# 작업 계약: 피드에 태그된 팀원에게 전용 알림 발송

- **관련 이슈**: #210
- **브랜치**: `Feat/#210-feed-tagged-notification` (origin/develop e42d175에서 분기)
- **상태**: 승인됨 (2026-11-22)

## 목표
일반 게시글을 작성할 때 태그된 팀원(작성자 제외)은 `FEED_UPLOADED` 대신 `FEED_TAGGED` 알림을 받는다. 나머지 활성 팀원은 지금처럼 `FEED_UPLOADED`를 받는다. 게시글 하나에 한 사람이 받는 알림은 하나뿐이다.

## 결정 사항 (2026-11-22 사람 선택)
- 이벤트: 새 record `domain/notification/event/FeedTaggedEvent(List<Long> recipientMemberIds, Long actorTeamMemberId, String actorNickname, Long feedId, Long teamId)`를 만든다. `FeedPostedEvent`와 모양이 같고, 기존 패턴(이벤트 하나에 타입 하나)을 따른다. `FeedPostedEvent`는 바꾸지 않는다.
- 타입: `NotificationType.FEED_TAGGED`
- 문구: title `게시글에 태그됐어요`, body `{닉네임}님이 회원님을 게시글에 태그했어요.`
- data: `feedId`, `teamId` (문자열). `FEED_UPLOADED`와 같다.
- 피드를 수정해 태그를 추가해도 알림을 보내지 않는다(`updateFeed`는 건드리지 않는다). 이유:
  - `Feed.syncTags`(`Feed.java:125-139`)는 반환값이 void라서, 새로 추가된 태그를 알려면 비교 코드가 더 필요하다.
  - 태그를 뺐다 다시 넣으면 알림이 다시 가는 것을 막을 기록이 없다.
  - 필요하면 후속 이슈로 다룬다.

## 범위: 건드려도 되는 파일
- `src/main/java/kusitms/spin/tikitak/domain/notification/enums/NotificationType.java` (값 추가)
- `src/main/java/kusitms/spin/tikitak/domain/notification/event/FeedTaggedEvent.java` (신규)
- `src/main/java/kusitms/spin/tikitak/service/feed/FeedService.java` (`createFeed`만 수정)
- `src/main/java/kusitms/spin/tikitak/service/notification/NotificationEventListener.java` (핸들러 추가)
- `src/test/java/kusitms/spin/tikitak/service/feed/FeedServiceTest.java`
- `src/test/java/kusitms/spin/tikitak/service/notification/NotificationEventListenerTest.java`
- 마이그레이션은 없다. 근거:
  - `V22__create_notification.sql:4`가 `type VARCHAR(50) NOT NULL`이고, migration 디렉터리 grep으로 CHECK 제약이 없는 것을 확인했다.
  - `Notification.type`은 `@Enumerated(STRING)`, `length = 50`이다(`Notification.java:40-42`).
  - `FEED_TAGGED`는 11자다.

## 구현 방식

> 줄 번호는 origin/develop e42d175 기준이다.
`FeedService.createFeed`(`FeedService.java:313-356`)를 고친다. `save` 뒤의 코드만 바뀐다.
1. 태그 수신자: `taggedMembers`에서 `id != author.getId()`인 것만 골라 `tm.getMember().getId()`로 바꾼다.
   - 쿼리는 늘지 않는다. 작성자가 아닌 태그 대상은 `findActiveByTeamIdAndIds`가 `join fetch tm.member`로 불러온다(`TeamMemberRepository.java:91-106`).
2. 일반 수신자: 기존 `findActiveMemberIdsByTeamIdExcludingTeamMember` 결과에서 태그 수신자 member.id를 메모리에서 뺀다. 쿼리와 리포지토리는 바꾸지 않는다.
3. 발행:
   - 일반 수신자가 비어 있지 않으면 `FeedPostedEvent`를 발행한다(지금 자리).
   - 태그 수신자가 비어 있지 않으면 `FeedTaggedEvent`를 발행한다.
   - 둘은 서로 독립이다.

리스너: `handleFeedPosted`(`NotificationEventListener.java:91-112`)를 복제해 `handleFeedTagged`를 만든다.
- `@Async("notificationExecutor")`와 `@TransactionalEventListener(phase = AFTER_COMMIT)`를 붙인다.
- 수신자마다 `try { notificationService.send } catch (Exception) { log.error }`로 실패를 격리한다.

## 의도된 동작 변경
| 경우 | 이전 | 이후 |
|---|---|---|
| 작성, 작성자 외 태그 없음(기본) | 다른 활성 팀원 전체가 `FEED_UPLOADED`를 받는다 | 동일. `FeedTaggedEvent`는 발행하지 않는다 |
| 작성, 일부 팀원 태그 | 태그된 사람도 `FEED_UPLOADED`를 받는다 | 태그된 사람은 `FEED_TAGGED`, 나머지는 `FEED_UPLOADED`를 받는다 |
| 작성, 다른 활성 팀원 전원 태그 | 전원 `FEED_UPLOADED` | 전원 `FEED_TAGGED`. `FeedPostedEvent`는 발행하지 않는다 |
| 요청 태그 목록에 작성자 id 포함 | 작성자는 알림을 받지 않는다 | 동일. 작성자는 두 알림 모두 받지 않는다(`resolveTaggedMembersIncludingAuthor` 656행이 중복을 없앤다) |
| 검증 실패(FEED005~009, MEDIA 등, `save` 전) | 기존 에러 응답. 조회·발행 없음 | 동일 |
| 1인 팀 | 이벤트를 발행하지 않는다 | 동일. 다른 사람을 태그하면 FEED008이 난다 |
| 피드 수정으로 태그 추가 | 알림 없음 | 동일(결정 사항) |
| 태그 대상이 탈퇴 회원(TeamMember는 활성) | 일반 알림 저장 단계에서 빠진다 | 태그 알림 저장 단계에서 빠진다(`NotificationPersistService.java:36-41`) |
| 커밋 실패, executor 포화 | #208 계약과 같다 | 동일. 다만 태그가 있으면 게시글 하나에 async 제출이 최대 2건이다 |
| 알림 목록 API와 FCM data의 `type` | 4종 | `FEED_TAGGED`가 새로 나올 수 있다 |

- 응답 코드·본문은 바뀌지 않는다(201, `FeedController.java:95-96`).
- 추가 쿼리는 없다.
- LEFT/BANNED 팀원이나 비활성 팀의 팀원을 태그하면 지금처럼 FEED008로 요청 전체가 실패한다(`FeedService.java:629-645`). 그래서 태그 수신자는 일반 수신자 조회와 같은 상태 조건을 통과한 사람들이다.

## 완료 조건
- [x] `NotificationType`에 `FEED_TAGGED`가 있고, 기존 4개 값은 그대로다
- [x] `FeedTaggedEvent` record의 필드가 결정 사항과 일치한다. `FeedPostedEvent`는 바뀌지 않았다
- [x] `updateFeed`, 리포지토리, 기존 리스너 메서드 diff가 0이다
- [x] `FeedServiceTest` (createFeed)
  - 일부 태그: `FeedPostedEvent`의 수신자에서 태그된 member.id가 빠지고, `FeedTaggedEvent`의 수신자는 태그된 member.id(작성자 제외)다. 두 이벤트 모두 기대 객체 전체와 `isEqualTo`로 비교한다
  - 전원 태그: `FeedTaggedEvent`만 발행되고 `FeedPostedEvent`는 발행되지 않는다(이벤트 타입별로 `ArgumentCaptor`/`never()` 구분)
  - 태그 없음: `FeedPostedEvent`만 발행되고 `FeedTaggedEvent`는 발행되지 않는다(기존 테스트가 이걸 보장하지 않으면 보강)
  - 요청 태그 목록에 작성자 id를 넣어도 작성자 member.id가 두 이벤트 어디에도 없다
- [x] `NotificationEventListenerTest`
  - `FEED_TAGGED` payload의 type, title, body, actorTeamMemberId, data(`feedId`, `teamId`)를 정확히 비교한다
  - 첫 수신자의 `send`가 예외를 던져도 다음 수신자 `send`가 호출된다
- [x] 새 리스너 메서드에 `@Async("notificationExecutor")`와 `@TransactionalEventListener(phase = AFTER_COMMIT)`가 붙어 있다
- [x] 수정한 Java 파일에 Spotless를 적용했고 `./gradlew spotlessCheck`가 통과한다
- [x] `./gradlew test`가 통과한다(ArchUnit 포함). 쿼리·스키마 변경이 없으므로 integrationTest는 필수가 아니다

## 커밋 구성
1. `[Feat] 피드 태그된 팀원에게 태그 알림 발송 (#210)`: 위 범위 전체와 이 계약 파일. 작은 변경이라 한 커밋으로 낸다

## 하지 말 것
- `updateFeed`나 `Feed.syncTags` 수정, 수정 시 알림
- `TeamMemberRepository`·`DailyQuestionTeamMemberRepository` 쿼리 변경
- `FeedPostedEvent` 필드 변경, 기존 핸들러·문구 수정
- 이벤트 record에 엔티티(`TeamMember` 등) 담기. ArchUnit L5(domain slice 순환)를 피하려는 것이다
- executor 설정 변경, 범위 밖 리팩터링, 손 포맷, 사람 승인 없는 커밋·푸시

## 남은 리스크
- 앱이 모르는 `type`을 받을 때 어떻게 동작하는지, `FEED_TAGGED`를 눌렀을 때 어느 화면으로 가는지는 **미확인**이다(저장소 밖). PR에 FE 확인 요청을 적는다.
- 일반 수신자에서 태그 수신자를 member.id 기준으로 뺀다. 그런데 team_member의 `(team_id, member_id)`에는 DB 유니크가 없다(`V1__initial_schema.sql:45-55`). 같은 팀에 같은 member의 활성 TeamMember가 둘 있으면 어떻게 될지는 **미확인**이다. 다만 이 경우에도 그 member는 태그 알림 하나만 받는다(일반 목록에서 빠지므로).
- 태그가 있으면 `notificationExecutor`(core 2 / max 4 / queue 50, `AsyncConfig.java:17-19`)에 제출하는 작업이 게시글 하나에 2건이 된다. 실제 부하 수준은 **미확인**이다.
- `TeamMember.nickname`은 NULL을 허용한다(`V1__initial_schema.sql:49`). 닉네임이 null이면 문구가 "null님이"가 된다. 기존 알림도 같은 문제를 갖고 있고, 실제로 null이 생기는지는 **미확인**이다.

## 참고
- 배경: `docs/contracts/2026-11-22-feed-uploaded-notification.md` 결정 사항(태그 알림을 만들 때 일반 알림에서 빼고 대체한다)
- 결정 기록: `docs/decisions/2026-11-05-external-call-outside-transaction.md`
- lessons: 같은 결과 분기는 `never()`로 고정(2026-11-04), record는 기대 객체 전체로 비교(2026-11-19)
