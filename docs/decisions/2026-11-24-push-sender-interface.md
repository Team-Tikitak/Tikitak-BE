# 푸시 발송은 PushSender 인터페이스 뒤에 두고, FCM 구현체가 SDK 타입과 미설정 여부를 감춘다

- **날짜**: 2026-11-24
- **관련 이슈**: #213 (후속 #214, #215)
- **상태**: 결정됨 (2026-11-24 계약 승인)
- **계약**: [2026-11-24-push-sender-interface](../contracts/2026-11-24-push-sender-interface.md)

## 상황 (Context)
- 대량 알림 발송 성능을 측정하려면(#215) 실제 FCM 대신 지연과 실패율을 조절하는 가짜 발송기를 붙여야 한다.
- 그런데 `NotificationService`가 `Optional<FirebaseMessaging>`을 직접 받아 `sendEach`를 부르고, `MessagingErrorCode.UNREGISTERED`로 토큰 삭제를 판단하고 있었다. 바꿔 끼울 지점이 없었다.
- FCM이 설정되지 않은 환경(로컬, 테스트)에서는 `FirebaseConfig`가 조건부라 빈이 없다. 이때는 저장만 하고, 토큰 조회와 발송은 건너뛴다(`saveForActiveMember(..., false)`).

## 검토한 선택지 (Options considered)
- **위치**
  1. `service/notification/push` (채택)
  2. (기각) `global/push`
  3. (기각) 새 `infra/` 패키지
- **입력**
  1. 토큰 목록 + 알림 도메인을 모르는 `PushMessage(title, body, data)` (채택)
  2. (기각) `NotificationPayload` + `notificationId`를 그대로 넘김
- **결과를 아예 받지 못한 경우** (`sendEach`가 `FirebaseMessagingException`을 던짐)
  1. 구현체가 잡아 모든 토큰을 `FAILED`로 돌려줌 (채택)
  2. (기각) 전용 checked 예외로 감싸서 던짐
- **FCM 미설정**
  1. 구현체는 항상 등록하고 `Optional<FirebaseMessaging>`을 받음. 미설정 여부는 `isAvailable()`로 알림 (채택)
  2. (기각) `NotificationService`가 `Optional<PushSender>`를 받고, 구현체는 조건부 빈으로 등록
  3. (기각) `NoopPushSender`와 `FcmPushSender` 중 하나를 고름

## 결정 (Decision)
- `PushSender`
  - 메서드: `isAvailable()`, `send(List<String> tokens, PushMessage)`
  - 결과: `List<PushResult(status, errorCode, errorMessage)>`. 입력 토큰과 크기와 순서가 같다.
  - status는 `SUCCESS`, `INVALID_TOKEN`, `FAILED` 셋이다. 결과에 토큰 원문은 넣지 않는다.
- `FcmPushSender`
  - `UNREGISTERED` 하나만 `INVALID_TOKEN`으로 바꾼다. 지금 삭제 조건과 같다.
  - `FirebaseMessagingException`은 잡아서 error 로그를 남기고 모든 토큰을 `FAILED`로 돌려준다. 그 밖의 RuntimeException은 밖으로 나간다.
- `NotificationService`
  - data를 만든다: payload data → `type` → `notificationId`.
  - `INVALID_TOKEN` 결과만 보고 토큰을 지운다.
  - 토큰별 로그는 인덱스로 `deviceTokenId`와 짝지어 남긴다.

## 이유 (Rationale)
- **위치**
  - `service/auth`의 `LoginCodeStore`/`RedisLoginCodeStore`에 인터페이스와 구현체를 service 아래 두는 선례가 있다.
  - ArchUnit의 service 규칙(C2 트랜잭션 위치, C9 raw 예외 금지)이 그대로 적용된다.
  - `global`은 공통 처리용이라 성격이 맞지 않는다. `infra`는 선례가 없고 CLAUDE.md의 Architecture 절을 고쳐야 한다.
- **입력**: data 조립 규칙(`type`, `notificationId`)이 구현체마다 중복되지 않는다. 그래서 #215의 가짜 발송기는 결과만 만들면 된다.
- **결과를 받지 못한 경우**
  - javap(firebase-admin 9.4.3)로 SDK 동작을 확인했다.
    - 토큰별 실패는 SDK가 `SendResponse.fromException`으로 바꿔 결과에 넣는다.
    - `sendEach`가 예외를 던지는 경우는 `InterruptedException`/`ExecutionException`을 `CANCELLED`로 감쌀 때뿐이다.
  - 즉 실제로는 드문 경로다. 이 경로를 결과 값으로 표현하면 호출부가 결과 한 가지만 다루면 된다.
  - 전용 예외 안은 로그를 지금과 똑같이 유지할 수 있다. 하지만 타입이 하나 늘고, 호출부가 결과와 예외 두 경로를 모두 처리해야 한다.
- **미설정**
  - 미설정이면 토큰을 조회하지 않는 동작을 지키려면, 저장 전에 발송할 수 있는지 알아야 한다. 그래서 어느 안이든 `isAvailable()` 같은 질의가 필요하다.
  - 조건부 빈(`@ConditionalOnBean`)은 스캔되는 컴포넌트에서 평가 순서를 믿기 어렵다(Spring Boot 문서 기준, 실행 확인은 안 함).
  - 조건부 빈이면 `ExternalCallConnectionTest`의 `@MockitoBean FirebaseMessaging`과 어긋날 위험이 있다.
  - 채택안에서는 `ExternalCallConnectionTest`가 수정 없이 통과했다(2026-11-24 `./gradlew test --rerun-tasks`).
  - 여러 구현체 중 하나를 고르는 구조는 가짜 발송기가 실제로 들어오는 #215에서 정한다.

## 의도된 동작 변경
발송 내용, 토큰 삭제 조건, 예외 전파, 트랜잭션 경계는 바뀌지 않았다. 로그만 바뀐다(계약 표 참고).
- **토큰별 실패 warn**: `memberId`와 `errorMessage`가 추가되고, 예외 스택은 빠진다.
- **결과를 받지 못한 경우**:
  - 이전: `NotificationService`의 error 로그 1건(`memberId` 포함)
  - 이후: `FcmPushSender`의 error 로그 1건(`tokenCount`, 예외 스택)과, 토큰별 warn(`memberId`, `errorCode=CANCELLED`)

## 트레이드오프 / 남은 리스크
- 토큰별 실패 로그에 예외 스택이 남지 않는다. 원인 추적은 `errorCode`와 `errorMessage`로 한다.
- `PushResult`를 인덱스로 토큰과 짝짓는다. 구현체가 입력 순서를 지키지 않으면 엉뚱한 토큰을 지울 수 있다.
  - FCM 구현체는 SDK가 `ApiFutures.allAsList`로 입력 순서대로 모으므로 안전하다(javap 확인).
  - 새 구현체(#215 가짜 발송기 등)는 이 규약을 지켜야 한다.
- 토큰 500개 초과 시 SDK가 `IllegalArgumentException`을 던지는 동작, 재시도 없음은 이전과 같다. 측정(#215) 뒤 병목별 이슈에서 다룬다.
