# 작업 계약: ArchUnit 예외 목록의 기존 규칙 위반 정리

- **관련 이슈**: #168
- **브랜치**: `Refactor/#168-archunit-exception-cleanup` (Orca worktree, develop 4f9239b 기준)
- **상태**: 완료 (2026-11-04, PR #172)

## 목표
- ArchUnit 예외 상수에 남아 있는 기존 위반 중 L3, L4, C4, C9, C10을 고치고, 해당 예외 항목을 지운다.
- C7(`AuthController` OAuth 302)은 범위 밖이라 그대로 둔다.
- 패키지 이동과 설정 전환은 동작을 바꾸지 않는다. 의도된 동작 변경은 아래 C9의 TokenService 한 가지뿐이다.

## 항목별 작업 (조사: 2026-11-04 researcher)

| 규칙 | 작업 |
|---|---|
| L3 | `service.notification.dto.NotificationPayload` → `domain.notification.dto.NotificationPayload`로 이동 (내용 변경 없음) |
| L4 | `service.home.dto.RegionRow`, `service.map.dto.MapPinRow` → `repository.feed.projection`으로 이동 (둘 다 native query + interface projection이라 쿼리 문자열 변경 없음) |
| C4 | `domain.auth.RefreshToken` → `domain.auth.entity.RefreshToken`으로 이동 (`@Table(name = "refresh_token")`이 명시돼 있어 DB 영향 없음) |
| C10 | `global/config`에 `OAuthStateProperties`(prefix `auth.oauth.state`), `LoginCodeProperties`(prefix `auth.login-code`) record를 새로 만든다. 구성요소는 `redisKeyPrefix`, `ttl`이다. 기본값은 지금 `@Value` 기본값과 똑같이 compact constructor에 둔다: `tikitak:local:oauth:state:` / `tikitak:local:auth:login-code:` / `PT5M`. `PropertiesConfig`에 등록하고, 두 Store는 생성자 주입으로 바꾼다. |
| C9 TokenService | `toJson`, `sign`, `hash` 실패 → `BusinessException(ErrorCode.AUTH010, e)`. AUTH010은 신규 코드로 "토큰 처리 중 서버 오류가 발생했습니다." / 500이다. |
| C9 RedisLoginCodeStore | 직렬화·역직렬화 실패 → `BusinessException(ErrorCode.COMMON004, e)`. 지금 fallback 핸들러가 내는 응답(COMMON004, 500)과 같다. |
| C9 ImageUrlResolver | `@PostConstruct validateConfiguration()`을 `global/config`의 새 기동 검증 빈으로 옮긴다. 두 Properties(`ImageOptimizationProperties`, `R2Properties`)를 함께 보는 검증이라 단일 `@Validated`로는 옮길 수 없다. 기동 실패용 `IllegalStateException`과 메시지는 그대로 둔다. config 패키지는 C9 검사 대상이 아니고, HTTP 응답 경로도 아니다. |

## 의도된 동작 변경 (1건)
- JWT secret 누락 등으로 `sign`/`hash`가 실패하는 경우에 해당한다.
  - refresh 검증 경로: 401(AUTH007) → 500(AUTH010)
  - 발급 경로: 500(AUTH105/AUTH008) → 500(AUTH010). 상태코드는 그대로다.
- 정상 토큰, 만료 토큰, 위조 토큰의 응답은 바뀌지 않는다.

## 범위: 건드려도 되는 파일
- 이동 대상: `NotificationPayload.java`, `RegionRow.java`, `MapPinRow.java`, `RefreshToken.java` (기존 파일 삭제 → 새 위치 생성)
- import 수정
  - main: `Notification.java`, `NotificationService.java`, `NotificationEventListener.java`, `FeedRepository.java`, `MapService.java`, `TokenService.java`, `RefreshTokenRepository.java`
  - test: `NotificationServiceTest`, `NotificationEventListenerTest`, `HomeServiceTest`, `MapServiceTest`, `FeedRegionRepositoryIntegrationTest`, `FeedRepositoryIntegrationTest`
- C10
  - `global/config/OAuthStateProperties.java`, `global/config/LoginCodeProperties.java` (신규)
  - `global/config/PropertiesConfig.java`
  - `service/auth/RedisOAuthStateStore.java`, `service/auth/RedisLoginCodeStore.java`
  - `RedisLoginCodeStoreIntegrationTest.java`: `ReflectionTestUtils`로 필드를 주입하던 것을 Properties 생성자 주입으로 바꾼다.
- C9
  - `global/exception/ErrorCode.java` (AUTH010 한 줄)
  - `TokenService.java`, `RedisLoginCodeStore.java`, `service/media/ImageUrlResolver.java`
  - `global/config/` 아래 기동 검증 클래스 1개 (신규)
  - `ImageUrlResolverTest.java`: 검증 테스트 2개를 새 검증 클래스 테스트로 옮기고, 메시지 단언은 유지한다.
- `architecture/LayerDependencyArchTest.java`, `architecture/ConventionArchTest.java`: 예외 항목 제거. 비게 된 상수도 정리한다.
- `CLAUDE.md`: 패키지 구조 설명에 `domain/<feature>/dto`, `repository/<feature>/projection`을 추가한다.

## 완료 조건
- [ ] L3, L4, C4, C9, C10 예외 항목이 상수에서 모두 사라지고, C7 항목만 남는다.
- [ ] `./gradlew test` 통과 (ArchUnit 포함)
- [ ] `./gradlew integrationTest` 통과. Docker가 필요하다. Feed projection 테스트와 RedisLoginCodeStore 테스트가 여기서 돈다.
- [ ] `./gradlew spotlessCheck` 통과. `FeedRepository` text block의 공백이 바뀌지 않는다(`git diff`로 import 줄만 바뀌었는지 확인).
- [ ] 이동만 한 파일은 패키지 선언과 import 외 변경이 없다. `git diff -M`에서 rename으로 잡히는지로 확인한다.
- [ ] C10 Properties 기본값이 기존 `@Value` 기본값과 문자 단위로 같다.
- [ ] 새 테스트 2개
  - Properties 기본값 테스트: 값이 없을 때 기존 기본값이 들어간다.
  - TokenService 테스트: secret이 비었을 때 AUTH010 `BusinessException`이 나온다. 단위 테스트로 가능한 범위에서 작성한다.

## 하지 말 것
- 범위 밖 리팩터링, 손 포맷 정리 (수정한 파일에 spotlessApply를 돌리는 건 허용)
- `application*.yaml`의 키 이름이나 값 변경. Redis 키 프리픽스가 바뀌면 TTL 5분 동안 살아 있는 키를 읽지 못한다.
- `AuthProperties` 구성요소 변경
- C7 예외 제거, `OAuthAuthenticationException` 정리 (결정 기록상 별도 결정 사항이다)
- 새 ArchUnit 예외 추가
- 사람 승인 없이 커밋/푸시

## 커밋 구성 (한 PR, 항목별 커밋, 각 커밋 승인)
1. L3 + L4 + C4 패키지 이동
2. C10 Properties 전환
3. C9 예외 정리

커밋마다 해당 예외 항목도 함께 지운다.

## 참고
- `docs/decisions/2026-11-02-archunit-explicit-exceptions.md`: 예외는 클래스 단위다. `RedisLoginCodeStore`는 C9와 C10 양쪽 목록에 있어서 각각 고쳐야 지울 수 있다.
- `docs/lessons-log.md`: Spotless text block 사고, worktree는 develop 기준
- 결정 사항 (2026-11-04 사용자 선택): payload 패키지 `domain.notification.dto`, TokenService 실패는 500, C10은 신규 record 2개
