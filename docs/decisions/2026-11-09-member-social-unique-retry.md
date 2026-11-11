# 회원 소셜 계정은 ACTIVE 부분 unique 인덱스로 막고, 동시 첫 로그인에서 진 요청은 AuthService에서 1회 재시도한다

- **날짜**: 2026-11-09
- **관련 이슈**: #181 (계약 `docs/contracts/2026-11-09-member-social-unique.md`)
- **상태**: 결정됨 (2026-11-09 사람이 결정)

## 상황 (Context)
- `OAuthMemberLoginService.findOrCreateMember`(`OAuthMemberLoginService.java:54-79`)는 `findBySocialProviderAndProviderIdAndStatus(provider, providerId, ACTIVE)`로 조회하고, 없으면 `memberRepository.save(Member.createSocialMember(...))`로 만든다. 조회와 생성 사이에 락도 unique 제약도 없었다.
- 그래서 같은 소셜 계정의 첫 로그인이 동시에 2건 들어오면 둘 다 "없음"을 보고 ACTIVE 회원을 2행 만들었다. 그 뒤 이 계정으로 로그인하면 `Optional` 조회 결과가 2건이라 예외가 나고, `AuthService`의 `catch (Exception)`이 500 `AUTH105`로 바꾼다.
- 이 경쟁은 [외부 API 호출은 트랜잭션 밖에서 실행](2026-11-05-external-call-outside-transaction.md) 기록의 "남은 리스크"에 "회원 unique 제약은 후속 이슈"로 적혀 있었다. 이 기록이 그 후속 처리다(그 문서는 고치지 않았다).
- 탈퇴(`Member.withdraw()`, `Member.java:105-111`)는 `status`를 `INACTIVE`로 바꾸고 `providerId`를 `"WITHDRAWN:" + id + ":" + UUID`로 덮어쓴다. 이 덮어쓰기는 #71(`a5460da`, 2026-05-23)에서 들어왔고, 그 전의 `withdraw()`는 `status`만 바꿔 원래 `providerId`를 그대로 두었다. 따라서 #71 이전에 탈퇴한 행은 같은 `(social_provider, provider_id)`로 재가입한 ACTIVE 행과 값이 겹칠 수 있다.

## 검토한 선택지 (Options considered)

### 인덱스
| 선택지 | 내용 | 문제 |
|---|---|---|
| 전체 unique | `(social_provider, provider_id)` 전체에 unique | #71 이전 탈퇴 행과 재가입 행이 겹치면 마이그레이션이 실패한다. 조회는 ACTIVE만 보는데 제약은 INACTIVE까지 걸려 의미가 다르다. |
| `CREATE UNIQUE INDEX CONCURRENTLY` | 쓰기를 막지 않고 생성 | `executeInTransaction=false`가 필요하다. 실패하면 INVALID 인덱스가 남아 수동 정리가 필요하다. |
| **부분 unique, 일반 `CREATE UNIQUE INDEX` (채택)** | `V25__add_member_social_provider_active_unique.sql`: `CREATE UNIQUE INDEX uk_member_social_provider_active ON member (social_provider, provider_id) WHERE status = 'ACTIVE';` | 아래 "남은 리스크" |

### 위반 처리
| 선택지 | 내용 | 문제 |
|---|---|---|
| 같은 트랜잭션 안에서 catch 후 재조회 | `findOrCreateMember`에서 위반을 잡고 기존 회원을 다시 조회 | 아래 "이유" 참고. 트랜잭션이 이미 abort·rollback-only라 재조회도 커밋도 할 수 없다. |
| `INSERT ... ON CONFLICT DO NOTHING` | native SQL로 생성 후 조회 | 아래 "이유" 참고. |
| `REQUIRES_NEW`로 회원 생성만 분리 | 생성을 별도 트랜잭션으로 커밋 | 아래 "이유" 참고. |
| advisory lock | `pg_advisory_xact_lock`으로 같은 계정 요청을 줄 세움 | 아래 "이유" 참고. |
| **AuthService에서 `OAuthMemberLoginService` 호출만 1회 재시도 (채택)** | 아래 결정 | 아래 "남은 리스크" |

## 결정 (Decision)
- **인덱스**: `V25`에 위 한 문장만 둔다. `-- flyway:executeInTransaction=false` 지시자는 쓰지 않으므로 Flyway 트랜잭션 안에서 실행된다. 엔티티(`Member`)는 바꾸지 않았다.
- **재시도**: `AuthService`(`service/auth/AuthService.java`)에 아래를 둔다.
  - 상수 `MEMBER_SOCIAL_ACTIVE_UNIQUE = "uk_member_social_provider_active"` (28행)
  - `private <T> T retryOnMemberRace(SocialProvider provider, Supplier<T> memberLogin)` (145-155행): 첫 호출에서 `DataIntegrityViolationException`이 나고 `isMemberSocialDuplicate`가 참이면 `log.info("Member social unique conflict on first login, retrying once. provider={}", provider)`를 남기고 한 번만 다시 부른다. 로그에는 provider만 넣고 providerId·이메일은 넣지 않는다. 다른 예외와 두 번째 호출의 예외는 그대로 던진다.
  - `private boolean isMemberSocialDuplicate(Throwable)` (157-167행): cause 체인을 돌며 메시지에 상수가 있는지 본다. `DailyQuestionService.isDailyAnswerDuplicate`(262행)와 같은 형태다.
- **감싸는 위치**: `loginWithOAuth`(5인자, 82행)의 `oauthMemberLoginService.loginWithToken(userInfo)`와 `issueAppLoginCode`(5인자, 105-108행)의 `oauthMemberLoginService.prepareLoginCode(userInfo)`만 감싼다. 둘 다 기존 `try` 안이다. OAuth 외부 호출(`fetchOAuthUserInfo`, 81행·104행)과 `loginCodeStore.save`(110행)는 헬퍼 밖이라 재시도하지 않는다. 기존 `catch (OAuthAuthenticationException)` / `catch (BusinessException)` / `catch (Exception) → AUTH105` 구조는 그대로다.
- **진 요청의 `isNewMember`는 `false`다.** 재시도는 이긴 쪽이 커밋한 회원을 조회해 기존 회원 경로로 가기 때문이다. 앱 경로의 loginCode payload도 `newMember=false`다. 두 요청이 받는 회원은 같다.
- 재시도에서도 이 제약 위반이 나면 지금처럼 500 `AUTH105`다. 새 ErrorCode는 추가하지 않았다.

## 이유 (Rationale)
- **부분 인덱스(`WHERE status = 'ACTIVE'`)를 고른 이유**
  - 조회 조건 `findBySocialProviderAndProviderIdAndStatus(..., ACTIVE)`(`MemberRepository.java:21-25`)와 제약의 의미가 같다. "ACTIVE 회원은 소셜 계정당 하나"만 막으면 된다.
  - #71 이전 탈퇴 행(원래 `providerId`를 가진 INACTIVE 행)이 남아 있어도 인덱스 대상이 아니라 마이그레이션이 통과한다. #71 이후 탈퇴 행은 `WITHDRAWN:` 값이라 애초에 겹치지 않는다.
  - 탈퇴 회원이 같은 계정으로 재가입해 새 ACTIVE 행을 만드는 동작도 그대로 유지된다(`MemberRepositoryIntegrationTest.inactiveAndActiveMembersWithSameSocialAccountAreSaved`).
- **CONCURRENTLY를 쓰지 않은 이유**
  - `member`는 회원 수만큼의 테이블이라 작을 것으로 추정했다(행 수는 측정하지 않았다). 일반 `CREATE UNIQUE INDEX`가 잡는 쓰기 차단 시간이 짧다고 보았다.
  - 트랜잭션 안에서 실행되므로 기존 중복 때문에 실패하면 인덱스 없이 깔끔하게 롤백된다. CONCURRENTLY는 실패 시 INVALID 인덱스가 남는다.
  - CONCURRENTLY는 `executeInTransaction=false`와 Flyway 락 설정 문제를 다시 끌어온다. 이 문제는 [CREATE INDEX CONCURRENTLY + Flyway 데드락](2026-09-21-flyway-concurrent-index-deadlock.md)에서 `transactional-lock: false`로 해결했지만(`application.yaml:20`), 이번 인덱스에는 그 경로가 필요 없다.
- **같은 트랜잭션에서 catch 후 재조회할 수 없는 이유**
  - `Member`의 id는 `GenerationType.IDENTITY`(`Member.java:32`)라 `save` 시점에 INSERT가 바로 나간다. 위반은 `findOrCreateMember` 안에서 난다(`OAuthMemberLoginConcurrencyIntegrationTest`가 `hasStackTraceContaining("findOrCreateMember")`로 확인).
  - PostgreSQL은 문장 하나가 실패하면 그 트랜잭션을 abort 상태로 만든다. 같은 트랜잭션에서 다음 문장(재조회)을 보내면 `current transaction is aborted`(SQLSTATE 25P02)로 실패한다.
  - Spring 쪽에서도 예외가 `@Transactional` 경계(리포지토리 `save`)를 지나며 바깥 트랜잭션이 rollback-only로 표시된다. 예외를 잡고 계속해도 커밋할 수 없다.
  - 그래서 `OAuthMemberLoginService`의 트랜잭션을 통째로 끝내고, 바깥(`AuthService`)에서 새 트랜잭션으로 다시 부르는 방식을 골랐다. 트랜잭션 빈을 따로 둔 구조는 [외부 API 호출은 트랜잭션 밖에서 실행](2026-11-05-external-call-outside-transaction.md)에서 이미 만들어져 있어, 재시도는 그 호출 한 줄만 감싸면 된다.
- **ON CONFLICT를 고르지 않은 이유**
  - 회원 INSERT를 native SQL로 쓰면 `Member`가 상속하는 `BaseTimeEntity`의 auditing과, `Member.createSocialMember`가 정하는 기본값(`status=ACTIVE`, `termsAgreed=false`, `privacyAgreed=false`, `onboardingCompleted=false`)을 SQL에도 따로 적어야 해서 엔티티와 이중 관리가 된다. 다른 테이블에는 선례가 있다(`FeedReactionRepository.upsertReaction`, `PlaceRepository.insertIfAbsentByExternalPlaceId`). 다만 두 테이블은 컬럼이 적고 회원 생성은 기본값이 많다.
  - 부분 인덱스에 맞추려면 충돌 대상을 지정한 형태(`ON CONFLICT (social_provider, provider_id) WHERE status = 'ACTIVE' DO NOTHING`)가 필요하다. 대상 없는 `ON CONFLICT DO NOTHING`은 다른 제약 위반까지 조용히 삼킨다. 그런데 `ExternalCallConnectionTest`가 H2(`MODE=PostgreSQL`)로 `loginWithOAuth`·`issueAppLoginCode`를 실제로 실행하고, H2 2.3.232와 2.4.240은 대상을 지정한 `INSERT ... ON CONFLICT (...) DO NOTHING`을 문법 오류로 거부했다(2026-11-09 직접 실행. 대상 없는 형태는 확인하지 않았다).
  - 계약상 `MemberRepository`와 `OAuthMemberLoginService` 수정이 범위 밖이다.
- **REQUIRES_NEW를 고르지 않은 이유**
  - 회원 생성만 먼저 커밋되므로, 그 뒤 토큰 발급이 실패해도 회원이 남는다. 지금은 회원 생성과 토큰 발급이 한 트랜잭션이라 함께 롤백되고, `ExternalCallConnectionTest.memberCreationRollsBackWhenTokenIssuanceFails`가 이 동작을 지킨다.
  - 바깥 트랜잭션이 커넥션을 쥔 채 안쪽 트랜잭션이 커넥션을 하나 더 잡아, 요청 하나가 커넥션 2개를 쓴다.
  - 기존 `REQUIRES_NEW` 사용처는 `ObjectDeleteOutboxProcessor`(26행) 하나뿐이다.
- **advisory lock을 고르지 않은 이유**
  - 코드베이스에 애플리케이션 쿼리로 advisory lock을 쓰는 선례가 없다(`src/main`, `src/test`에 `pg_advisory` 호출 없음. Flyway가 마이그레이션 락으로 내부에서 쓰는 것은 별개다).
  - H2에서 동작하지 않는다. H2 2.3.232와 2.4.240 모두 `pg_advisory_xact_lock`, `pg_advisory_lock`을 `Function ... not found`로 거부하는 것을 2026-11-09 직접 실행해 확인했다.
  - 락 키 설계(provider + providerId 해시)와 락 대기가 생긴다. 경쟁은 첫 로그인 때만 생기므로 드문 충돌에 재시도 1회로 충분하다고 보았다.

## 검증
- `MemberRepositoryIntegrationTest`(Postgres, Flyway 적용 스키마)
  - `activeMembersWithSameSocialAccountViolateUniqueIndex`: 같은 `(KAKAO, id)` ACTIVE 2행 → `DataIntegrityViolationException`, 메시지 체인에 `uk_member_social_provider_active`
  - `inactiveAndActiveMembersWithSameSocialAccountAreSaved`: INACTIVE 1행 + ACTIVE 1행 저장됨
  - `activeMembersWithDifferentSocialProvidersAreSaved`: provider가 다르면 같은 provider_id로 ACTIVE 2행 저장됨
- `AuthServiceTest`(mock): 웹·앱 각각 "이 제약 위반 후 성공 → 두 번째 결과, `times(2)`", "다른 제약 위반 → AUTH105, 재시도 없음", "두 번 모두 이 제약 위반 → AUTH105, `times(2)`"
  - `loginWithOAuthRetriesMemberLoginOnceOnMemberSocialDuplicate`, `loginWithOAuthDoesNotRetryOnOtherConstraintViolation`, `loginWithOAuthThrowsAuth105WhenRetryAlsoHitsMemberSocialDuplicate`
  - `issueAppLoginCodeRetriesMemberLoginOnceOnMemberSocialDuplicate`, `issueAppLoginCodeDoesNotRetryOnOtherConstraintViolation`, `issueAppLoginCodeThrowsAuth105WhenRetryAlsoHitsMemberSocialDuplicate`
- `OAuthMemberLoginConcurrencyIntegrationTest`(Postgres, 테스트 트랜잭션 끔, 실제 프록시 빈)
  - `loginWithOAuthRetriesAndLogsInAsExistingMemberWhenLosingFirstLoginRace`: 스레드 A가 `TransactionTemplate`에서 회원을 INSERT하고 커밋하지 않은 채 기다리는 동안, 스레드 B의 `loginWithOAuth`가 `pg_stat_activity`에서 `wait_event_type = 'Lock'`인 `insert into member` 상태가 되는 것과 B가 끝나지 않은 것을 확인한 뒤 A를 커밋한다. B는 예외 없이 `isNewMember=false`, A가 만든 회원 ID의 토큰을 받고, ACTIVE 행은 1건이다.
  - `directMemberLoginThrowsMemberSocialDuplicateWhenLosingFirstLoginRace`: 같은 경쟁에서 재시도 없이 `OAuthMemberLoginService.loginWithToken`을 직접 부르면 이 제약의 `DataIntegrityViolationException`이 난다(재시도를 빼면 실패함을 확인).

## 남은 리스크
- **재시도는 1회뿐이라, 재시도에서도 이 위반이 나면 500 `AUTH105`다.** 계약은 이 경우를 "이론상, 3건 이상 동시 등"으로 적었다. 다만 진 요청의 INSERT는 이긴 트랜잭션이 커밋한 뒤에야 위반으로 끝나므로(동시성 테스트가 확인한 락 대기), 3건 이상이어도 진 요청들은 재시도 조회에서 이긴 회원을 볼 것으로 본다. 3건 이상 경쟁은 테스트하지 않았다. `AUTH105`를 받더라도 다시 로그인하면 기존 회원 경로로 간다.
- **배포 전에 ACTIVE 중복이 없는지 사람이 확인해야 한다.** 중복이 있으면 V25가 실패해 새 앱이 기동하지 않는다(트랜잭션 안이라 인덱스는 남지 않는다). dev와 prod에서 아래 결과가 0건이어야 한다. NULL은 인덱스가 중복으로 보지 않으므로 제외한다. 중복 정리 SQL은 마이그레이션에 넣지 않았다.
  ```sql
  SELECT social_provider, provider_id, count(*) FROM member
  WHERE status = 'ACTIVE' AND provider_id IS NOT NULL
  GROUP BY 1, 2 HAVING count(*) > 1;
  ```
  - dev는 develop 머지 시 바로 배포되므로 PR 머지 직전에, prod는 main 반영 직전에 확인한다.
  - 확인 후 V25 적용 전 사이에 구 버전에서 경쟁이 겹치면 중복이 새로 생길 수 있다. 회원 생성을 멈추는 수단이 없어 중지 대신 실패 시 절차로 통제한다. prod는 `cd.yml`이 새 색상 헬스체크 실패 시 트래픽을 전환하지 않아 기존 색상이 계속 서비스하고, dev는 앱이 기동하지 못한다. 중복을 정리한 뒤 배포 job을 다시 실행한다.
- **커밋 시점에 나는 위반의 재시도는 테스트가 없다.** IDENTITY라 INSERT가 `save` 시점에 나가므로 위반은 거의 항상 INSERT 시점에 난다(동시성 테스트가 확인한 경로). 커밋 시점 위반이 `DataIntegrityViolationException`으로 번역돼 재시도되는지는 확인하지 않았다.
- **동시성 integration 테스트는 웹 경로(`loginWithOAuth`)만 실제 DB로 확인한다.** 앱 경로(`issueAppLoginCode`)의 재시도는 `AuthServiceTest`의 mock으로만 확인한다. 두 경로는 같은 `retryOnMemberRace`를 쓴다.
- **H2 단위 테스트 스키마에는 이 인덱스가 없다.** 테스트용 H2는 Flyway를 끄고 `ddl-auto: create-drop`으로 엔티티에서 스키마를 만들며, 엔티티는 바꾸지 않았다. 제약 동작은 Postgres integration 테스트에서만 확인된다.
- **`provider_id`에 `NOT NULL`을 추가하지 않았다.** main 코드에서 null이 생기는 경로는 없다고 보았지만(계약 조사), PostgreSQL unique 인덱스는 NULL끼리 중복으로 보지 않으므로 `provider_id`가 null인 ACTIVE 행은 이 인덱스로 막히지 않는다.
- 재시도 후에도 실패하는 경우와 그 밖의 예외는 `catch (Exception)`이 원인 예외를 버리고 `AUTH105`로 바꾼다. 원인 로그가 남지 않는 문제는 이번 범위 밖이다.

## 다시 볼 조건
- 재시도 로그(`Member social unique conflict on first login`)가 자주 보이거나, 같은 계정 로그인에서 `AUTH105`가 반복될 때
- 회원 생성 경로가 OAuth 로그인 외에 생길 때(그 경로도 이 제약 위반을 처리해야 한다)
- `member` 테이블이 커져 인덱스 추가·변경에 쓰기 차단 시간이 문제될 때
