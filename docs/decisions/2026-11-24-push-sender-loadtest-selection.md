# 측정용 가짜 푸시 발송기는 `loadtest & !prod` 프로필 + `@Primary`로 고르고, 배치는 같은 프로필의 ApplicationRunner로 시작한다

- **날짜**: 2026-11-24
- **관련 이슈**: #215 (선행 #213, #214)
- **상태**: 결정됨 (2026-11-24 사람 결정, 2026-11-25 계약 승인)
- **계약**: [2026-11-24-bulk-notification-baseline](../contracts/2026-11-24-bulk-notification-baseline.md)

## 상황 (Context)
- 오늘의 질문 공개 알림 배치(#214, `DailyQuestionOpenedNotificationBatchService`)를 받는 사람 1만/10만/100만 규모로 로컬에서 돌려 개선 전 기준선을 재야 한다. 실제 FCM은 부를 수 없으므로 지연과 실패율을 조절하는 가짜 발송기가 필요하다.
- #213에서 발송을 `PushSender` 인터페이스 뒤로 옮기면서, 여러 구현체 중 하나를 고르는 구조는 가짜 발송기가 실제로 들어오는 #215에서 정하기로 했다([2026-11-24-push-sender-interface](2026-11-24-push-sender-interface.md)).
- 배치는 cron(`notification.daily-question-opened.cron`)으로만 시작한다. 측정 회차마다 바로 시작시킬 방법이 필요하다.

## 검토한 선택지 (Options considered)
**가짜 발송기 등록**
- a. (채택) 가짜에 `@Profile("loadtest & !prod") @Primary`. `FcmPushSender`는 바꾸지 않는다.
- b. (기각) `FcmPushSender`에 `@Profile("!loadtest")`를 붙이고 가짜에 `@Profile("loadtest")`.
- c. (기각) `@ConditionalOnProperty`로 설정 값에 따라 가짜를 켠다.

**발송 시작**
- a. (채택) 같은 프로필 전용 `ApplicationRunner`. `loadtest.daily-question-opened.run-on-startup=true`일 때만 기동 직후 1회 실행.
- b. (기각) 측정용 컨트롤러 엔드포인트.
- c. (기각) 회차마다 cron env를 몇 분 뒤 시각으로 맞춤.

## 결정 (Decision)
- `service/notification/push/LoadTestPushSender`
  - `@Component @Profile("loadtest & !prod") @Primary`. 프로필 식은 `LoadTestConfig.PROFILE` 상수 하나로 모든 측정 전용 빈이 같이 쓴다.
  - `isAvailable()`은 항상 true. 빈 입력이면 `List.of()`.
  - `send` 호출(회원 한 명)마다 `loadtest.push.delay-ms`만큼 한 번 쉰다. FCM `sendEach` 한 번의 왕복을 흉내 낸다.
  - 토큰마다 (토큰 문자열 + `seed`) 해시로 [0, 1) 값을 만들어 `invalid-token-rate` 미만이면 `INVALID_TOKEN`, `invalid-token-rate + failure-rate` 미만이면 `FAILED`, 나머지는 `SUCCESS`. 같은 입력이면 같은 결과이고, 결과는 입력과 크기·순서가 같다.
  - 쉬는 중 인터럽트되면 플래그를 복원하고 전부 `FAILED`를 돌려준다(service 패키지라 raw 런타임 예외를 만들지 않는다, ArchUnit C9).
  - 토큰별 결과를 Micrometer 카운터 `loadtest.push.results{status}`로 센다. 배치 지표 `notification.batch.sends`는 토큰별 `FAILED`도 success로 세므로 실패율은 이 카운터로 본다.
- `global/config/LoadTestProperties`(prefix `loadtest`) + `LoadTestConfig`(`@Profile("loadtest & !prod") @EnableConfigurationProperties`)
  - `push.delay-ms`(기본 0, 음수 불가), `push.failure-rate`·`push.invalid-token-rate`(기본 0, 각 0~1, 합 ≤ 1), `push.seed`(기본 0), `daily-question-opened.run-on-startup`(기본 false).
  - 검증은 record compact constructor에서 한다. 값이 잘못되면 바인딩이 실패해 기동이 멈춘다.
  - `PropertiesConfig`에 넣지 않아, 다른 프로필에는 Properties 빈도 없다.
- `service/notification/LoadTestDailyQuestionOpenedRunner`
  - 같은 프로필의 `ApplicationRunner`. `run-on-startup=true`면 `sendDailyQuestionOpenedNotifications()`를 트랜잭션 없이 한 번 부르고 시작·종료 로그를 남긴다.
  - cron은 건드리지 않는다. 측정 env에서 `DAILY_QUESTION_OPENED_NOTIFICATION_CRON=-`로 끈다.
- `loadtest` 프로필에서는 배치뿐 아니라 이벤트 리스너 알림(피드·태그·댓글 등, 모두 `NotificationService.send` 경유)도 가짜 발송기로 간다. 측정 전용이라 의도한 것이다.

## 이유 (Rationale)
- **등록 a**: `FcmPushSender`의 diff가 0이라 운영과 기존 테스트의 등록 조건이 그대로다. `prod`가 함께 켜지면 등록되지 않는다.
- **등록 b 기각**: `FcmPushSender`의 등록 조건이 바뀌어 `ExternalCallConnectionTest`가 타는 Fcm 등록 경로까지 영향을 받는다([2026-11-24-push-sender-interface](2026-11-24-push-sender-interface.md) "미설정" 이유).
- **등록 c 기각**: 프로퍼티는 env 하나로 켜지므로 운영 env 실수로 가짜가 켜질 수 있다. 프로필 식에는 `!prod`를 넣을 수 있다.
- **시작 a**: 측정 스크립트가 앱 기동만 하면 되고, 보안 설정을 바꾸지 않는다.
- **시작 b 기각**: 호출하려면 JWT 서명이 필요하거나 `SecurityConfig` 전역 화이트리스트를 바꿔야 한다.
- **시작 c 기각**: 회차마다 cron 시각을 맞춰야 하고, 기동 시간에 따라 시작 시각이 어긋난다.
- 결과를 난수가 아니라 해시로 정해서 같은 시드 데이터면 회차마다 같은 토큰이 같은 결과를 낸다. 번호만 다른 토큰(`loadtest-<member_id>-<n>`)도 고르게 퍼지도록 FNV-1a 64비트 해시에 SplitMix64 마무리 함수를 쓴다(토큰 10,000개에서 설정 비율 ±2%p를 단위 테스트로 확인).

## 검증
- `LoadTestPushSenderTest`: 크기·순서, 결정성, 빈 입력, rate 0 → 전부 `SUCCESS`, invalid 1 → 전부 `INVALID_TOKEN`, 10,000개 비율 ±2%p, 인터럽트 → 전부 `FAILED`·플래그 복원, 카운터 값.
- `LoadTestConfigTest`(`ApplicationContextRunner`): 프로필 없음 → 측정 전용 빈 0개 / `loadtest` → 등록되고 `PushSender` 단일 조회가 `LoadTestPushSender`(`FcmPushSender`도 함께 등록한 상태) / `loadtest,prod` → 0개 / 합 > 1, 음수 지연, 범위 밖 rate → 기동 실패.
- `LoadTestDailyQuestionOpenedRunnerTest`: `run-on-startup=false` → 배치 `never()`, true → 1회.

## 트레이드오프 / 남은 리스크
- 운영의 `SPRING_PROFILES_ACTIVE` 실제 값은 GitOps 저장소에 있어 **미확인**이다. `!prod` 조건은 운영이 `prod` 프로필을 쓴다는 전제이고, 이는 `application-prod.yaml`이 있다는 점으로 추정한 것이다. 운영이 `prod` 없이 `loadtest`를 켜는 경우는 막지 못한다.
- 가짜 발송기의 지연은 회원 단위 한 번이다. 실제 FCM의 토큰 수별 지연, 동시성 제한, 전송 한도는 흉내 내지 않는다.
- 배치 지표 `notification.batch.sends`는 토큰별 실패를 구분하지 않는다. 측정 결과의 실패율은 `loadtest.push.results`로만 읽는다.
- `handleResults`가 결과 길이를 검증하지 않는 문제는 이번 범위 밖이다(별도 이슈). 가짜 발송기는 크기·순서 규약을 테스트로 고정했다.
