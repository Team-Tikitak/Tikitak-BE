# 작업 계약: 푸시 발송을 PushSender 인터페이스로 분리

- **관련 이슈**: #213
- **브랜치**: `Refactor/#213-pushsender-interface` (origin/develop bd15eaf에서 분기)
- **상태**: 완료 (2026-11-24, PR #216)

## 목표
`NotificationService`가 Firebase SDK(`FirebaseMessaging`, `Message`, `BatchResponse`, `SendResponse`, `MessagingErrorCode`, `FirebaseMessagingException`)를 직접 쓰지 않게 한다. 대신 `PushSender` 인터페이스에만 의존하게 해서, #215에서 지연과 실패율을 조절하는 가짜 발송기로 바꿔 끼울 수 있게 한다. 발송 동작(무엇을 보내는지, 어떤 토큰을 지우는지, 어떤 예외가 밖으로 나가는지)은 바꾸지 않는다. 바뀌는 것은 로그뿐이다(아래 표).

## 결정 사항 (2026-11-24 사람 선택)
- **패키지**: `service/notification/push`. `service/auth`의 `LoginCodeStore`/`RedisLoginCodeStore`처럼 인터페이스와 구현체를 service 아래에 둔다. 이렇게 하면 ArchUnit의 service 규칙(C2, C9 등)이 그대로 적용된다.
- **입력**: `send(List<String> tokens, PushMessage message)`.
  - `PushMessage(String title, String body, Map<String, String> data)`
  - data를 만드는 일은 `NotificationService`에 남긴다: `payload.getData()` 전체, 그 위에 `type`, `notificationId`가 null이 아니면 `notificationId`. 지금 `toMessage`(`NotificationService.java:93-106`)와 같은 순서로 넣어서, 같은 키가 있으면 뒤의 값이 이긴다.
- **결과**: `List<PushResult>`. 입력 토큰과 크기와 순서가 같다.
  - `PushResult(PushStatus status, String errorCode, String errorMessage)`
  - `PushStatus`는 `SUCCESS`, `INVALID_TOKEN`, `FAILED` 세 가지다.
  - `INVALID_TOKEN`에는 `MessagingErrorCode.UNREGISTERED` 하나만 대응한다. 지금 삭제 조건(`:267`)과 같다.
  - 결과에 토큰 원문은 넣지 않는다. 토큰별 로그는 `NotificationService`가 인덱스로 `DeviceTokenTarget`과 짝지어 `deviceTokenId`로 남긴다.
- **결과를 아예 받지 못한 경우(A안)**: `sendEach`가 `FirebaseMessagingException`을 던지면 `FcmPushSender`가 잡아 error 로그를 남기고, 모든 토큰을 `FAILED`로 돌려준다.
  - javap로 확인한 사실(firebase-admin 9.4.3):
    - 토큰 하나하나의 실패는 SDK가 `SendResponse.fromException`으로 바꿔 결과에 넣는다(`FirebaseMessaging$2`). 그래서 이 예외는 나오지 않는다.
    - `sendEach`가 이 예외를 던지는 경우는 `InterruptedException`과 `ExecutionException`을 `ErrorCode.CANCELLED`로 감쌀 때뿐이다.
  - 그 밖의 RuntimeException은 지금처럼 밖으로 나가게 둔다. 예를 들어 토큰이 500개를 넘을 때 SDK가 던지는 `IllegalArgumentException`도 그대로 나간다.
- **FCM 미설정**:
  - `FcmPushSender`를 항상 `@Component`로 등록하고 `Optional<FirebaseMessaging>`을 주입받는다.
  - `PushSender.isAvailable()`이 `firebaseMessaging.isPresent()`를 돌려준다.
  - `NotificationService`는 지금 `firebaseMessaging.isPresent()`/`isEmpty()`를 쓰는 자리(`:64`, `:70`)에 `pushSender.isAvailable()`을 쓴다. 조건부 빈은 만들지 않는다.
  - 여러 구현체 중 하나를 고르는 구조(`@Primary`, 프로필)는 #215에서 정한다.

## 범위: 건드려도 되는 파일
- 신규: `src/main/java/kusitms/spin/tikitak/service/notification/push/PushSender.java`, `PushMessage.java`, `PushResult.java`, `PushStatus.java`, `FcmPushSender.java`
- 수정: `src/main/java/kusitms/spin/tikitak/service/notification/NotificationService.java`. 바꾸는 범위는 `send`, `toMessage`, `handleResponses`, `isUnregistered`, 필드, import까지다. 목록, 개수, 읽음 메서드는 건드리지 않는다.
- 수정: `src/test/java/kusitms/spin/tikitak/service/notification/NotificationServiceTest.java`
- 신규: `src/test/java/kusitms/spin/tikitak/service/notification/push/FcmPushSenderTest.java`
- 문서: 이 계약, `docs/decisions/2026-11-24-push-sender-interface.md`(신규), `docs/decisions/README.md`(행 추가)
- `ExternalCallConnectionTest`, `FirebaseConfig`, `NotificationPersistService`, `NotificationEventListener`는 수정하지 않는다. `ExternalCallConnectionTest`가 고치지 않고도 통과하는 것이 실제 빈 연결(`NotificationService` → `FcmPushSender` → `@MockitoBean FirebaseMessaging`)을 검증하는 근거다.

## 구현 방식
`FcmPushSender.send(tokens, message)`:
1. `tokens`가 비어 있으면 SDK를 부르지 않고 빈 리스트를 돌려준다. 호출부가 이미 걸러 주므로 지금 동작과 같고, 빈 리스트를 넣으면 SDK가 `IllegalArgumentException`을 던지는 것을 막는 방어다.
2. `isAvailable()`이 false인데 `send`가 불리면 SDK를 부르지 않고 모든 토큰을 `FAILED(errorCode=null)`로 돌려준다. `NotificationService`는 이 경로를 타지 않는다. C9 규칙 때문에 raw 예외는 던지지 않는다.
3. 토큰마다 `Message`를 만든다. `setToken`, `setNotification(title, body)`, `putAllData(data)`를 쓰고, APNs, Android, priority 설정은 지금처럼 넣지 않는다. 그다음 `sendEach`를 한 번 부른다.
4. 응답을 인덱스대로 `PushResult`로 바꾼다.
   - 성공이면 `SUCCESS`.
   - 실패이고 코드가 `UNREGISTERED`면 `INVALID_TOKEN`.
   - 그 밖의 실패는 `FAILED`. `errorCode`는 `MessagingErrorCode.name()`(null이면 null)이고, `errorMessage`는 예외 메시지다.
5. `FirebaseMessagingException`은 잡아서 error 로그 "FCM 일괄 전송 결과를 받지 못했습니다. tokenCount={}"를 예외와 함께 남기고, 모든 토큰을 `FAILED`(errorCode는 `e.getErrorCode().name()`)로 돌려준다.

`NotificationService.send`:
- 저장, 미설정 확인, 토큰 0개 확인의 흐름과 로그 문구는 그대로 두고, 판단 기준만 `pushSender.isAvailable()`로 바꾼다.
- `PushMessage`를 만들고 `pushSender.send(fcmTokens, message)`를 부른다.
- 결과를 인덱스로 짝지어 처리한다. 지금처럼 `results.size()`만큼 돈다.
  - `INVALID_TOKEN`이면 info 로그(기존 문구, `deviceTokenId`, `errorCode`)를 남기고 삭제 대상에 넣는다.
  - `FAILED`이면 warn 로그를 남긴다(아래 표).
  - 삭제 대상이 있으면 지금처럼 `deleteDeviceTokens`를 한 번 부른다.
- `try/catch(FirebaseMessagingException)`는 없앤다.

## 의도된 동작 변경
| 경우 | 이전 | 이후 |
|---|---|---|
| 회원 없음 또는 탈퇴 | warn 후 return, 저장 안 함 | 동일 |
| FCM 미설정 | `saveForActiveMember(..., false)`로 토큰을 조회하지 않고 저장, warn 후 return | 동일(`isAvailable()=false`) |
| 토큰 0개 | 저장 후 return | 동일 |
| 일부 토큰 `UNREGISTERED` | 해당 토큰만 별도 트랜잭션으로 삭제, info 로그 | 동일 |
| 그 밖의 토큰별 실패 | 삭제 안 함, warn 로그(`deviceTokenId`, `errorCode`, 예외 스택) | 삭제 안 함. warn 로그는 `memberId`, `deviceTokenId`, `errorCode`, `errorMessage`로 바뀐다. **예외 스택은 빠진다**(SDK 타입이 밖으로 나오지 않게 하려는 것) |
| `sendEach`가 `FirebaseMessagingException`을 던짐(interrupt 등) | 삭제 안 함, 예외 안 나감. error 로그 "FCM 알림 전송에 실패했습니다. memberId, type" 1건 | 삭제 안 함, 예외 안 나감. **로그가 바뀐다**: `FcmPushSender`의 error 로그 1건(`tokenCount`, 예외 스택, memberId 없음)에 더해 `NotificationService`의 토큰별 warn(`memberId`, `deviceTokenId`, `errorCode=CANCELLED`) |
| `sendEach`가 RuntimeException을 던짐(토큰 500개 초과 IAE 포함) | 리스너까지 전파, 알림은 커밋됨 | 동일 |
| 저장 트랜잭션 실패, 토큰 삭제 실패 | 리스너까지 전파 | 동일 |
| FCM으로 나가는 메시지(token, notification, data) | `toMessage` 결과 | 필드와 값이 같다 |

- 트랜잭션 경계는 바뀌지 않는다. `send`는 `NOT_SUPPORTED`를 유지하고, 저장과 삭제는 `NotificationPersistService`가 맡는다. `FcmPushSender`에는 `@Transactional`이 없다.
- HTTP 응답 코드는 해당 없다. `send`는 async 리스너에서만 불린다(`NotificationEventListener`).

## 완료 조건
- [x] `NotificationService.java`가 `com.google.firebase`를 하나도 import하지 않는다. main에서 `com.google.firebase.messaging`을 import하는 파일은 `FirebaseConfig`와 `FcmPushSender`뿐이다(grep).
- [x] `PushSender`에 `isAvailable()`과 `send(List<String>, PushMessage)` 두 메서드가 있고, `PushResult`/`PushStatus`가 결정 사항과 같다.
- [x] `FirebaseConfig`, `NotificationPersistService`, `NotificationEventListener`, `ExternalCallConnectionTest`의 diff가 0이다. `NotificationService`의 목록, 개수, 읽음 메서드도 diff가 0이다.
- [x] `FcmPushSenderTest`
  - 성공, `UNREGISTERED`, 그 밖의 코드(`UNAVAILABLE`)가 섞인 응답이 순서대로 `SUCCESS`/`INVALID_TOKEN`/`FAILED`로 바뀐다. `errorCode`도 비교한다.
  - `sendEach`에 넘긴 `Message`의 token, title, body, data를 비교한다. `Message`의 getter(`getToken`, `getData`, `getNotification`)는 package-private이고 `Notification`에는 getter가 없다(javap 확인). 그래서 `ArgumentCaptor`로 잡은 뒤 reflection으로 필드를 읽는 등, 비교 방법은 구현자가 고르고 보고한다.
  - `FirebaseMessagingException`이면 예외가 나가지 않고 모든 토큰이 `FAILED`다.
  - RuntimeException은 밖으로 나간다.
  - 빈 토큰 리스트이거나 미설정이면 `sendEach`를 `never()` 부른다.
  - `isAvailable()`이 Optional의 존재 여부와 같다.
- [x] `NotificationServiceTest`의 send 테스트는 `PushSender` mock으로 바꾸고, 기존 시나리오를 모두 유지한다.
  - 미설정이면 `saveForActiveMember(..., false)`이고 `send`는 `never()`다.
  - `INVALID_TOKEN`이면 해당 id만 삭제한다.
  - `FAILED`이면 삭제를 `never()`한다.
  - `PushSender`가 RuntimeException을 던지면 전파되고, 순서는 저장 → 발송이다.
  - `PushMessage`의 data(`type`, `notificationId`, payload data)는 기대 객체 전체와 `isEqualTo`로 비교한다(lessons :53).
- [x] 수정한 Java 파일에 Spotless를 적용했고 `./gradlew spotlessCheck`가 통과한다.
- [x] `./gradlew test`가 통과한다. ArchUnit, `ExternalCallConnectionTest`, `TikitakApplicationTests`를 포함한다(빈 구성이 바뀌므로, lessons :34, :50). 쿼리와 스키마는 바뀌지 않으므로 integrationTest는 필수가 아니다.

## 커밋 구성
1. `[Refactor] 푸시 발송을 PushSender 인터페이스와 FCM 구현체로 분리 (#213)`: 신규 main 5개, `NotificationService`, 테스트 2개, 이 계약
2. `docs: PushSender 분리 결정 기록 (#213)`: 결정 기록과 README 행

## 하지 말 것
- 가짜 발송기, `NoopPushSender`, `@Primary`/프로필로 구현체를 고르는 구조(#215 범위)
- 500개 분할, 재시도, 비동기 발송(`sendEachAsync`) 등 성능 개선(측정 뒤 별도 이슈)
- `INVALID_ARGUMENT`, `SENDER_ID_MISMATCH` 같은 다른 코드를 무효 토큰으로 넓히는 것
- `FirebaseConfig`의 빈 조건 변경
- 범위 밖 리팩터링, 손으로 하는 포맷 정리, 사람 승인 없는 커밋과 푸시

## 참고
- `docs/decisions/2026-11-05-external-call-outside-transaction.md`: 외부 호출을 트랜잭션 밖에서 하는 원칙(유지)
- `docs/contracts/2026-11-05-external-call-tx-separation.md`: 알림 경로 동작 표
- lessons-log :33(`never()`로 분기 고정), :34/:50(빈 구성 변경은 컨텍스트 기동으로 확인), :53(조건 객체는 전체 비교)
- 미확인: FCM 응답 순서가 입력 순서와 같다는 가정. 기존 코드도 같은 가정을 쓰고 있어 이번 변경으로 위험이 늘지 않는다. SDK는 `ApiFutures.allAsList`로 입력 순서대로 모은다(javap `FirebaseMessaging.sendEachOpAsync` 바이트코드 오프셋 116). 이 SDK 내부 순서는 확인했지만, FCM 서버 쪽 순서와는 관계가 없다.
