# 작업 계약: 오늘의 질문 공개 알림 일괄 발송

- **관련 이슈**: #214
- **브랜치**: `Feat/#214-daily-question-opened-notification` (origin/develop 931c22c에서 분기)
- **상태**: 완료 (2026-11-24, PR #218)

## 목표
매일 KST 자정(설정값)에 활성 팀에 활성 멤버십이 하나 이상 있는 활성 회원 전체에게 `DAILY_QUESTION_OPENED` 알림을 회원당 한 번, 공통 문구로 보낸다. 대량 발송 개선의 기준선(baseline)으로 쓰기 위해 **최적화하지 않고** 기존 `NotificationService.send()`를 차례로 부르며, Micrometer 지표와 시작/종료 로그를 처음부터 넣는다.

## 결정 사항 (2026-11-24 사람 선택)
- 타입: `NotificationType.DAILY_QUESTION_OPENED`
- 문구: title `오늘의 질문이 도착했어요`, body `팀원들과 오늘의 질문에 답해 보세요.`, data 없음(기본값 `Map.of()`) → `team_id`/`feed_id`는 NULL, `actorTeamMemberId` null
- 발송 시각: `global/config/DailyQuestionOpenedNotificationProperties` record(prefix `notification.daily-question-opened`, 필드 `cron`, 비었거나 null이면 기본값 `0 0 0 * * *`)를 `PropertiesConfig`에 등록하고, `@Scheduled(cron = "#{@'<빈 이름>'.cron()}", zone = KstDateProvider.KST_ZONE_ID)`로 참조. 기본값은 record 한 곳에만 둔다
  - 빈 이름: `@EnableConfigurationProperties`로 등록한 빈은 `<prefix>-<FQCN>` 이름을 받는다(확인됨: `DailyQuestionOpenedNotificationPropertiesTest`에서 `getBeanNamesForType`이 `notification.daily-question-opened-kusitms.spin.tikitak.global.config.DailyQuestionOpenedNotificationProperties` 하나를 반환). SpEL 따옴표 빈 참조(`@'...'`)도 같은 테스트에서 `ScheduledTaskHolder`에 설정한 cron 작업이 등록되는 것으로 확인
  - `application.yaml`: `notification.daily-question-opened.cron: ${DAILY_QUESTION_OPENED_NOTIFICATION_CRON:}` (빈 값 → record 기본값)
  - 테스트 `src/test/resources/application.yaml`: `cron: "-"` (`Scheduled.CRON_DISABLED`, 테스트 컨텍스트에서 등록 안 함)
- 지표(배치 단위만, `send()` 내부는 손대지 않음). 모두 태그 `type=DAILY_QUESTION_OPENED`
  - `notification.batch.duration` Timer: 배치 전체 소요 시간
  - `notification.batch.recipient.query.duration` Timer: 받는 사람 조회 시간
  - `notification.batch.send.duration` Timer: 발송 루프 전체 시간
  - `notification.batch.recipients` DistributionSummary: 실행당 대상 수
  - `notification.batch.sends` Counter, 태그 `result=success|failure`
- 성공/실패 정의: `send()`가 예외 없이 끝나면 성공, 예외면 실패. `send()`가 void라 조회 뒤 탈퇴·FCM 미설정·토큰별 FAILED는 성공으로 집계된다(근거 `NotificationService.java:60-86`이 이 경우 return/로그만). `send()` 반환형 변경은 범위 밖
- 팀 범위 알림 조회에서 빠지는 문제는 그대로 두고 PR에 FE 확인 요청(아래 동작 변경 표)
- 중복 실행 방지(분산 락) 없음. 로그는 기존 배치처럼 영어

## 범위: 건드려도 되는 파일
- `src/main/java/kusitms/spin/tikitak/domain/notification/enums/NotificationType.java` (값 추가)
- `src/main/java/kusitms/spin/tikitak/repository/team/TeamMemberRepository.java` (`@Query` 메서드 1개 추가)
- `src/main/java/kusitms/spin/tikitak/service/notification/DailyQuestionOpenedNotificationBatchService.java` (신규)
- `src/main/java/kusitms/spin/tikitak/global/config/DailyQuestionOpenedNotificationProperties.java` (신규)
- `src/main/java/kusitms/spin/tikitak/global/config/PropertiesConfig.java` (등록)
- `src/main/resources/application.yaml`, `src/test/resources/application.yaml` (위 키만)
- 테스트 신규: `src/test/java/kusitms/spin/tikitak/service/notification/DailyQuestionOpenedNotificationBatchServiceTest.java`, `src/test/java/kusitms/spin/tikitak/global/config/DailyQuestionOpenedNotificationPropertiesTest.java`(또는 스케줄 등록을 확인하는 컨텍스트 테스트, 같은 패키지/이름 계열)
- 테스트 수정: `src/test/java/kusitms/spin/tikitak/repository/team/TeamMemberRepositoryIntegrationTest.java`
- 이 계약 파일
- 마이그레이션 없음. 근거: `V22__create_notification.sql:4` `type VARCHAR(50) NOT NULL`, notification에 CHECK 없음(migration grep), `DAILY_QUESTION_OPENED` 21자

## 구현 방식
- 조회: `TeamMemberRepository`에 정적 JPQL `@Query`(QueryDSL 결정 기록 `2026-11-19-querydsl-feed-list.md:39-40` "정적 쿼리는 @Query")
  `select distinct m.id from TeamMember tm join tm.member m join tm.team t where m.status = :memberStatus and tm.status = :teamMemberStatus and t.status = :teamStatus order by m.id` → `List<Long>`. enum은 `@Param`(기존 스타일, `TeamMemberRepository.java:190-204`). `m.status` 조건이 필요한 이유: 탈퇴가 TeamMember 상태를 바꾸지 않음(`MeService.java:184-201`)
- 배치: `@Component @RequiredArgsConstructor @Slf4j`, `ObjectDeleteOutboxRetryBatchService.java:14-43` 골격. **`@Transactional` 없음**(결정 기록 `2026-11-05-external-call-outside-transaction.md`; 걸면 `send`의 NOT_SUPPORTED가 바깥 트랜잭션을 중단만 해 배치 내내 커넥션을 잡음). 스케줄러 스레드에서 받는 사람마다 `try { notificationService.send(id, payload) } catch (Exception e) { 실패 카운트 + log.error(memberId) }`. `notificationExecutor` 사용 안 함
- `MeterRegistry`는 생성자 주입. 시작 로그(대상 수), 종료 로그(대상 수, 성공 수, 실패 수, 소요 ms)
- 조회 자체가 예외를 던지면: 로그·지표 없이 예외 전파(스케줄러가 로그로 남김). 발송 0건

## 의도된 동작 변경
| 경우 | 이전 | 이후 |
|---|---|---|
| 매일 cron 시각(기본 KST 00:00) | 아무 일 없음 | 대상 회원마다 `DAILY_QUESTION_OPENED` 알림 저장 + FCM 푸시(토큰 있을 때). 여러 팀 소속도 1건 |
| 대상 0명 | — | send 호출 없음, 지표(대상 0, duration) 기록, 로그 |
| 한 회원 `send` 예외 | — | 그 회원만 실패로 집계, 나머지 계속 |
| 조회와 발송 사이 탈퇴 | — | `saveForActiveMember`에서 빠지고 warn 로그(`NotificationService.java:64-67`), 성공으로 집계 |
| 알림 목록·안읽음 개수·일괄 읽음 API를 `teamId`와 함께 호출 | — | 이 알림은 `team_id` NULL이라 빠짐(`NotificationRepositoryCustomImpl.java:72-77` scope). `teamId` 없이 부르면 포함. FE 확인 필요 |
| 알림 API/FCM data의 `type` | 5종 | `DAILY_QUESTION_OPENED`가 새로 나올 수 있음 |
| cron에 `-` 설정 | — | 스케줄 등록 안 함 |
| cron 값이 잘못됨 | — | 기동 실패. 근거: implementer가 `ApplicationContextRunner` 임시 프로브(`cron=not a cron`)로 `BeanCreationException: ... Encountered invalid @Scheduled method ... Cron expression must consist of 6 fields` 확인(프로브는 삭제, 운영 부팅으로는 확인 안 함) |

## 완료 조건
- [x] `NotificationType`에 `DAILY_QUESTION_OPENED`가 있고 기존 5개 값은 그대로다
- [x] `TeamMemberRepositoryIntegrationTest`(새 쿼리)
  - 활성 팀 활성 멤버십 활성 회원은 포함
  - 탈퇴(INACTIVE) 회원, 비활성 팀에만 속한 회원, LEFT/BANNED/INVITED 멤버십만 있는 회원은 제외
  - 활성 팀 2개에 속한 회원은 결과에 1번만, 결과는 id 오름차순 (`containsExactly`)
- [x] `DailyQuestionOpenedNotificationBatchServiceTest` (`UnitTest`, mock 리포지토리·NotificationService, `SimpleMeterRegistry`)
  - 받는 사람마다 `send` 1회, payload 전체(type/title/body/actorTeamMemberId null/data 빈 Map)를 기대 객체와 비교 (`NotificationPayload`에 `equals`가 없어 `usingRecursiveComparison().isEqualTo`)
  - 조회 인자가 `(ACTIVE, ACTIVE, ACTIVE)` 
  - 중간 회원 `send` 예외에도 뒤 회원 `send` 호출, `sends{result=success}`/`{result=failure}` 카운트가 정확
  - 대상 0명이면 `verify(notificationService, never()).send(any(), any())`, 대상 수 summary 0 기록
  - `notification.batch.duration`, `recipient.query.duration`, `send.duration` Timer가 각 1회 기록, 태그 `type=DAILY_QUESTION_OPENED`
  - `@Scheduled`의 zone이 `KstDateProvider.KST_ZONE_ID`이고 cron이 Properties 빈을 참조(reflection, `RefreshTokenCleanupBatchServiceTest.java:46-57` 패턴)
- [x] Properties 바인딩 테스트: 값 미설정·빈 문자열이면 `0 0 0 * * *`, 설정하면 그 값(바인딩 경로로, lessons 2026-11-04)
- [x] 스케줄 등록 컨텍스트 테스트(`ApplicationContextRunner`): cron을 설정하면 `ScheduledTaskHolder`에 그 표현식의 cron 작업이 등록되고(SpEL 빈 참조 동작 확인), `-`이면 등록되지 않는다
- [x] 수정한 Java 파일에 Spotless 적용, `./gradlew spotlessCheck` 통과
- [x] `./gradlew test` 통과(ArchUnit, `TikitakApplicationTests` 기동 포함 — `MeterRegistry` 주입 빈이 컨텍스트에 올라가는지 확인)
- [x] `./gradlew integrationTest` 통과(쿼리 추가)

## 커밋 구성
1. `[Feat] 오늘의 질문 공개 알림 일괄 발송 (#214)` — 위 범위 전체 + 이 계약 파일 (한 커밋)

## 하지 말 것
- `NotificationService`/`NotificationPersistService`/`PushSender` 수정, `send()` 반환형 변경, 내부 단계 Timer 추가
- 배치 최적화(페이징, 병렬, 벌크 저장, FCM multicast 묶기), `notificationExecutor`·스케줄러 풀 설정 변경, 분산 락
- 알림 조회 scope(teamId null 포함) 변경
- 범위 밖 리팩터링, 손 포맷, 사람 승인 없는 커밋/푸시

## 남은 리스크
- 스케줄러 풀 크기 1(Boot 4.0.6 `TaskSchedulingProperties$Pool.size` 기본값, researcher javap 확인). 00:00에 `ObjectDeleteOutboxRetryBatchService`(`0 */30 * * * *`)와 같은 스레드를 쓰므로, 이 배치가 30분을 넘기면 00:30 재시도가 밀린다
- 블루/그린 전환 중 두 인스턴스가 함께 뜨면 중복 발송 가능. 자정 배포를 피하는 것으로 둔다
- 서버 재시작 시 이어서 보내기 불가, 받는 사람 전체를 메모리에 읽음, 단일 스레드(의도된 한계, 이슈 본문)
- 자정 푸시가 사용자를 깨울 수 있음(시각은 설정으로 변경 가능)
- 앱이 `DAILY_QUESTION_OPENED`를 처리하는지, 알림 목록을 `teamId`와 함께 부르는지: 저장소 밖이라 **미확인**. PR에 FE 확인 요청
- 잘못된 cron 값은 기동 실패로 이어진다(위 표 근거). 환경 변수 오타가 배포 실패로 드러난다
- 트랜잭션 밖 리포지토리 조회의 커넥션 반납 시점: **미확인**(`open-in-view: false`, `application.yaml:27`)

## 참고
- 조사: `NotificationService.java:56-95`, `NotificationPersistService.java:35-56`, `NotificationEventListener.java:105-111`(실패 격리), `Notification.java:64-75`(data에서 teamId/feedId 추출)
- 결정 기록: `2026-11-05-external-call-outside-transaction.md`, `2026-11-04-kst-boundaries-without-tz-change.md:29`(zone 명시), `2026-11-24-push-sender-interface.md`
- lessons: 같은 결과 분기 `never()` 고정(2026-11-04), 기동 검증은 컨텍스트로(2026-11-04), 조건 객체 전체 비교(2026-11-19)
