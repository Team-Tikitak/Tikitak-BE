# 작업 계약: 리프레시 토큰 재사용 탐지 및 JWT 검증 강화

- **관련 이슈**: #163
- **브랜치**: `Fix/#163-refresh_token_reuse` (Orca worktree, develop fa9f1f5 기준)
- **상태**: 완료 (2026-11-07, PR #187)

## 목표
- revoke된 refresh token이 다시 오거나 같은 토큰으로 동시에 재발급하면, 이를 재사용으로 보고 그 회원의 refresh token을 모두 revoke한다. 재발급은 한 번만 성공한다.
- 만료된 refresh token 행을 매일 정리한다.
- JWT 서명 비교를 상수 시간으로 바꾸고, secret이 32바이트보다 짧으면 앱이 기동하지 않게 한다.

## 사람이 정한 것 (2026-11-05)
- 동시 재발급에서 진 쪽(조건부 update 0행)도 **유예 없이 재사용으로 처리**한다.
- 재사용 응답은 **AUTH007을 그대로** 쓴다. ErrorCode는 추가하지 않고, 서버 로그(warn)로만 구분한다.
- payload에 랜덤 `jti`를 넣는 작업도 이번 범위에 포함한다.
- 운영/개발 `JWT_SECRET`은 32바이트 이상이다(사람이 확인함).
- 아래는 조사 때 낸 추천을 그대로 따른다: noRollbackFor, 조건부 update, `expires_at` 기준 정리, `@PostConstruct` 검증 빈.

## 항목별 작업

| # | 작업 |
|---|---|
| 1 | `createToken` payload에 `jti`(`UUID.randomUUID().toString()`)를 추가한다. access와 refresh 둘 다 넣는다. 검증 로직은 jti를 읽지 않는다. |
| 2 | `validateRefreshToken`/`validateAccessToken`의 서명 비교를 `MessageDigest.isEqual(a.getBytes(US_ASCII), b.getBytes(US_ASCII))`로 바꾼다. 결과와 응답 코드는 바꾸지 않는다. |
| 3 | `global/config/JwtSecretValidator`를 새로 만든다(`@Component` + `@PostConstruct`, `ImageOptimizationConfigValidator`와 같은 형태). `authProperties.jwt()`가 null이거나, secret이 null이거나, `secret.getBytes(UTF_8).length < 32`이면 `IllegalStateException`으로 기동에 실패한다. 메시지에는 secret 값을 넣지 않는다. `AuthProperties` record는 고치지 않는다. 테스트 `src/test/resources/application.yaml`에 32바이트 이상인 `auth.jwt.secret`을 추가한다. |
| 4 | `RefreshTokenRepository`에 `int revokeIfActive(String tokenHash, LocalDateTime now)`를 추가한다. 쿼리는 `UPDATE ... SET revoked=true, revokedAt=:now WHERE tokenHash=:hash AND revoked=false`이고, `@Modifying(flushAutomatically = true, clearAutomatically = true)`를 붙인다. |
| 5 | `reissueToken`을 아래 순서로 바꾼다. `@Transactional(noRollbackFor = RefreshTokenReuseException.class)`를 붙이고, `RefreshTokenReuseException`은 TokenService 안의 private static 클래스로 둔다(`BusinessException(AUTH007)`을 상속, `MediaUploadCompletionService:74,196` 선례).<br>① 서명, type, exp 검증(기존과 같음)<br>② `findByTokenHash`: 없으면 AUTH007<br>③ **이미 revoke됨 → 재사용 처리**<br>④ DB 만료 → AUTH007(재사용 아님)<br>⑤ 회원이 비활성 → AUTH007<br>⑥ `revokeIfActive`가 0이면 **재사용 처리**<br>⑦ `issueToken`<br>재사용 처리는 `log.warn`(memberId 포함, 토큰 원문/해시는 넣지 않음) → 기존 `revokeAllRefreshTokens(memberId)` → `RefreshTokenReuseException`을 던진다. |
| 6 | `service/auth/RefreshTokenCleanupBatchService`를 새로 만든다. `@Scheduled(cron = "0 45 4 * * *", zone = KstDateProvider.KST_ZONE_ID)`와 `@Transactional`을 붙이고, `refreshTokenRepository.deleteExpired(LocalDateTime.now())`(벌크 `DELETE ... WHERE expiresAt < :now`)를 한 번 호출한 뒤 삭제 건수를 info 로그로 남긴다. revoke됐지만 아직 만료 전인 행은 재사용 탐지에 쓰이므로 지우지 않는다. 분산 락은 넣지 않는다. 같은 DELETE는 두 번 돌아도 결과가 같다. |
| 7 | 결정 기록 `docs/decisions/2026-11-05-refresh-token-reuse-strict.md`를 쓰고 README 목록에 한 줄 추가한다. 내용은 유예를 두지 않은 이유, revoke된 행을 만료 전까지 남기는 이유, 남은 리스크(프론트가 refresh를 동시에 호출하면 전 기기 로그아웃)다. |

## 의도된 동작 변경

| 경우 | 이전 | 이후 |
|---|---|---|
| revoke된 refresh token으로 재발급 | 401 AUTH007 | 401 AUTH007 + **그 회원의 유효한 refresh token 전부 revoke** |
| 같은 토큰으로 동시에 재발급 2건 | 둘 다 200, 토큰 쌍 2개 발급 | 1건 200, 1건 401 AUTH007. 이긴 쪽이 받은 새 refresh token까지 전부 revoke |
| 같은 회원이 같은 초에 토큰을 두 번 발급 (`token_hash` unique 위반) | 재발급 500 AUTH008, OAuth 웹 로그인 500 AUTH105, 앱 loginCode 교환 500 COMMON004 (2026-11-07 verifier가 정정) | 200 |
| DB에서 만료됐지만 revoke되지 않은 토큰 | 401 AUTH007 | 변화 없음(revoke-all 하지 않음) |
| secret이 비었거나 null | 정상 기동, 첫 서명 시점에 500 AUTH010 | 기동 실패 |
| secret이 1~31바이트 | 정상 기동, 정상 서명 | 기동 실패 |
| 만료된 `refresh_token` 행 | 계속 남음 | 매일 04:45 KST에 삭제 |
| 서명 비교, access 검증, 로그아웃, 탈퇴(`MeService`) | — | 변화 없음 |
| `reissueToken` 커밋 실패 (트랜잭션 경계는 옮기지 않음) | `AuthService` catch → 500 AUTH008 | 변화 없음 |
| 재사용 처리 후 커밋 실패 (2026-11-07 verifier가 찾음) | 경로 없음 (rollback으로 끝나 항상 401 AUTH007) | 500 AUTH008, revoke-all 미적용 |
| 재사용 처리 중 revoke-all 조회 DB 오류 (2026-11-07 verifier가 찾음) | 경로 없음 | ③ revoke된 토큰: try 안이라 401 AUTH007 + rollback. ⑥ 동시 재발급: 500 AUTH008. 둘 다 revoke-all 미적용 |

- 이미 발급된 토큰(jti 없음)도 계속 검증을 통과한다.

## 범위: 건드려도 되는 파일
- `src/main/java/kusitms/spin/tikitak/service/auth/TokenService.java`
- `src/main/java/kusitms/spin/tikitak/service/auth/RefreshTokenCleanupBatchService.java` (신규)
- `src/main/java/kusitms/spin/tikitak/repository/auth/RefreshTokenRepository.java`
- `src/main/java/kusitms/spin/tikitak/domain/auth/entity/RefreshToken.java` (revoke와 만료를 따로 판정하는 메서드 추가만 허용)
- `src/main/java/kusitms/spin/tikitak/global/config/JwtSecretValidator.java` (신규)
- `src/test/resources/application.yaml` (`auth.jwt.secret`만 추가)
- `src/test/java/kusitms/spin/tikitak/service/auth/TokenServiceTest.java`
- `src/test/java/kusitms/spin/tikitak/service/auth/TokenServiceConcurrencyIntegrationTest.java` (신규)
- `src/test/java/kusitms/spin/tikitak/repository/auth/RefreshTokenRepositoryIntegrationTest.java` (신규)
- `src/test/java/kusitms/spin/tikitak/service/auth/RefreshTokenCleanupBatchServiceTest.java` (신규)
- `src/test/java/kusitms/spin/tikitak/global/config/JwtSecretValidatorTest.java` (신규)
- `docs/decisions/2026-11-05-refresh-token-reuse-strict.md` (신규), `docs/decisions/README.md`

## 완료 조건
- [ ] `./gradlew test`, `./gradlew integrationTest`, `./gradlew spotlessCheck` 통과(ArchUnit 포함)
- [ ] `TokenService`에 `Objects.equals(sign(` 호출이 0건이다.
- [ ] `TokenServiceTest`
  - 같은 회원으로 `issueToken`을 연속 두 번 호출하면 refresh token 문자열이 서로 다르고, payload에 `jti`가 있다.
  - revoke된 토큰: AUTH007, `findAllByMemberIdAndRevokedFalse(memberId)`로 찾은 토큰이 모두 revoke됨, `revokeIfActive`와 `save`는 `never()`
  - `revokeIfActive`가 0을 반환: AUTH007, revoke-all 실행, `save`는 `never()`
  - DB 만료(revoke 안 됨): AUTH007, `findAllByMemberIdAndRevokedFalse`와 `revokeIfActive`는 `never()`
  - 정상: `revokeIfActive` 1회, 새 토큰 저장
  - 기존 테스트(만료 JWT, 위조, secret ""/null → AUTH010, access 검증)는 그대로 통과한다.
- [ ] `RefreshTokenRepositoryIntegrationTest`(Postgres)
  - `revokeIfActive`가 첫 호출에서 1, 두 번째 호출에서 0을 반환하고, `revoked_at`이 기록된다.
  - `deleteExpired(now)`는 만료된 행(revoke 여부 무관)만 지우고, 만료 전 행은 revoke됐어도 남긴다.
- [ ] `TokenServiceConcurrencyIntegrationTest`(Postgres, 프록시가 붙은 실제 `TokenService`, 테스트 트랜잭션 끔)
  - 같은 refresh token으로 두 스레드가 동시에 `reissueToken`을 호출하면 성공 1건, AUTH007 1건이다.
  - 끝난 뒤 그 회원의 `revoked = false` 행이 0건이다. 예외가 났는데도 revoke-all이 롤백되지 않았음을 커밋된 DB 상태로 확인한다.
  - revoke된 토큰을 한 번 더 보내도(순차) revoke-all이 커밋된다.
  - 테스트가 만든 데이터는 테스트 안에서 정리한다.
- [ ] `JwtSecretValidatorTest`(`ApplicationContextRunner`, 바인딩 경로 사용)
  - secret 31바이트 → 기동 실패, 32바이트 → 기동 성공
  - `auth.jwt` 미설정 → 기동 실패
  - 한글 11자(33바이트) → 기동 성공. 문자 수가 아니라 바이트 수로 판정함을 확인한다.
- [ ] `RefreshTokenCleanupBatchServiceTest`: `deleteExpired`를 한 번 호출하고, `@Scheduled`의 cron과 zone이 위 값과 같다.
- [ ] `TikitakApplicationTests` contextLoads가 통과한다(테스트 yaml의 secret으로 검증을 통과).

## 커밋 구성
1. JWT 강화: jti, `MessageDigest.isEqual`, `JwtSecretValidator`, 테스트 yaml + 테스트 (항목 1~3)
2. 재사용 탐지와 조건부 update: repository, `reissueToken`, unit 테스트, integration 테스트 (항목 4~5)
3. 만료 토큰 정리 배치 + 테스트 (항목 6, `deleteExpired` repository 테스트 포함)
4. 결정 기록 + README (항목 7)

## 하지 말 것
- 새 ErrorCode 추가, 응답 코드 변경, `AuthService`/`AuthController`/`MeService` 수정
- `AuthProperties` record 변경(compact constructor 검증 포함), `AuthControllerTest`/`GoogleOAuthServiceTest`의 secret 상수 변경
- Flyway 마이그레이션 추가(`expires_at` 인덱스 포함), ShedLock 등 분산 락 도입
- 재사용 유예 시간, Nimbus(`spring-security-oauth2-jose`)로 교체
- 로그나 예외 메시지에 토큰 원문/해시나 secret을 넣는 것
- 범위 밖 리팩터링, 손으로 하는 포맷 정리(Spotless 결과는 허용)
- 사람 승인 없이 커밋/푸시

## 참고
- 조사: 2026-11-05 researcher. `reissueToken`이 바깥 트랜잭션 경계이고(`AuthService.refreshToken`에는 트랜잭션이 없음), self-invocation이라 `revokeAllRefreshTokens`의 `@Transactional`은 따로 적용되지 않는다. 그래서 noRollbackFor가 필요하다.
- 동시성: Postgres READ COMMITTED에서는 진 쪽 UPDATE가 이긴 쪽 커밋까지 기다렸다가 0행이 된다. 그 뒤 진 쪽의 `findAllByMemberIdAndRevokedFalse`는 이긴 쪽이 새로 발급한 토큰도 본다.
- `revokeIfActive`의 `clearAutomatically` 때문에 ⑥ 이후 영속성 컨텍스트가 비워진다. `issueToken`은 member를 다시 조회하므로 문제없다.
- lessons-log `:32`(호출부 전체와 최종 코드 표 → 조사 결과에 반영), `:33`(같은 코드를 내는 분기는 `never()`로 고정), `:34`(기동 검증은 `ApplicationContextRunner`, 바인딩 경로로 확인)
- `docs/contracts/2026-11-04-archunit-exception-cleanup.md`(#168): `sign`/`hash` 실패는 AUTH010. 이번에 바꾸지 않는다.
- 남은 리스크(PR에 기록): 프론트가 refresh를 동시에 호출하거나 같은 토큰으로 재시도하면 전 기기 로그아웃이 된다. blue/green 전환 중에 배치가 두 번 돌 수 있지만 결과는 같다.
- develop fa9f1f5 반영(2026-11-07): `RefreshToken`이 `BaseCreatedTimeEntity`를 상속하고 `createdAt`은 auditing이 채운다. 동시성 integration 테스트에서 `TokenService`를 띄울 때 JPA auditing이 켜져 있어야 한다.
