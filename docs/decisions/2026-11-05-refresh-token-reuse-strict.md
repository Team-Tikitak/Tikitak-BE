# refresh token 재사용은 유예 없이 탐지하고, revoke된 행은 만료 전까지 남긴다

- **날짜**: 2026-11-05
- **관련 이슈**: #163 (계약 `docs/contracts/2026-11-05-refresh-token-reuse.md`)
- **상태**: 결정됨 (2026-11-05 사람이 결정)

## 상황 (Context)
- revoke된 refresh token으로 재발급을 요청해도 `AUTH007`만 돌려주고 끝났다. 토큰 탈취 신호로 처리하지 않았다.
- `reissueToken`은 `findByTokenHash`로 락 없이 조회한 뒤 revoke했다. 같은 토큰으로 동시에 요청하면 두 요청이 모두 검증을 통과해 토큰 쌍이 2개 발급됐다.
- `refresh_token` 행을 지우는 배치가 없어 테이블이 계속 커졌다.
- payload가 `sub`, `type`, `iat`(초 단위), `exp`뿐이라, 같은 회원이 같은 초에 두 번 발급받으면 토큰 문자열과 해시가 같았다. `token_hash`는 `UNIQUE`(`V2__create_refresh_token.sql`)라 두 번째 저장이 실패했다. 최종 응답은 상위 `catch` 구조에 따라 재발급 500 `AUTH008`, OAuth 웹 로그인 500 `AUTH105`, 앱 loginCode 교환 500 `COMMON004`였다.
- 서명 비교가 `Objects.equals`였고, secret 길이는 검증하지 않았다. 1~31바이트 secret으로도 정상 기동·서명했고, 빈 secret은 첫 서명 시점에야 500 `AUTH010`으로 드러났다.

## 검토한 선택지 (Options considered)
| 선택지 | 내용 | 문제 |
|---|---|---|
| 유예 시간(N초) | 직전 N초 안에 revoke된 토큰의 재전송은 재사용으로 보지 않는다 | 유예 판정 로직과 테스트가 늘어난다. 이슈는 revoke된 토큰 재사용 시 모든 refresh token을 revoke하라고 적었고, 유예는 그 범위 밖이다. |
| 비관적 락 | `findByTokenHash`를 `SELECT ... FOR UPDATE`로 바꾼다 | 진 쪽 요청이 락을 기다린다. 조건부 update는 영향 행 수(0)로 바로 판정할 수 있다. |
| 새 ErrorCode | 재사용을 `AUTH007`과 다른 코드로 응답한다 | 클라이언트가 할 일은 어느 쪽이든 재로그인이라, 구분해도 처리가 달라지지 않는다. |
| revoke-all을 `REQUIRES_NEW` 별도 빈으로 | 예외가 나도 revoke-all만 따로 커밋한다 | `noRollbackFor` 선례(`MediaUploadCompletionService`의 `@Transactional(noRollbackFor = ExpiredUploadException.class)`, 74행/196행)보다 변경이 크다. |
| **조건부 update + 유예 없음 + `noRollbackFor` (채택)** | 아래 결정 | 아래 "남은 리스크" |

## 결정 (Decision)
- **재사용을 유예 없이 탐지한다.** 아래 두 경우를 재사용으로 본다.
  - revoke된 토큰이 다시 온 경우 (`validateRefreshToken`에서 `savedToken.isRevoked()`)
  - 동시 재발급에서 진 경우: `RefreshTokenRepository.revokeIfActive`(`UPDATE ... WHERE tokenHash = :tokenHash AND revoked = false`)가 0을 반환
- 재사용이면 `log.warn`(memberId와 사유만, 토큰 원문/해시는 넣지 않음) → `revokeAllRefreshTokens(memberId)` → `RefreshTokenReuseException`을 던진다.
- 응답은 **`AUTH007`을 그대로** 쓴다. `RefreshTokenReuseException`은 `TokenService` 안의 private static 클래스로 `BusinessException(AUTH007)`을 상속한다. 새 ErrorCode는 추가하지 않고, 재사용은 warn 로그로만 구분한다.
- `reissueToken`에 `@Transactional(noRollbackFor = RefreshTokenReuseException.class)`를 붙여, 예외를 던져도 revoke-all이 커밋되게 한다. `revokeAllRefreshTokens`는 같은 클래스 안에서 부르므로(self-invocation) 그 메서드의 `@Transactional`은 따로 적용되지 않는다.
- DB에서 만료됐지만 revoke되지 않은 토큰은 재사용이 아니다. `AUTH007`만 내고 revoke-all은 하지 않는다.
- **만료 토큰 정리 배치**(`RefreshTokenCleanupBatchService`, 계약상 `cron = "0 45 4 * * *"`, `zone = KstDateProvider.KST_ZONE_ID`, 매일 04:45 KST)는 `expires_at < now`인 행만 지운다. revoke됐지만 만료 전인 행은 남긴다.
- access/refresh token payload에 랜덤 `jti`(`UUID.randomUUID().toString()`)를 넣는다. 검증 로직은 `jti`를 읽지 않으므로 jti가 없는 기존 토큰도 계속 통과한다.
- 서명 비교를 `MessageDigest.isEqual`(US-ASCII 바이트)로 바꾼다. 결과와 응답 코드는 같다.
- `JwtSecretValidator`(`@Component` + `@PostConstruct`)가 기동 시점에 `auth.jwt`가 없거나, secret이 null이거나, `secret.getBytes(UTF_8).length < 32`이면 `IllegalStateException`으로 기동을 막는다. 문자 수가 아니라 UTF-8 바이트 수로 판정한다. 이전에는 정상 기동하던 **1~31바이트 secret도 이제 기동에 실패한다**. 운영/개발 `JWT_SECRET`이 32바이트 이상인 것은 사람이 확인했다.

## 이유 (Rationale)
- **유예를 두지 않는 이유**: 탈취된 토큰과 정상 클라이언트의 재시도를 서버에서 구분할 수 없다. 유예를 두면 그 시간 안의 재사용은 탐지하지 못하고, 판정 로직과 테스트가 늘어난다. 조건부 update는 영향 행 수만 보면 되므로 락 대기 없이 바로 판정한다.
- **revoke된 행을 만료 전까지 남기는 이유**: 재사용 탐지는 "revoke된 행이 있다"로 판정한다. revoke된 행까지 지우면 재사용된 토큰이 `findByTokenHash`에서 "없는 토큰"(`AUTH007`, revoke-all 없음)으로 보여 탐지할 수 없다. 이슈는 "만료/revoke된 행 정리"를 적었지만, 이 이유로 만료 기준으로만 지운다.
- **만료된 행을 지워도 안전한 이유**: `validateRefreshToken`은 JWT 서명·type·`exp`를 먼저 검사하고 그 뒤에 `findByTokenHash`를 부른다. DB `expires_at`은 `issueToken`에서 토큰을 만든 뒤 `LocalDateTime.now().plusSeconds(refreshTokenExpiresIn)`으로 정하므로 JWT `exp`보다 이르지 않다. 따라서 `expires_at < now`인 행의 토큰은 DB 조회 전에 `exp` 검사에서 이미 `AUTH007`로 걸러진다.
- **`jti`를 넣는 이유**: 같은 초 발급 시 토큰 문자열이 같아 `token_hash` unique 위반으로 500(재발급 `AUTH008`, 로그인 `AUTH105`/`COMMON004`)이 나던 것을 없앤다. 이번 범위에 넣는 것은 2026-11-05 사람이 정했다.
- **secret 32바이트**: HS256 키는 256비트(32바이트) 이상이어야 한다(RFC 7518 §3.2). 설정 오류는 요청 시점의 500이 아니라 기동 실패로 드러나야 한다.

## 남은 리스크
- **프론트가 refresh를 동시에 호출하거나 같은 토큰으로 재시도하면 전 기기 로그아웃이 된다.** 동시 재발급에서 진 쪽의 revoke-all은 이긴 쪽이 막 받은 새 refresh token까지 revoke한다. 프론트가 refresh를 한 번만 호출하는지(단일 비행 처리)는 확인하지 못했다.
- **재사용 처리가 끝까지 적용되지 않는 경로가 있다.** 둘 다 재발급 자체는 거부된다.
  - 재사용 처리 후 커밋이 실패하면 `AuthService.refreshToken`의 `catch (Exception)`이 500 `AUTH008`로 바꾸고, revoke-all은 적용되지 않는다.
  - revoke-all 조회(`findAllByMemberIdAndRevokedFalse`)에서 DB 오류가 나면, revoke된 토큰 경로는 `validateRefreshToken`의 `try` 안이라 401 `AUTH007` + rollback, 동시 재발급 경로(`reissueToken`)는 500 `AUTH008`이다. 둘 다 revoke-all이 적용되지 않는다.
- **prod blue/green 전환 중 두 컨테이너가 함께 떠 있으면 정리 배치가 두 번 돌 수 있다.** 같은 조건의 DELETE라 결과는 같다. 분산 락은 넣지 않았다.
- **동시성 integration 테스트(`TokenServiceConcurrencyIntegrationTest`)는 타이밍에 따라 두 경로 중 어느 쪽으로도 통과한다.** 이긴 쪽이 진 쪽의 `findByTokenHash`보다 먼저 커밋하면 진 쪽은 "revoke된 토큰" 경로로, 그렇지 않으면 `revokeIfActive` 0행 경로로 끝난다. 결과(성공 1건, `AUTH007` 1건, 유효 토큰 0건)는 같다. 0행 경로는 `TokenServiceTest`(mock이 0 반환)와 `RefreshTokenRepositoryIntegrationTest`(두 번째 호출이 0 반환)로 따로 확인한다.
- `refresh_token.expires_at`에는 인덱스가 없다. 이번에는 마이그레이션을 추가하지 않았다.

## 다시 볼 조건
- 프론트의 refresh 호출 방식을 확인했는데 동시 호출·재시도가 있을 때 (유예 시간 또는 프론트 단일 비행 처리 재검토)
- 재사용 탐지 로그가 정상 사용자에게서 자주 보일 때
- JWT 구현을 Nimbus(`spring-security-oauth2-jose`)로 바꿀 때
- `refresh_token` 테이블이 커져 정리 배치의 DELETE가 느려질 때 (`expires_at` 인덱스 검토)
