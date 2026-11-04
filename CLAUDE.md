# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

티키탁(Tikitak) 백엔드 — 대학생 팀 프로젝트의 사진 피드, 지도 핀, 데일리 질문 답변을 함께 기록하는 팀 단위 아카이브 서비스의 API다. 
Java 21, Spring Boot 4, PostgreSQL + Flyway, Redis, JPA/Hibernate, 미디어 저장은 Cloudflare R2(S3 호환), 푸시 알림은 Firebase Admin SDK, 인증은 OAuth2(Google/Kakao/Apple) + JWT를 사용한다.

## Architecture

최상위는 계층별(package-by-layer), 그 아래는 기능별(package-by-feature) 구조이다: `controller/<feature>`, `service/<feature>`, `repository/<feature>`, `domain/<feature>/entity|enums`. 도메인이 쓰는 값 타입(예: 알림 payload)은 `domain/<feature>/dto`, repository가 반환하는 native query projection은 `repository/<feature>/projection`에 둔다. 기능별로 이름(auth, feed, team, media, dailyquestion, map, home, me, member, notification, place, system)이 네 계층 전체에서 동일하게 유지되므로, 하나의 기능을 이해하려면 각 계층에서 같은 하위 패키지를 함께 보면 된다.

`global/`은 공통 처리를 모아둔 곳이다:
- `global/config` — Spring 설정 클래스 (`SecurityConfig`, `R2Config`, `FirebaseConfig`, `KakaoMapProperties` 등)
- `global/security` — JWT 인증: `TokenAuthenticationFilter`가 `SecurityContext`에 `AuthPrincipal`을 채워 넣고, `@CurrentMemberId` + `CurrentMemberIdArgumentResolver`가 인증된 회원 ID를 컨트롤러 메서드 파라미터에 바로 주입한다 (미인증 시 `BusinessException(ErrorCode.AUTH009)` 발생).
- `global/exception` — `GlobalExceptionHandler`(`@RestControllerAdvice`) + `ErrorCode` enum + `BusinessException`. 비즈니스 규칙 실패는 임의의 예외 대신 `new BusinessException(ErrorCode.XXX)`를 던져야 하며, 새로운 실패 케이스는 `ErrorCode`에 항목을 추가한다.
- `global/dto` — `CommonResponse<T>` / `ErrorResponse`가 모든 엔드포인트의 표준 성공/에러 응답 포맷이다. `PatchField<T>`는 PATCH 요청 DTO에서 "필드가 아예 없음"과 "필드를 명시적으로 null로 설정함"을 구분하기 위한 3상태(tri-state) 래퍼(`undefined()` vs `of(value)`, `of(null)` 포함)이며, Jackson 연동은 `PatchFieldDeserializer`를 참고.

인증 흐름: OAuth2 authorization-code 교환(`/api/v1/auth/oauth/*/start|callback`)으로 JWT access/refresh 토큰을 발급하며, OAuth state와 로그인 코드는 `tikitak:<profile>:...` 키 프리픽스로 Redis에 저장된다. 인증이 필요 없는 엔드포인트는 `SecurityConfig`에서 명시적으로 화이트리스트로 등록하고, 그 외 나머지는 모두 유효한 access token이 필요하다.

DB 스키마는 `src/main/resources/db/migration` 아래 Flyway로 관리됨. (`V1__...`부터 순차적으로 추가만 되며, 새 파일 번호는 마지막 번호 + 1). `ddl-auto`는 `none`이므로, 엔티티를 변경할 때는 Hibernate 자동 생성이 아니라 반드시 새 마이그레이션 파일을 함께 추가해야 함.

미디어 업로드(S3/R2)는 presigned URL 방식이다: 클라이언트가 presigned PUT을 요청 → R2에 직접 업로드 → 완료 엔드포인트 호출로 검증 및 미디어 레코드 상태 전환. 아웃박스 패턴(`ObjectDeleteOutbox*`)이 고아/삭제된 오브젝트의 비동기 삭제를 처리함.

## Commands

```bash
# Build
./gradlew build

# Run (로컬 Postgres/Redis 필요, 또는 application.yaml 참고해 env 지정)
./gradlew bootRun

# Unit test only (@Tag("integration") 제외)
./gradlew test

# Integration tests (Testcontainers 사용 — Docker 실행 필요)
./gradlew integrationTest

# Single test class / method
./gradlew test --tests "kusitms.spin.tikitak.service.feed.FeedServiceTest"
./gradlew test --tests "kusitms.spin.tikitak.service.feed.FeedServiceTest.methodName"

# check는 test와 integrationTest를 모두 실행
./gradlew check

# 포맷 적용 / 검사 (Spotless: 탭 들여쓰기, 줄 끝 공백, 파일 끝 개행, import 정리. CI는 spotlessCheck 실패 시 막힘)
./gradlew spotlessApply
./gradlew spotlessCheck
```

## Testing conventions

`src/test/java/.../support` 아래 3개의 베이스 클래스가 있음:
- `UnitTest` — `@ExtendWith(MockitoExtension.class)`, Spring 컨텍스트 없음. 레포지토리/협력 객체를 mock한 서비스 계층 테스트에 사용함.
- `ApiTest extends UnitTest` — 컨트롤러별로 standalone `MockMvc`를 구성함. (`mockMvc(controller)`). `GlobalExceptionHandler`가 연결되어 있고, `@CurrentMemberId`는 고정된 `TEST_MEMBER_ID`로 resolve됨. 전체 Spring 컨텍스트 없이 컨트롤러 테스트에 사용함.
- `IntegrationTest` — `@Tag("integration")`, Testcontainers로 실제 `PostgreSQLContainer` 사용, `@DataJpaTest`, `integration-test` 프로필. 엔티티 빌더 헬퍼(`member(...)`, `team(...)`, `teamMember(...)`)와 `persist`/`flushAndClear`를 제공함. 이 테스트들만 `integrationTest`에서 실행되며, 일반 `test` 태스크는 `integration` 태그가 붙은 테스트를 제외함.

테스트 트리에서 알아두면 좋은 다른 패턴들:
- `support/fixture/*Fixture` — FixtureMonkey/수동 object-mother 스타일로 요청/도메인 객체를 만드는 픽스처.
- `support/fake/**` — 전체 Spring 컨텍스트 없이 여러 협력 레포지토리가 필요한 테스트를 위해 직접 작성한 fake repository/publisher 구현체.
- `*PropertyTest` 클래스는 jqwik을 사용한 property-based testing으로 validator를 검증한다. (예: `FeedCommentValidatorPropertyTest`, `AuthServicePropertyTest`).
- `architecture/` — ArchUnit으로 위 Architecture/Conventions 규칙을 강제한다(`LayerDependencyArchTest`, `ConventionArchTest`, 테스트 클래스는 검사 제외). 기존 위반은 규칙별 예외 상수에 이유와 함께 명시돼 있으며, 새 예외 추가는 리뷰 대상이다([결정 기록](docs/decisions/2026-11-02-archunit-explicit-exceptions.md)).

## Conventions

- 새 엔드포인트는 `CommonResponse.success(data)` / `successWithStatus(data, status)`로 응답하고, 실패는 raw exception이나 수동으로 만든 에러 `ResponseEntity`가 아니라 `BusinessException` + `ErrorCode`로 처리함.
- "설정 안 함"과 "명시적 null"을 구분해야 하는 부분 수정(PATCH) DTO는 일반 nullable 필드가 아니라 `PatchField<T>` 필드를 사용함.
- 설정값은 여기저기 흩어진 `@Value` 대신 `global/config`의 `*Properties` 클래스(예: `R2Properties`, `AuthProperties`, `FirebaseProperties`)를 통해 바인딩함.
- 커밋 전에 수정한 Java 파일에 Spotless를 적용한다. 포맷은 손으로 맞추지 않고 Spotless 결과를 그대로 쓴다([결정 기록](docs/decisions/2026-11-04-spotless-minimal-rules.md)).
  - 작업 트리에 이번 작업과 관계없는 미커밋 Java 파일이 없으면 `./gradlew spotlessApply`. 기존 파일은 이미 정리돼 있으므로 수정한 파일만 바뀐다.
  - 관계없는 미커밋 Java 파일이 있으면 `spotlessApply`가 그 파일까지 고치므로, 수정한 파일마다 `./gradlew spotlessApply -PspotlessIdeHook=<파일 절대경로>`를 쓴다(한 번에 파일 하나).
  - text block 안의 앞쪽 공백도 탭 단위로 바뀐다. 공백에 의미가 있는 text block을 추가하면 결정 기록의 "남은 리스크"를 따른다.

## Git workflow

- **모든 git commit은 실행 전에 반드시 사용자의 검사(review)를 받는다.** 테스트를 통과했거나 작업이 끝났다는 이유만으로 자동으로 커밋하지 않는다. 작업을 여러 단계로 나눠 진행할 때는 각 단계가 끝날 때마다 diff를 보여주고 승인을 받은 뒤에만 커밋한다.

## 작업 하네스

- 작업은 `docs/contracts/TEMPLATE.md` 양식의 짧은 계약으로 시작하고, 사람이 승인한 뒤 구현한다.
- 워커 역할은 `.claude/agents/`에 정의돼 있다: `researcher`(조사, 읽기 전용) → `implementer`(계약 범위 안에서 구현) → `verifier`(별도 컨텍스트에서 완료 조건 판정, 수정 안 함).
- 실수·실패는 `docs/lessons-log.md`에 한 줄로 남기고, 같은 실수가 두 번째 나오면 린터/테스트/설정/이 파일의 규칙으로 승격한다.
- 설계 판단은 `docs/decisions/`에 결정 하나당 파일 하나로 남긴다.
