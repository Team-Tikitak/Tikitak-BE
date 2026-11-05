# 작업 계약: 외부 API 호출 트랜잭션 분리 및 카카오 OAuth 타임아웃

- **관련 이슈**: #161
- **브랜치**: `Fix/#161-external-api-tx-separation` (Orca worktree, develop 09fcdf4 기준)
- **상태**: 승인됨 (2026-11-05)

## 목표
- OAuth 로그인, 알림 전송, 업로드 완료에서 외부 네트워크 호출(카카오/구글 API, FCM, R2 `headObject`)이 DB 트랜잭션·비관적 락 밖에서 실행되게 한다.
- 카카오 OAuth HTTP 호출에 Google과 같은 타임아웃(connect 3s, request 5s)을 건다.

## 항목별 작업 (조사: 2026-11-05 researcher)

트랜잭션 분리는 **패키지마다 별도 빈**으로 한다(`ObjectDeleteOutboxProcessor` 선례, ArchUnit C2). 같은 클래스 안 `this.` 호출로 `@Transactional`을 나누지 않는다. 아래 새 빈 이름은 가칭이며, 구현 중 바꾸면 보고한다.

| 항목 | 작업 |
|---|---|
| 1. 카카오 타임아웃 | `KakaoOAuthService`의 `HttpClient`를 builder + `connectTimeout(3s)`로 만들고, 두 요청에 `.timeout(5s)`를 건다. 값은 Google처럼 `private static final` 상수로 둔다(Properties 바인딩 안 함). IO/타임아웃 실패 로그 문구를 Google처럼 "request failed"로 고친다(현재 "response parsing failed"). |
| 2. OAuth 로그인 | `AuthService.loginWithOAuth`(4·5인자), `issueAppLoginCode`(4·5인자)에서 `@Transactional`을 뺀다. 순서: Redis state consume → 외부 호출(Google/Kakao/Apple) → 새 빈 `service/auth/OAuthMemberLoginService`의 `@Transactional` 메서드에서 회원 조회/생성 + `updateSocialProfile` + `resolveActiveTeamId` (+ 웹 경로만 `issueToken`, 앱 경로는 기존처럼 토큰 미발급) → (앱) 커밋 후 트랜잭션 밖에서 `loginCodeStore.save`. 새 빈 메서드는 웹용·앱용 2개. |
| 3. 알림 전송 | `NotificationService.send()`는 `@Transactional(propagation = NOT_SUPPORTED)`를 명시한다(클래스 수준 readOnly 트랜잭션을 물려받지 않게). 순서: 새 빈 `service/notification/NotificationPersistService`의 트랜잭션에서 회원 확인 + 알림 저장 + 토큰 조회(토큰 ID·값만 반환) → FCM `sendEach`(트랜잭션 없음) → UNREGISTERED 토큰은 같은 새 빈의 별도 트랜잭션에서 ID 기반으로 삭제. |
| 4. 업로드 완료 | `MediaService.completeUpload`에서 `@Transactional`을 뺀다. 순서: 새 빈 `service/media/MediaUploadCompletionService`의 readOnly 트랜잭션에서 락 없이 upload·media를 읽고 기존 검증을 수행, `headObject`에 필요한 값(key, contentType, size)만 반환 → `headObject` 전부(트랜잭션 없음) → 같은 새 빈의 `@Transactional(noRollbackFor = ExpiredUploadException.class)` 메서드에서 지금처럼 PESSIMISTIC_WRITE 락을 잡고 **모든 검증을 다시 수행**(존재, 소유자, PENDING, 만료, media 수·publicId, 각 media PENDING, 타입·크기)한 뒤 상태 전환. 사전 단계에서 만료를 발견하면 `headObject`를 건너뛰고 락 단계로 넘겨 `expire()` 저장과 MEDIA014가 지금과 같게 나오게 한다. |

## 의도된 동작 변경

| 경우 | 이전 | 이후 |
|---|---|---|
| 카카오 API 응답 지연 | 무한 대기, 그동안 DB 커넥션 점유 | connect 3s / request 5s 후 AUTH104(401), 커넥션 점유 없음 |
| 앱 로그인(`issueAppLoginCode`)에서 Redis login code 저장 실패 | 회원 생성까지 롤백, AUTH105 | 회원 생성·프로필 갱신은 커밋된 채로 남고 응답은 AUTH105(같음). 앱 경로는 원래 토큰을 발급하지 않음(발급은 `exchangeLoginCode`). 다음 로그인은 기존 회원으로 처리됨 |
| OAuth 로그인 중 트랜잭션 시작 실패(커넥션 획득 타임아웃 등) 또는 커밋 실패 | COMMON004(500). 시작 실패는 state 소비 전 | AUTH105(500). 트랜잭션이 `AuthService`의 `catch (Exception)` 안으로 들어감. 시작 실패는 state 소비·외부 호출 이후 (2026-11-05 사용자 허용) |
| 앱 로그인에서 DB 커밋 실패 | Redis에 login code가 남을 수 있음 | 커밋 실패 시 login code를 저장하지 않음 |
| FCM이 `FirebaseMessagingException` 외 RuntimeException을 던짐 | 알림 레코드 롤백(남지 않음) | 알림 레코드가 남음. 리스너는 지금처럼 catch 후 로그 |
| 무효 토큰 삭제 실패 | 알림 레코드까지 롤백 | 알림 레코드는 남고 토큰 삭제만 실패 |
| 푸시 수신 직후 알림 목록 조회 | `send()` 자체 트랜잭션의 커밋 전에 푸시가 나감(리스너의 AFTER_COMMIT은 원 트랜잭션 기준). FCM 응답 직후 커밋되므로 목록에 안 보이는 창은 수십~수백 ms | 알림 커밋 후 푸시라 창 없음 (부수 효과) |

- 그 밖의 경로(정상 로그인, OAuth 외부 호출 실패, 회원 생성 후 토큰 발급 실패 시 롤백, 업로드 완료의 모든 에러 코드 MEDIA012/013/014/017과 롤백·만료 저장 동작)는 응답과 DB 결과가 바뀌지 않는다.

## 범위: 건드려도 되는 파일
- main
  - `service/auth/KakaoOAuthService.java`, `service/auth/AuthService.java`, `service/auth/OAuthMemberLoginService.java`(신규)
  - `service/notification/NotificationService.java`, `service/notification/NotificationPersistService.java`(신규)
  - `repository/notification/` 의 device token 리포지토리 (ID 기반 삭제 메서드가 필요할 때만)
  - `service/media/MediaService.java`, `service/media/MediaUploadCompletionService.java`(신규), headObject 입력용 값 record가 필요하면 같은 패키지에 신규 1개
- test
  - `service/auth/AuthServiceTest.java`, `property/auth/AuthServicePropertyTest.java`, 새 빈의 단위 테스트
  - `service/notification/NotificationServiceTest.java`, 새 빈의 단위 테스트
  - `service/media/MediaServiceTest.java`, 새 빈의 단위 테스트
  - 신규: 카카오 타임아웃 테스트, 커넥션 미점유 검증용 `@SpringBootTest` 테스트 클래스 1개(H2, `test` 태스크에서 실행)
- docs: `docs/decisions/2026-11-05-external-call-outside-transaction.md`(신규), `docs/decisions/README.md` 인덱스

## 완료 조건
- [ ] `./gradlew test` 통과 (ArchUnit 포함), `./gradlew spotlessCheck` 통과
- [ ] `./gradlew integrationTest` 통과 (리포지토리 메서드를 추가했다면 필수)
- [ ] `AuthService`·`MediaService`의 대상 메서드에 `@Transactional`이 없고, `NotificationService.send()`에는 `NOT_SUPPORTED`가 명시돼 있다. 새 트랜잭션 메서드는 모두 다른 빈에 있다(같은 클래스 `this.` 호출로 트랜잭션을 나누지 않음).
- [ ] 커넥션 미점유 테스트(`@SpringBootTest`, 테스트 클래스에 `@Transactional` 없음): OAuth `getUserInfo`, FCM `sendEach`, R2 `headObject`를 mock으로 바꾸고, answer 안에서 `TransactionSynchronizationManager.isActualTransactionActive() == false`와 Hikari `activeConnections == 0`을 단언한다. 세 경로 모두 있고, answer가 실제로 호출됐는지도 verify한다.
- [ ] 같은 테스트에서 분리 후에도 DB 결과가 맞는지 확인: 로그인 후 회원과 refresh token 저장, 알림 저장, 업로드 완료 후 media UPLOADED.
- [ ] 카카오 타임아웃: `HttpClient.connectTimeout()`이 3s이고 두 요청의 `timeout()`이 5s임을 테스트로 확인한다.
- [ ] 회귀 테스트
  - 회원 생성 후 토큰 발급이 실패하면 회원이 롤백된다(같은 트랜잭션 유지).
  - 업로드 완료: 사전 단계 통과 후 락 단계에서 media가 PENDING이 아니면 MEDIA014로 끝나고 상태 전환을 하지 않는다(`never()`로 사전 단계 분기와 구분).
  - 업로드 완료: 만료된 upload는 `headObject`를 부르지 않고(`never()`), `expire()`가 커밋되며 MEDIA014.
  - 알림: FCM이 RuntimeException을 던져도 저장 트랜잭션은 이미 커밋돼 있다(저장 빈 호출 후 예외 전파 확인).
- [ ] 위 "의도된 동작 변경" 표 외의 응답 변경이 없다(기존 단위 테스트의 기대 에러 코드를 바꾸지 않는다).

## 하지 말 것
- 범위 밖 리팩터링, 손 포맷 정리 (수정한 파일 spotlessApply 결과는 허용)
- `TransactionTemplate` 사용, 같은 클래스 안 `@Transactional` 메서드 self-invocation
- Apple JWKS·FCM·S3Client 타임아웃, `deleteUnusedMedia`/정리 배치의 락 안 R2 호출, 회원 unique 제약 (후속 이슈)
- 타임아웃 값의 Properties 바인딩, Google/Place 코드 변경
- `application*.yaml` 변경, 마이그레이션 추가
- 새 ArchUnit 예외 추가
- 사람 승인 없이 커밋/푸시

## 커밋 구성 (한 PR, 항목별 커밋, 각 커밋 승인)
1. 카카오 타임아웃 + 테스트
2. OAuth 로그인 분리 + 커넥션 미점유 테스트 클래스 신설(OAuth 경로)
3. 알림 전송 분리 + 미점유 테스트에 FCM 경로 추가
4. 업로드 완료 분리 + 미점유 테스트에 R2 경로 추가 + 결정 기록

## 참고
- 결정 (2026-11-05 사용자 선택): 별도 빈 방식, 알림 동작 변경 허용, `@SpringBootTest`+H2 검증, 범위 밖 항목은 후속 이슈
- 알림 분리를 유지하기로 한 근거 (2026-11-05): `notificationExecutor`(core 2, max 4) 크기만큼 커넥션이 FCM 왕복 동안 묶이고(풀 10의 20~40%), `FirebaseOptions` 타임아웃이 없음. 효과는 세 항목 중 가장 작다.
- 사전 확인: `TikitakApplicationTests`(`@SpringBootTest`)가 `./gradlew test`에서 H2로 기동·통과함 (2026-11-05)
- 이슈 완료 조건의 "k6 재실행"은 코드 작업이 아니라서 이 계약 밖이다. 머지·배포 후 따로 측정한다.
- 남은 리스크: R2 S3Client에 타임아웃이 없어서 커넥션은 잡지 않아도 업로드 완료 요청 자체는 오래 걸릴 수 있다(후속 이슈).
- `docs/decisions/2026-09-21-mediaupload-orphan-check-no-status-transition.md`: upload 만료 전이는 `completeUpload` 안에서만 일어난다. `expire()` 저장을 잃지 않을 것.
- `docs/lessons-log.md`: 같은 에러 코드 분기는 `never()`로 고정, 프록시·기동 동작은 컨텍스트 테스트로 확인, 생성자 직접 호출 테스트(`AuthServicePropertyTest`, `NotificationServiceTest`, `MediaServiceTest`)는 의존성 변경 시 함께 수정.
- 조사에서 확인 못 한 것: `TikitakApplicationTests`(`@SpringBootTest`)가 현재 `test` 태스크에서 기동하는지, Boot 4의 `@MockitoBean` 사용 가능 여부. 커밋 2 착수 전에 확인한다.
