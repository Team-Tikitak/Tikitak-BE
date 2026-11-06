# 작업 계약: 공통 감사 필드(BaseTimeEntity) 2단계 적용

- **관련 이슈**: #183
- **브랜치**: `Refactor/#183-base-time-entity-2` (Orca worktree, develop e9f9d92 기준)
- **상태**: 승인됨 (2026-11-06)

## 목표
- 남은 6개 엔티티(Feed, FeedComment, Member, MemberDeviceToken, Notification, RefreshToken)를 1단계 상위 클래스로 옮기고, 엔티티 안의 시각 세팅(팩토리 now, `@PrePersist`/`@PreUpdate`)을 없앤다.
- Feed/FeedComment는 수정 API 응답과 "이미지·태그만 수정해도 `updatedAt` 갱신" 동작을 지키기 위해 `BaseTimeEntity.touch()`를 쓴다.
- Member/MemberDeviceToken의 수동 `updatedAt` 세팅은 없애고 auditing에만 맡긴다(읽는 곳 0건).

## 사람이 정한 것 (2026-11-06)
- Feed/FeedComment 응답 `updatedAt`: `BaseTimeEntity`에 `protected void touch()` 도입
- Member/MemberDeviceToken touch: 버림
- Feed 통합 테스트의 `createdAt` 고정값: `IntegrationTest`에 native update 헬퍼 추가

## 작업 항목

| # | 작업 |
|---|---|
| 1 | Member, MemberDeviceToken → `BaseTimeEntity` / Notification, RefreshToken → `BaseCreatedTimeEntity` |
|   | - `createdAt`/`updatedAt` 필드 선언, 팩토리의 `LocalDateTime now`/`.createdAt(now)`/`.updatedAt(now)`, 도메인 메서드의 `this.updatedAt = ...` 줄 제거 (Member 7곳, MemberDeviceToken 1곳). 다른 필드에 쓰는 now(`deletedAt`, `termsAgreedAt` 등)는 남긴다 |
|   | - `@Builder` → `@SuperBuilder`, `@AllArgsConstructor` 제거 (`new X(` 호출 0건) |
| 2 | `IntegrationTest`에 `created_at` native update 헬퍼 추가, Feed `createdAt` 고정값에 기대는 IT 8개 파일에 적용 |
|   | - 헬퍼는 DB 값(native `UPDATE ... SET created_at`)과 영속성 컨텍스트 안 엔티티 값을 함께 맞춘다. `getCreatedAt()`을 커서로 쓰는 테스트(FeedRegionFilter)가 있기 때문 |
|   | - 대상: TeamMemberRepository, FeedEveryonePick, FeedTag, FeedRegion, FeedRepository, FeedCombination, FeedAllTagged, FeedRegionFilter IT. 단언·데이터 의미는 바꾸지 않는다 |
|   | - 이 커밋은 Feed 엔티티 변경 전이므로 헬퍼가 없어도 통과하는 상태에서 넣는다(3번 이후에도 통과해야 함) |
| 3 | `BaseTimeEntity`에 `protected void touch()` (`updatedAt = LocalDateTime.now()`) 추가. Feed, FeedComment → `BaseTimeEntity` |
|   | - 필드 선언과 `@PrePersist`/`@PreUpdate` 메서드 삭제(시각만 다룸), 도메인 메서드의 `this.updatedAt = ...`를 `touch()`로 교체 (Feed 6곳, FeedComment 2곳. `Feed.delete()`의 `deletedAt = now`는 유지) |
|   | - `@Builder` → `@SuperBuilder`, `@AllArgsConstructor` 제거, Feed의 `@Builder.Default` 4개 유지 |
| 4 | 테스트 추가 (`IntegrationTest` 상속, 새 파일) |
|   | - Feed: 도메인 수정 메서드 호출 직후(flush 전) `getUpdatedAt()`이 이전 값보다 늦다. 태그·이미지만 바꾸는 메서드(`replaceTags` 또는 `syncTags`) 후 flush/clear하면 DB `updated_at`이 이전보다 늦다 |
|   | - FeedComment: `updateContent` 직후(flush 전) `getUpdatedAt()`이 이전보다 늦다 |
|   | - Member: 같은 값으로 `updateSocialProfile` 후 flush/clear하면 `updated_at`이 그대로다. 다른 값이면 늦어진다 |
|   | - Notification/RefreshToken: 팩토리로 만들어 persist하면 `createdAt`이 채워진다 |
|   | - 이전 시각과 비교할 때는 persist 후 native update로 과거 시각을 넣어 동일 시각 오탐을 피한다 |
| 5 | 결정 기록 갱신: `docs/decisions/2026-11-05-base-time-entity.md`에 2단계 결과(touch 도입 이유, Member 계열 touch 제거, IT 헬퍼), `README.md` 요약 줄 |

## 범위: 건드려도 되는 파일
- `src/main/java/kusitms/spin/tikitak/domain/common/BaseTimeEntity.java`
- `src/main/java/kusitms/spin/tikitak/domain/feed/entity/{Feed,FeedComment}.java`
- `src/main/java/kusitms/spin/tikitak/domain/member/entity/Member.java`
- `src/main/java/kusitms/spin/tikitak/domain/notification/entity/{MemberDeviceToken,Notification}.java`
- `src/main/java/kusitms/spin/tikitak/domain/auth/entity/RefreshToken.java`
- 위 엔티티 변경으로 컴파일이 깨지는 main/test 호출부 (`@SuperBuilder` 타입 추론 보완 등, 최소 수정)
- `src/test/java/kusitms/spin/tikitak/support/IntegrationTest.java`, 작업 2의 IT 8개 파일
- 신규 테스트: `src/test/java/kusitms/spin/tikitak/domain/common/` 아래
- `docs/decisions/2026-11-05-base-time-entity.md`, `docs/decisions/README.md`
- 마이그레이션 없음 (6개 테이블 컬럼이 이미 일치: V1, V2, V19, V22)

## 의도된 동작 변경

| 경우 | 이전 | 이후 |
|---|---|---|
| Member를 같은 값으로 수정 (로그인마다 `updateSocialProfile`, 반복 `agreeRequiredTerms`/`completeOnboarding`, 이미 null인 `clearProfileImage` 등) | UPDATE 발생, `updated_at` 갱신 | UPDATE 없음, `updated_at` 그대로 |
| MemberDeviceToken을 같은 회원·플랫폼으로 재등록 (`reassign`) | `updated_at` 갱신 | 그대로 |
| Member/MemberDeviceToken 값이 실제로 바뀌는 수정 | 도메인 메서드 시각 | flush 시각 (µs 차이) |
| 4개 팩토리(Member, MemberDeviceToken, Notification, RefreshToken)로 만든 객체의 `createdAt` | 팩토리 호출 시각 | persist 시각. JPA 없는 단위 테스트에서는 null (단언하는 테스트 없음) |
| Feed/FeedComment에 `createdAt`을 미리 넣고 persist | 넣은 값 유지 (`if null` 가드) | auditing이 덮어씀. main에는 이런 호출 없음 |
| 6개 엔티티 UPDATE문 | `created_at` 포함 | 빠짐 (`updatable = false`). 값 변화 없음 |
| Feed/FeedComment 수정 응답 `updatedAt`, 이미지·태그만 수정 시 DB `updated_at` | 도메인 메서드 시각 / 갱신 | 같음 (touch 유지) |

- 트랜잭션 경계, flush 위치, 예외 경로 변경: 없음

## 완료 조건
- [ ] 6개 엔티티 파일에 `private LocalDateTime createdAt`/`updatedAt` 선언, `@PrePersist`, `@PreUpdate`가 0건이다.
- [ ] Member, MemberDeviceToken, Notification, RefreshToken 파일에 `updatedAt =`, `.createdAt(`, `.updatedAt(`가 0건이다.
- [ ] Feed, FeedComment 파일에 `updatedAt =`가 0건이고 `touch()` 호출이 각각 6곳, 2곳이다.
- [ ] `BaseTimeEntity`에 `protected void touch()`가 있고, 다른 콜백 메서드는 없다.
- [ ] 작업 4의 테스트가 있고, 각 테스트가 주장대로 검증한다 (flush 전 단언은 flush 전에, 같은 값 수정은 실제로 같은 값으로).
- [ ] 작업 2의 IT 8개 파일이 단언 변경 없이 통과한다.
- [ ] `./gradlew test`, `./gradlew integrationTest`, `./gradlew spotlessCheck` 통과.
- [ ] 결정 기록의 수치(수정 위치 수, 파일 수)가 grep 결과와 일치한다.

## 커밋 구성
1. Member, MemberDeviceToken, Notification, RefreshToken 상위 클래스 적용 (작업 1)
2. IT `created_at` native update 헬퍼 도입 (작업 2)
3. `touch()` 도입, Feed/FeedComment 상위 클래스 적용 + 작업 4 테스트 (작업 3, 4)
4. 결정 기록 갱신 (작업 5)

## 하지 말 것
- `deletedAt` 이동, 커스텀 `DateTimeProvider`/`AuditorAware` 추가, `@EnableJpaAuditing` 위치 변경
- 서비스 계층에 flush 추가 등 트랜잭션·flush 위치 변경
- Member/MemberDeviceToken에 `touch()` 사용
- IT 단언이나 데이터 의미 변경, 범위 밖 리팩터링
- 사람 승인 없이 커밋/푸시

## 참고
- 결정 기록 `docs/decisions/2026-11-05-base-time-entity.md` (2단계 사유, `@SuperBuilder` 타입 추론, createdAt 덮어쓰기)
- 1단계 계약 `docs/contracts/2026-11-05-base-time-entity.md`, 대조 예시 `Team.java`
- 기존 패턴: native update로 시각 바꾸기 `BaseTimeEntityIntegrationTest.java:33-42`
- lessons-log: 픽스처 시각의 숨은 전제(2026-11-05), 문서 수치 재확인(verifier 5번)
- 확인 못 한 것: inverse 컬렉션만 바뀌면 dirty가 아니라는 점과 `AuditingHandler`의 createdAt 덮어쓰기는 알려진 동작 기준. 작업 2·4 테스트와 `integrationTest`로 판정
