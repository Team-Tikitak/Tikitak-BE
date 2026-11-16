# 작업 계약: Apple JWKS·FCM·R2 S3Client 외부 호출 타임아웃

- **관련 이슈**: #179
- **브랜치**: `Fix/#179-external-call-timeout` (develop에서 분기)
- **상태**: 승인됨 (2026-11-15)

## 목표
외부 호출 세 곳이 외부 응답을 무한정 기다리지 않게 상한을 둔다. Apple은 JWKS 캐시가 요청 사이에 유지되게 한다. 경로별 최종 응답(ErrorCode·상태 전이)은 바꾸지 않는다.

## 조사로 확인한 현재 기본값 (오케스트레이터가 Gradle 캐시 jar를 `javap`로 확인)
| 대상 | 현재 | 근거 |
|---|---|---|
| Apple JWKS | connect/read **500ms**가 이미 걸려 있다. 다만 요청마다 디코더를 새로 만들어 JWKS를 매번 조회한다 | `spring-security-oauth2-jose-7.0.5.jar` `NimbusJwtDecoder$RestTemplateWithNimbusDefaultTimeouts`(`setConnectTimeout(500)`, `setReadTimeout(500)`), 기본 cache는 `NoOpCache`. `nimbus-jose-jwt-10.4` `JWKSourceBuilder.DEFAULT_CACHE_TIME_TO_LIVE = 300000`. 생성 위치는 `AppleOAuthService.java:71` |
| FCM | connect/read/write **0(무제한)** | `firebase-admin-9.4.3.jar` `FirebaseOptions$Builder()`는 타임아웃 필드를 초기화하지 않는다(기본 0). `FirebaseRequestInitializer$TimeoutInitializer.initialize`가 그 값을 그대로 `HttpRequest.setConnect/Read/WriteTimeout`에 넣는다. 설정 위치 `FirebaseConfig.java:26-28` |
| R2 S3Client | connect 2s, read 30s, write 30s, 커넥션 획득 10s. 호출 전체 상한 없음 | `http-client-spi-2.34.0.jar` `SdkHttpConfigurationOption` 정적 초기화(`ofSeconds(30)`, `30`, `2`, `10`). `ClientOverrideConfiguration.apiCallTimeout()`/`apiCallAttemptTimeout()`는 `Optional`이고 기본 비어 있음(미확인: 빈 값이 "상한 없음"이라는 건 SDK 문서 지식). 설정 위치 `R2Config.java:24-31` |

## 결정 (2026-11-15 사용자 선택)
- 값은 **클래스 상수**(`private static final`)로 둔다. #161 Kakao·Google 선례(`docs/contracts/2026-11-05-external-call-tx-separation.md:17`)와 같다. Properties 바인딩은 하지 않는다.
- Apple: **디코더를 한 번만 만들어 재사용**하고, 타임아웃은 기본 500ms를 그대로 둔다.
- FCM: connect **3s** / read **10s** / write **10s**.
- R2 `S3Client`: `ClientOverrideConfiguration`으로 `apiCallAttemptTimeout` **5s**, `apiCallTimeout` **15s**. 의존성은 추가하지 않는다. `S3Presigner`는 바꾸지 않는다(로컬 서명이라 네트워크 호출 없음. SDK 설계 지식 기준, 미확인).

## 범위: 건드려도 되는 파일
- `src/main/java/kusitms/spin/tikitak/service/auth/AppleOAuthService.java`
- `src/main/java/kusitms/spin/tikitak/global/config/FirebaseConfig.java`
- `src/main/java/kusitms/spin/tikitak/global/config/R2Config.java`
- 신규 `src/test/java/kusitms/spin/tikitak/service/auth/AppleOAuthServiceTest.java`
- 신규 `src/test/java/kusitms/spin/tikitak/global/config/FirebaseConfigTest.java`
- 신규 `src/test/java/kusitms/spin/tikitak/global/config/R2ConfigTest.java`
- `docs/decisions/2026-11-05-external-call-outside-transaction.md`, `docs/decisions/2026-11-12-media-delete-r2-after-commit.md`: "남은 리스크"의 #179 문장만 고친다

## 항목별 작업
1. **Apple**: `JwtDecoder`를 필드로 바꾸고 빈 생성 시 한 번만 `NimbusJwtDecoder.withJwkSetUri(JWK_SET_URI).build()`로 만든다. 테스트에서 mock 디코더를 넣을 수 있게 package-private 생성자(`AuthProperties`, `JwtDecoder`)를 둔다. Spring이 쓰는 생성자는 `AuthProperties`만 받는다. 검증 로직과 catch 구조(`:89-97`)는 그대로 둔다.
2. **FCM**: options 생성을 package-private static 메서드(예: `buildOptions(GoogleCredentials)`)로 분리하고 `setConnectTimeout(3000)`, `setReadTimeout(10000)`, `setWriteTimeout(10000)`을 상수로 넣는다. 기존 DEFAULT 앱 재사용 분기(`:30-33`)는 그대로 둔다.
3. **R2**: `S3Client` 빌더에 `overrideConfiguration(ClientOverrideConfiguration.builder().apiCallAttemptTimeout(5s).apiCallTimeout(15s).build())`를 추가한다.
4. **문서**: 결정 기록 두 곳의 "타임아웃 없음(#179)" 문장을 "#179에서 해소: …값…"으로 고친다.

## 의도된 동작 변경
**응답 코드 변경: 없음.** 바뀌는 것은 최대 대기 시간과 Apple JWKS 조회 횟수뿐이다. 타임아웃 예외가 나도 상위 `catch (Exception)`이 받아 지금과 같은 응답으로 끝난다.

| 외부 호출 | 진입점 | 이전 | 이후 | 근거 |
|---|---|---|---|---|
| Apple JWKS | POST `/api/v1/auth/oauth/apple/callback` 웹 / 앱(`oauthMode=app`) | 실패 시 AUTH104 401. 로그인마다 JWKS 조회, 각 조회 최대 약 1s(500+500ms) | 같은 AUTH104 401. JWKS는 인스턴스 캐시(TTL 5분)가 유지되는 동안 재조회하지 않는다 | `AppleOAuthService.java:89-97`(JwtException·Exception → OAuthAuthenticationException), `AuthService.java:83-90, 112-119` (researcher 보고) |
| FCM `sendEach` | 알림 이벤트 리스너 3개(`@Async`, AFTER_COMMIT) | 무한 대기 가능. 실패하면 error 로그 | connect 3s / read 10s / write 10s 상한에서 끊기고(재시도 정책은 미확인), 실패는 같은 로그 | `NotificationService.java:60-90`, `NotificationEventListener.java` catch(Exception) |
| R2 `headObject` | POST 업로드 완료 | `S3Exception` 아닌 실패 → MEDIA017 500 | 같음. `ApiCall(Attempt)TimeoutException`은 `SdkClientException` 하위라 `catch (Exception)` → MEDIA017 | `MediaService.java:292-295`, `javap` 클래스 계층 |
| R2 `deleteObject` | DELETE 미디어 API / 04:00 정리 배치 / 아웃박스(04:30 afterCommit, 30분 재시도) | MEDIA010 → 각각 200+warn / error 로그·다음 날 재시도 / `markFailed` | 같음 | `MediaService.java:383-388`, `ObjectDeleteOutboxProcessor.java:65` |
| R2 presign | POST 업로드 URL 발급 | 네트워크 호출 없음(미확인) | 변경 없음 | `S3Presigner`는 수정하지 않음 |

트랜잭션 경계·롤백 규칙은 바꾸지 않는다.

## 완료 조건
- [ ] `./gradlew spotlessCheck`, `./gradlew test` 통과
- [ ] `AppleOAuthServiceTest`: mock 디코더를 넣은 서비스에서 `getUserInfo`를 두 번 부르면 같은 디코더의 `decode`가 두 번 불린다(재사용). 디코더가 `JwtException`을 던지면 `OAuthAuthenticationException`이 난다(기존 매핑 유지)
- [ ] `AppleOAuthService`의 Spring용 생성자 경로로 만든 인스턴스는 디코더 필드가 null이 아니며, `validateIdToken` 안에 `NimbusJwtDecoder.withJwkSetUri` 호출이 남아 있지 않다(grep)
- [ ] `FirebaseConfigTest`: 분리한 메서드가 만든 `FirebaseOptions`의 `getConnectTimeout()==3000`, `getReadTimeout()==10000`, `getWriteTimeout()==10000`
- [ ] `R2ConfigTest`: `R2Config.s3Client()`로 만든 클라이언트의 `serviceClientConfiguration().overrideConfiguration()`에서 `apiCallAttemptTimeout()==5s`, `apiCallTimeout()==15s`. 네트워크 호출은 하지 않는다
- [ ] `TikitakApplicationTests`·`ExternalCallConnectionTest` 등 기존 컨텍스트 테스트가 그대로 통과한다(`./gradlew test`에 포함)
- [ ] 결정 기록 두 곳의 #179 문장이 실제 적용 값과 일치한다
- [ ] 범위 밖 파일 변경 없음(`git diff --stat origin/develop`)

## 커밋 구성
1. `[Fix] Apple id_token 검증 JWKS 디코더 재사용 (#179)`: AppleOAuthService + 테스트
2. `[Fix] FCM 연결·읽기·쓰기 타임아웃 설정 (#179)`: FirebaseConfig + 테스트
3. `[Fix] R2 S3Client 호출 타임아웃 설정 (#179)`: R2Config + 테스트 + 결정 기록 두 곳

## 하지 말 것
- 범위 밖 리팩터링, 손으로 하는 포맷 정리
- 사람 승인 없이 커밋/푸시
- `build.gradle` 변경(apache-client 등 의존성 추가)
- Properties 클래스·`application.yaml` 추가/변경. 특히 `AuthProperties` record 필드 추가 금지
- Apple 네트워크 실패를 AUTH104와 다른 코드로 바꾸기(동작 변경, 필요하면 별도 이슈)
- `S3Presigner`, `AsyncConfig`, 스케줄러 설정 변경

## 남은 리스크
- Apple 키 교체 시 캐시에 없는 kid가 오면 Nimbus가 다시 조회하는지: 미확인. 재조회하지 않으면 최대 TTL 5분 동안 새 키로 서명된 토큰이 AUTH104로 실패할 수 있다.
- FCM 재시도 정책(IOException·5xx)과 그 횟수: 미확인. 재시도가 있으면 실제 최대 대기는 (3s+10s)×시도 횟수다.
- 업로드 완료는 파일 최대 10개를 순차 `headObject`하므로 최악 약 150s(10 × 15s).
- `@Scheduled` 작업은 Boot 기본 스케줄러를 공유한다(pool 크기는 미확인, Boot 3 기준 1). R2 호출 하나가 최대 15s까지 다른 배치를 밀 수 있다.

## 참고
- `docs/decisions/2026-11-05-external-call-outside-transaction.md:34`, `docs/decisions/2026-11-12-media-delete-r2-after-commit.md:41`
- 타임아웃 상수·테스트 선례: `KakaoOAuthService.java:36-42`, `KakaoOAuthServiceTest.java:50-70`
- lessons-log 2026-11-04 #168: 설정·기동 검증은 실제 빈 생성 경로로 확인한다
