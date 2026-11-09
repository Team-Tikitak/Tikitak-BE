# 작업 계약: 회원 (social_provider, provider_id) unique 제약과 동시 가입 처리

- **관련 이슈**: #181
- **브랜치**: `Fix/#181-social_provider_unique` (Orca worktree, develop a93e26e 기준)
- **상태**: 완료 (2026-11-09, PR #189)

## 목표
- 같은 소셜 계정으로 ACTIVE 회원이 두 명 생기지 않게 DB에서 막는다.
- 첫 로그인이 동시에 두 번 들어와도 두 요청 모두 같은 회원으로 로그인에 성공한다.

## 사람이 정한 것 (2026-11-09)
- 인덱스는 **부분 unique**: `(social_provider, provider_id) WHERE status = 'ACTIVE'`. 이유: 조회 조건(`findBySocialProviderAndProviderIdAndStatus(..., ACTIVE)`)과 의미가 같다. 또 #71(2026-05-23) 이전에 탈퇴한 행은 원래 provider_id를 그대로 갖고 있을 수 있는데, 이런 행이 있어도 마이그레이션이 통과한다.
- 생성 방식은 **일반 `CREATE UNIQUE INDEX`**(트랜잭션 안에서 실행). CONCURRENTLY는 쓰지 않는다.
- 위반 처리: **AuthService에서 `OAuthMemberLoginService` 호출만 1회 재시도**한다.
- 경쟁에서 진 요청의 `isNewMember`는 **false**다. 재시도가 기존 회원 경로로 가기 때문이다.
- 아래는 조사 때 낸 추천을 따른다.
  - `provider_id NOT NULL`은 추가하지 않는다(main 코드에서 null이 생기는 경로 없음).
  - 재시도 후에도 실패하면 지금처럼 AUTH105를 낸다.
  - `catch (Exception)`이 원인 예외를 버리는 문제는 범위 밖으로 둔다.
  - 기존 ACTIVE 중복은 배포 전에 사람이 쿼리로 확인한다(아래 "배포 전 확인").

## 항목별 작업

| # | 작업 |
|---|---|
| 1 | `V25__add_member_social_provider_active_unique.sql`: `CREATE UNIQUE INDEX uk_member_social_provider_active ON member (social_provider, provider_id) WHERE status = 'ACTIVE';` 한 문장만 둔다. `executeInTransaction` 지시자는 쓰지 않는다. 엔티티(`Member`)는 바꾸지 않는다. |
| 2 | `AuthService`에 상수 `MEMBER_SOCIAL_ACTIVE_UNIQUE = "uk_member_social_provider_active"`와 private 메서드 `isMemberSocialDuplicate(Throwable)`를 둔다. 메서드는 cause 체인을 돌며 메시지에 상수가 있는지 본다(`DailyQuestionService.isDailyAnswerDuplicate` 형태). |
| 3 | `AuthService`에 private 헬퍼 `<T> T retryOnMemberRace(Supplier<T>)`를 둔다. 첫 호출에서 `DataIntegrityViolationException`이 났고 `isMemberSocialDuplicate`가 참이면, `log.info`(provider만 넣고 providerId·이메일은 넣지 않음)를 남긴 뒤 **한 번만** 다시 호출한다. 그 밖의 예외와 두 번째 호출의 예외는 그대로 던진다. |
| 4 | `loginWithOAuth`(5인자)의 `oauthMemberLoginService.loginWithToken(userInfo)`와 `issueAppLoginCode`(5인자)의 `oauthMemberLoginService.prepareLoginCode(userInfo)`를 헬퍼로 감싼다. 감싸는 위치는 기존 `try` 안이다. OAuth 외부 호출(`fetchOAuthUserInfo`)과 `loginCodeStore.save`는 재시도하지 않는다. 기존 `catch` 구조는 바꾸지 않는다. |
| 5 | 결정 기록 `docs/decisions/2026-11-09-member-social-unique-retry.md`를 쓰고 README 목록에 한 줄 추가한다. 내용: 부분 인덱스를 고른 이유, 같은 트랜잭션에서 catch 후 재조회할 수 없는 이유(PG abort와 rollback-only), ON CONFLICT·REQUIRES_NEW를 고르지 않은 이유, 남은 리스크. `docs/decisions/2026-11-05-external-call-outside-transaction.md`에 적힌 "후속 이슈" 문구는 고치지 않고, 새 기록에서 그 문서를 링크한다. |

## 의도된 동작 변경

진입점은 4개다. GET `/api/v1/auth/oauth/{provider}/callback` 웹과 앱(`oauthMode=app`), POST `/api/v1/auth/oauth/apple/callback` 웹과 앱이다. 웹은 `loginWithOAuth`, 앱은 `issueAppLoginCode`를 탄다. `/login-code/exchange`는 `findOrCreateMember`를 부르지 않으므로 영향이 없다.

| 경우 | 진입점 | 이전 | 이후 |
|---|---|---|---|
| 같은 계정의 첫 로그인 2건이 동시에 들어옴 | 웹 | 둘 다 200, ACTIVE 회원 2행 | 둘 다 200, 회원 1행. 이긴 쪽 `isNewMember=true`, 진 쪽 `false`. 두 응답의 회원은 같다 |
| 같음 | 앱 | 둘 다 loginCode 발급, 회원 2행 | 둘 다 loginCode 발급, 회원 1행. 진 쪽 payload `newMember=false` |
| ACTIVE 중복이 이미 있는 계정으로 로그인 | 4개 모두 | 500 AUTH105 (`Optional` 결과 2건 예외) | 변화 없음. 배포 전 확인으로 이런 데이터가 없게 한다 |
| 재시도에서도 이 제약 위반이 또 남 (이론상, 3건 이상 동시 등) | 웹/앱 | 경로 없음 | 500 AUTH105 |
| 다른 제약의 `DataIntegrityViolationException` | 웹/앱 | 500 AUTH105 | 변화 없음(재시도 안 함) |
| 회원 트랜잭션 시작 실패 / 커밋 실패 | 웹/앱 | 500 AUTH105 | 변화 없음. 커밋 실패 원인이 이 제약이면(IDENTITY라 INSERT가 즉시 나가서 가능성은 낮음) 1회 재시도 |
| 첫 시도 커밋 성공 후 `loginCodeStore.save` 실패 | 앱 | 500 AUTH105, 회원은 남음 | 변화 없음 |
| 탈퇴 회원이 같은 소셜 계정으로 재가입 | 4개 모두 | 새 ACTIVE 행 생성 | 변화 없음(탈퇴 행은 INACTIVE라 인덱스 대상이 아님) |

## 배포 전 확인 (사람)
dev와 prod에서 아래 쿼리 결과가 0건이어야 V25가 통과한다. 결과가 있으면 V25가 실패해 앱이 기동하지 않는다.
```sql
SELECT social_provider, provider_id, count(*) FROM member
WHERE status = 'ACTIVE' GROUP BY 1, 2 HAVING count(*) > 1;
```

## 범위: 건드려도 되는 파일
- `src/main/resources/db/migration/V25__add_member_social_provider_active_unique.sql` (신규)
- `src/main/java/kusitms/spin/tikitak/service/auth/AuthService.java`
- `src/test/java/kusitms/spin/tikitak/service/auth/AuthServiceTest.java`
- `src/test/java/kusitms/spin/tikitak/repository/member/MemberRepositoryIntegrationTest.java`
- `src/test/java/kusitms/spin/tikitak/service/auth/OAuthMemberLoginConcurrencyIntegrationTest.java` (신규)
- `docs/decisions/2026-11-09-member-social-unique-retry.md` (신규), `docs/decisions/README.md`
- 이 계약 파일(상태 갱신)

## 완료 조건
- [ ] `./gradlew test`, `./gradlew integrationTest`, `./gradlew spotlessCheck` 통과(ArchUnit 포함)
- [ ] `MemberRepositoryIntegrationTest`(Postgres, Flyway 적용 스키마)
  - 같은 `(KAKAO, id)`로 ACTIVE 2행을 flush하면 `DataIntegrityViolationException`이 나고, 메시지 체인에 `uk_member_social_provider_active`가 있다.
  - 같은 `(KAKAO, id)`로 INACTIVE 1행과 ACTIVE 1행은 저장된다.
  - provider가 다르면(KAKAO/GOOGLE) 같은 provider_id로 ACTIVE 2행이 저장된다.
- [ ] `AuthServiceTest`. `loginWithOAuth`와 `issueAppLoginCode` 각각 확인한다.
  - 첫 호출이 이 제약 이름을 담은 DIVE, 두 번째가 성공: 두 번째 결과를 반환한다. `oauthMemberLoginService` 호출은 `times(2)`, `fetchOAuthUserInfo`가 쓰는 OAuth 서비스와 state store 호출은 1회다.
  - 다른 제약 이름의 DIVE: AUTH105, `times(1)`
  - 두 번 모두 이 제약 DIVE: AUTH105, `times(2)`
  - 앱 경로: 재시도해서 성공하면 `loginCodeStore.save`가 1회 불린다.
- [ ] `OAuthMemberLoginConcurrencyIntegrationTest`(Postgres, 테스트 트랜잭션 끔, 프록시가 붙은 실제 `AuthService`·`OAuthMemberLoginService`, OAuth 서비스·state store·loginCode store는 mock)
  - **결정적 재현**: 스레드 A가 `TransactionTemplate` 안에서 같은 계정의 ACTIVE 회원을 INSERT하고 커밋하지 않은 채 기다린다. 그동안 스레드 B가 `loginWithOAuth`를 호출하면, B의 조회는 A의 행을 못 보고 B의 INSERT는 A를 기다리며 막힌다. 이때 A가 커밋하면 B는 200 응답(예외 없음)을 받고, `isNewMember=false`이며, 회원 ID가 A가 만든 회원과 같다. B가 막혔는지는 A 커밋 전에 B가 끝나지 않았음으로 확인한다.
  - 끝난 뒤 그 `(provider, providerId)`의 ACTIVE 행은 1건이다.
  - **재시도를 끄면 실패하는지** 확인한다: 같은 시나리오에서 `OAuthMemberLoginService`를 직접 호출하면(AuthService 재시도 없음) 이 제약의 DIVE가 난다.
  - `AopUtils.isAopProxy`로 프록시가 붙었는지 확인하고, 만든 데이터는 테스트 안에서 정리한다.
- [ ] `AuthService`의 `fetchOAuthUserInfo`, `loginCodeStore.save` 호출이 헬퍼 밖에 있다(재시도 대상 아님).

## 커밋 구성
1. `V25` 마이그레이션 + `MemberRepositoryIntegrationTest` 제약 케이스
2. `AuthService` 재시도 + `AuthServiceTest` + `OAuthMemberLoginConcurrencyIntegrationTest`
3. 결정 기록 + README

## 하지 말 것
- `Member` 엔티티, `OAuthMemberLoginService`, `MemberRepository` 수정 (같은 트랜잭션 안의 catch, ON CONFLICT, REQUIRES_NEW 금지)
- `catch (Exception)` → AUTH105 구조나 다른 ErrorCode 변경, 새 ErrorCode 추가
- 기존 중복 데이터를 정리하는 SQL을 마이그레이션에 넣는 것
- CONCURRENTLY, `executeInTransaction=false`
- 범위 밖 리팩터링, 손으로 하는 포맷 정리
- 사람 승인 없이 커밋/푸시

## 참고
- 조사: `OAuthMemberLoginService.java:54-79`, `AuthService.java:74-111`, `Member.java:105-111`(탈퇴 마커), `DailyQuestionService.java:49,116-121,262-272`(제약 이름 판별), `TokenServiceConcurrencyIntegrationTest.java`(동시성 테스트 골격)
- `docs/decisions/2026-11-05-external-call-outside-transaction.md`(별도 빈, self-invocation 금지, 이 경쟁을 후속 이슈로 명시)
- `docs/decisions/2026-09-21-flyway-concurrent-index-deadlock.md`
- lessons-log: 진입점별 응답 표 누락(#168, #161, #163), CONCURRENTLY 데드락(#154)
