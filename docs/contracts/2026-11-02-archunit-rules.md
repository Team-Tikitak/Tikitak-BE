# 작업 계약: ArchUnit으로 CLAUDE.md 아키텍처·컨벤션 규칙 강제

- **관련 이슈**: 없음 (하네스 Step 5). 기존 위반을 고치는 후속 작업은 별도 이슈로 만든다.
- **브랜치**: `chore/archunit-rules` (develop에서 분기, worktree `.claude/worktrees/archunit`)
- **상태**: 승인됨 (2026-11-03)

## 목표
- CLAUDE.md의 Architecture/Conventions 중 기계적으로 판정할 수 있는 규칙을 ArchUnit 테스트로 만든다. 사람과 AI 워커 모두 `./gradlew test`에서 위반을 바로 알 수 있게 한다.
- 기존 위반은 테스트 코드 안에 **명시적 예외 목록**(클래스 이름과 이유 주석)으로 허용한다. 새 위반만 막는다.
- 프로덕션 코드(`src/main`)는 바꾸지 않는다.

## 규칙 (조사: 2026-11-02 researcher, develop 1826624 기준)

`LayerDependencyArchTest`

| # | 규칙 | 기존 위반 → 예외 목록 |
|---|---|---|
| L1 | `..controller..`는 `..repository..`, `..domain..entity..`에 의존하지 않는다 | 없음 |
| L2 | `..service..`는 `..controller..`에 의존하지 않는다 | 없음 |
| L3 | `..domain..`은 `..service..`, `..controller..`, `..repository..`에 의존하지 않는다 | `domain.notification.entity.Notification` (`NotificationPayload` 사용) |
| L4 | `..repository..`는 `..service..`, `..controller..`에 의존하지 않는다 | `repository.feed.FeedRepository` (`RegionRow`, `MapPinRow` projection) |
| L5 | `domain`의 기능 slice(`..domain.(*)..`) 사이에 순환이 없다 | 없음 (추정. 실행해서 위반이 나오면 동결하지 말고 멈추고 보고) |

`ConventionArchTest`

| # | 규칙 | 기존 위반 → 예외 목록 |
|---|---|---|
| C1 | `@RestController`는 `..controller..`에 있고 이름이 `Controller`로 끝난다 | 없음 |
| C2 | `@Transactional`(클래스·메서드)은 `..service..`에서만 쓴다 | 없음 |
| C3 | `@ConfigurationProperties`는 `..global.config..`에만 있다 | 없음 |
| C4 | `@Entity`는 `..domain.*.entity..`에만 있다 | `domain.auth.RefreshToken` |
| C5 | `..domain.*.entity..`의 top-level 클래스는 `@Entity`다 (Lombok builder 같은 중첩 클래스 제외) | 없음 |
| C6 | `PatchField` 타입 필드에는 `@JsonDeserialize(using = PatchFieldDeserializer.class)`가 있다 | 없음 |
| C7 | `@RestController`의 핸들러 메서드는 `ResponseEntity<CommonResponse<…>>` 또는 `CommonResponse<…>`를 반환한다 (제네릭 인자까지 검사) | `AuthController#startOAuthLogin`, `#handleOAuthCallback`, `#handleAppleOAuthCallback` (OAuth 302 리다이렉트) |
| C8 | `..service..`는 `ResponseEntity`, `CommonResponse`, `jakarta.servlet..`에 의존하지 않는다 | 없음 |
| C9 | `..service..`, `..controller..`는 `IllegalArgumentException`, `IllegalStateException`, `RuntimeException`, `UnsupportedOperationException`을 직접 생성하지 않는다 (`BusinessException` + `ErrorCode` 사용) | `service.auth.TokenService`, `service.auth.RedisLoginCodeStore`, `service.media.ImageUrlResolver` (인프라·설정 실패) |
| C10 | `@Value`는 `..global.config..` 밖에서 쓰지 않는다 (`*Properties` 사용) | `service.auth.RedisOAuthStateStore`, `service.auth.RedisLoginCodeStore` |
| C11 | 표준 출력(`System.out/err`, `printStackTrace`)과 `java.util.logging`을 쓰지 않는다 (`GeneralCodingRules`) | 없음 |

예외 목록 작성 규칙은 다음과 같다.
- 규칙마다 상수(예: `Set<String>`)로 둔다.
- 항목마다 **이유와 후속 조치**를 한 줄 주석으로 남긴다.
- `because(...)`에 CLAUDE.md 근거를 적는다. 그래야 실패 메시지에서 이유가 보인다.

## 범위: 건드려도 되는 파일
- `build.gradle`: `testImplementation 'com.tngtech.archunit:archunit:<버전>'` 한 줄만 추가한다.
  - `archunit-junit5` 엔진이 아니라 core를 쓰고, 테스트는 JUnit Jupiter `@Test`로 작성한다.
- `src/test/java/kusitms/spin/tikitak/architecture/LayerDependencyArchTest.java` (신규)
- `src/test/java/kusitms/spin/tikitak/architecture/ConventionArchTest.java` (신규)
- (필요하면) `src/test/java/kusitms/spin/tikitak/architecture/ArchitectureClasses.java`: 공용 `JavaClasses` import. 테스트 클래스는 제외한다.
- `docs/decisions/2026-11-02-archunit-explicit-exceptions.md` (신규), `docs/decisions/README.md` (인덱스 한 줄)
- `CLAUDE.md`: Testing conventions에 `architecture/` 패키지 설명 한 줄

## 완료 조건
- [ ] `./gradlew test` 통과 (이 worktree 기준)
- [ ] 위 규칙 L1~L5, C1~C11이 각각 별도 테스트 메서드로 존재한다. 메서드 이름이나 `@DisplayName`에 규칙 번호가 들어간다.
- [ ] 예외 목록은 위 표에 적힌 클래스와 메서드만 포함한다. 각 항목에 이유 주석이 있다.
- [ ] **규칙이 실제로 위반을 잡는지 확인**: 규칙마다 예외 목록에서 항목을 하나 빼거나 위반 코드를 임시로 넣었을 때 테스트가 실패하는지 직접 돌려 본다. 결과를 보고에 규칙별로 적고, 임시 변경은 되돌린다. 위반 0인 규칙은 잡을 대상이 없으므로 대표 규칙 몇 개만 임시 위반 클래스로 확인한다.
- [ ] 사용한 ArchUnit 버전과 JUnit 버전(`testRuntimeClasspath` 기준)을 보고한다.
- [ ] 결정 기록 작성: 동결(FreezingArchRule) 대신 명시적 예외를 고른 이유, 규칙으로 만들지 않은 것(기능 간 의존, PatchField 강제, `LocalDateTime.now()`, OAuth 예외 24건)과 그 이유

## 하지 말 것
- `src/main` 수정. DTO 이동, `RefreshToken` 이동, `@Value` 전환은 후속 이슈에서 한다.
- `FreezingArchRule`이나 `archunit_store` 사용
- 위 예외 목록 외에 특정 패키지를 검사 대상에서 빼는 설정
- 표에 없는 규칙 추가. 추가하고 싶은 규칙은 보고에 "제안"으로만 적는다.
- `build.gradle`에서 의존성 한 줄 외의 변경
- 사람 승인 없이 커밋/푸시

## 참고
- `docs/lessons-log.md` 규칙 2: ArchUnit은 "가장 강한 승격 수단"이다. 이번 도입은 lessons 승격이 아니라 CLAUDE.md 규칙을 기계적으로 강제하는 작업이다.
