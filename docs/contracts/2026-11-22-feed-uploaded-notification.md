# 작업 계약: 일반 게시글 작성 시 팀원 알림 발송

- **관련 이슈**: #208
- **브랜치**: `Feat/#208-feed-created-notification` (origin/develop 2dc3a21에서 분기)
- **상태**: 완료 (2026-11-22, PR #211)

## 목표
일반 게시글(GENERAL 피드)을 작성하면 작성자를 뺀 활성 팀원에게 `FEED_UPLOADED` 알림(저장 + FCM 푸시)이 간다. 오늘의 질문 답변 알림(`DailyAnswerPostedEvent`)과 같은 방식(커밋 후 비동기, 수신자별 실패 격리)을 따른다.

## 결정 사항 (2026-11-22 사람 선택)
- 타입: `NotificationType.FEED_UPLOADED` (기존 `DAILY_QUESTION_UPLOADED`와 형식 통일)
- 이벤트: `domain/notification/event/FeedPostedEvent(List<Long> recipientMemberIds, Long actorTeamMemberId, String actorNickname, Long feedId, Long teamId)` (`DailyAnswerPostedEvent`와 같은 모양)
- 문구: title `새 게시글이 올라왔어요`, body `{닉네임}님이 새 게시글을 올렸어요.`
- data: `feedId`, `teamId` (문자열). `Notification.create`가 이 키 이름으로 `feed_id`/`team_id` 컬럼을 채운다(`Notification.java`)
- 태그된 팀원 전용 알림, 연속 게시글 묶음은 이번 범위 밖. 이번에는 태그된 팀원도 일반 알림 수신자에 포함돼 한 번 받는다. 후속 이슈에서 태그 알림(`FEED_TAGGED` 가칭)을 만들 때 태그된 팀원(작성자 제외)을 일반 알림에서 빼고 태그 알림으로 대체한다(게시글 하나당 한 사람에게 알림 하나). 지금 빼지 않는 이유: 대체 알림 없이 빼면 태그된 사람이 아무 알림도 못 받는다

## 범위: 건드려도 되는 파일
- `src/main/java/kusitms/spin/tikitak/domain/notification/enums/NotificationType.java` (값 추가)
- `src/main/java/kusitms/spin/tikitak/domain/notification/event/FeedPostedEvent.java` (신규)
- `src/main/java/kusitms/spin/tikitak/service/feed/FeedService.java` (`ApplicationEventPublisher` 주입, `createFeed`에서 `save` 뒤 발행)
- `src/main/java/kusitms/spin/tikitak/service/notification/NotificationEventListener.java` (핸들러 추가)
- `src/test/java/kusitms/spin/tikitak/service/feed/FeedServiceTest.java`
- `src/test/java/kusitms/spin/tikitak/service/notification/NotificationEventListenerTest.java`
- 마이그레이션 없음. 근거: `V22__create_notification.sql:4` `type VARCHAR(50) NOT NULL`, CHECK 제약 없음(migration 디렉터리 grep), `Notification.type`은 `@Enumerated(EnumType.STRING)`, `FEED_UPLOADED`는 13자

## 구현 방식

> 이 문서의 `FeedService.java` 줄 번호는 origin/develop 2dc3a21 기준이다(구현 후 save는 346행, `question(null)`은 329행).

- 발행: `DailyQuestionService.java:123-128` 패턴 그대로. `FeedService.java:343` `feedRepository.save` 뒤에 `teamMemberRepository.findActiveMemberIdsByTeamIdExcludingTeamMember(teamId, author.getId(), TeamMemberStatus.ACTIVE, TeamStatus.ACTIVE)`(member.id 반환, teamMemberId로 작성자 제외, `TeamMemberRepository.java:190-204`)로 수신자를 구하고, 비어 있지 않을 때만 `FeedPostedEvent` 발행
- 리스너: `NotificationEventListener.java:66-87` `handleDailyAnswerPosted` 복제. `@Async("notificationExecutor")` + `@TransactionalEventListener(phase = AFTER_COMMIT)`, 수신자마다 `try { notificationService.send } catch (Exception) { log.error }`

## 의도된 동작 변경
| 경우 | 이전 | 이후 |
|---|---|---|
| `POST` 일반 피드 작성 성공, 다른 활성 팀원 있음 | 201, 알림 없음 | 201(응답 동일), 커밋 후 비동기로 각 팀원에게 `FEED_UPLOADED` 알림 저장 + 푸시 |
| 작성 성공, 다른 활성 팀원 없음(1인 팀) | 201, 알림 없음 | 201, 이벤트 미발행 |
| 검증 실패(FEED006 등, `save` 전 예외) | 기존 에러 응답 | 동일, 수신자 조회·발행 없음 |
| `save` 뒤 커밋 실패 | 기존 에러 응답(롤백) | 동일. AFTER_COMMIT이 실행되지 않으므로 알림 없음 |
| 커밋 뒤 executor 큐 포화로 `@Async` 제출 거부 | — | 응답은 201 그대로, 알림 누락 + 에러 로그. 근거: spring-tx 7.0.7 javap — AFTER_COMMIT은 `PlatformSynchronization.afterCompletion(int)`에서 처리되고, `TransactionSynchronizationUtils.invokeAfterCompletion`이 `Throwable`을 잡아 "TransactionSynchronization.afterCompletion threw exception"으로 로그만 남김. 데일리 답변 알림과 같은 기존 특성 |
| 탈퇴 회원이 활성 TeamMember로 남은 경우 | — | 수신자 조회에는 포함되지만 `NotificationPersistService.saveForActiveMember`(37-41행)에서 빠짐(기존 데일리와 동일) |
| 알림 목록 API 응답 `type` | 3종 | `FEED_UPLOADED` 문자열이 새로 나올 수 있음(`NotificationResponseDTO.java:31` enum 그대로 직렬화). FCM data `type`도 동일 |

- 성공 응답 201 근거: `FeedController.java:95-96` `ResponseEntity.status(HttpStatus.CREATED)`
- 수신자 조회 쿼리 1회가 `createFeed` 트랜잭션 안에 추가된다.
- 데일리 답변 피드와의 중복 알림 없음: `createFeed`는 `question(null)`로 GENERAL만 만들고(`FeedService.java:326`), 데일리 답변은 `DailyQuestionService.createMyAnswer`가 따로 저장한다.

## 완료 조건
- [ ] `NotificationType`에 `FEED_UPLOADED`가 있고 기존 3개 값은 그대로다
- [ ] `FeedPostedEvent` record 필드가 위 결정 사항과 일치한다
- [ ] `FeedServiceTest`
  - 수신자가 있으면 `FeedPostedEvent` 1회 발행, 필드(수신자 목록·actorTeamMemberId·닉네임·feedId·teamId)를 기대 객체 전체와 `isEqualTo`로 비교 (feedId 검증을 위해 `save` stub은 id가 있는 Feed를 돌려준다)
  - 수신자 조회 인자가 `(TEAM_ID, 작성자 teamMemberId, ACTIVE, ACTIVE)`인지 검증(작성자 제외)
  - 수신자 목록이 비면 `verify(eventPublisher, never()).publishEvent(any())`
  - 검증 실패(예: FEED006)면 수신자 조회와 발행 모두 `never()`
- [ ] `NotificationEventListenerTest`
  - 수신자마다 `send` 호출, payload의 type=`FEED_UPLOADED`, title, body, actorTeamMemberId, data(`feedId`,`teamId`) 검증
  - 첫 수신자 `send`가 예외를 던져도 다음 수신자 `send`가 호출된다(실패 격리)
- [ ] 새 리스너 메서드에 `@Async("notificationExecutor")`와 `@TransactionalEventListener(phase = AFTER_COMMIT)`가 붙어 있다(코드 확인)
- [ ] 수정한 Java 파일에 Spotless 적용, `./gradlew spotlessCheck` 통과
- [ ] `./gradlew test` 통과 (ArchUnit 포함). integrationTest는 쿼리·스키마 변경이 없어 필수 아님

## 커밋 구성
1. `[Feat] 일반 게시글 작성 시 팀원 알림 발송 (#208)` — 위 범위 전체 + 이 계약 파일 (작은 변경이라 한 커밋)

## 하지 말 것
- 태그 알림, 알림 묶음(throttle), executor 설정 변경
- 기존 핸들러·문구 수정, 기존 핸들러에 실패 격리 테스트 추가 등 범위 밖 보강
- `TeamMemberRepository` 쿼리 변경
- 범위 밖 리팩터링, 손 포맷, 사람 승인 없는 커밋/푸시

## 남은 리스크
- 앱이 모르는 `type`을 받았을 때 동작과 `FEED_UPLOADED` 화면 이동 처리: 저장소 밖이라 **미확인**. PR에 FE 확인 요청을 적는다.
- 일반 피드는 데일리 답변보다 자주 생겨 `notificationExecutor`(core 2, max 4, queue 50, `AsyncConfig.java:14-25`) 사용량이 늘어난다. 포화 시 알림 누락(위 표). 실제 부하 수준은 **미확인**.

## 참고
- 조사: `DailyQuestionService.java:123-128`, `NotificationEventListener.java:66-87`, `NotificationService.java:61-106`
- 결정 기록: `docs/decisions/2026-11-05-external-call-outside-transaction.md`(알림 저장/FCM 분리)
- lessons: 같은 결과 분기 `never()` 고정(2026-11-04), 조건 객체 전체 `eq` 비교(2026-11-19)
