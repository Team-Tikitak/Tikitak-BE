# 감사 필드는 domain/common의 2단계 상위 클래스와 Spring Data auditing으로 모은다

- **날짜**: 2026-11-05
- **관련 이슈**: #159 (계약 `docs/contracts/2026-11-05-base-time-entity.md`)
- **상태**: 결정됨, 1단계 적용 완료 (2단계는 후속)

## 상황 (Context)
- 엔티티마다 `createdAt`/`updatedAt`을 따로 선언하고, 값을 넣는 방식도 세 가지가 섞여 있었다.
  - `@CreatedDate`/`@LastModifiedDate` + 엔티티별 `@EntityListeners(AuditingEntityListener.class)`: Team, TeamMember, TeamInvite
  - 엔티티 안의 `@PrePersist`/`@PreUpdate`: FeedImage, FeedReaction, Place, Question, Media, MediaUpload, ObjectDeleteOutbox, Feed, FeedComment
  - 팩토리 메서드에서 빌더로 `LocalDateTime.now()` 세팅: Member, MemberDeviceToken, Notification, RefreshToken
- 일부 `@PrePersist`는 `if (createdAt == null)` 가드가 있었고, 일부는 무조건 덮어썼다.
- `@EnableJpaAuditing`은 이미 `TikitakApplication`에 있었다.

## 결정 (Decision)
- `domain/common`에 `@MappedSuperclass` 상위 클래스 2개를 둔다. 둘 다 `abstract`이다.
  - `BaseCreatedTimeEntity`: `@EntityListeners(AuditingEntityListener.class)`, `@CreatedDate @Column(nullable = false, updatable = false) createdAt`
  - `BaseTimeEntity extends BaseCreatedTimeEntity`: `@LastModifiedDate @Column(nullable = false) updatedAt`
  - 상위 클래스에는 JPA 콜백 메서드를 두지 않는다.
- 하위 엔티티는 `@Builder` 대신 `@SuperBuilder`를 쓴다.
- 시간 소스는 Spring Data 기본값(`CurrentDateTimeProvider`)을 그대로 쓴다. 커스텀 `DateTimeProvider`와 `AuditorAware`는 두지 않는다.
- `deletedAt`은 상위 클래스에 넣지 않는다.
- 1단계 적용 대상은 동작이 바뀌지 않는 10개다.
  - `BaseCreatedTimeEntity`: TeamMember, TeamInvite, FeedImage, FeedReaction, Place
  - `BaseTimeEntity`: Team, Question, Media, MediaUpload, ObjectDeleteOutbox

## 이유 (Rationale)
- **위치 `domain/common`**: `domain/<feature>/entity`에 두면 ArchUnit C5("entity 패키지의 최상위 클래스는 `@Entity`")에 걸려 규칙 예외가 필요하다. `global/`에 두면 domain이 global에 의존하는 첫 사례가 된다. `domain/common`은 규칙을 바꾸지 않고 두 문제를 모두 피한다.
- **2단계 계층**: `updated_at` 컬럼이 없는 테이블(team_member, team_invite, place, feed_reaction, feed_image, 2단계의 notification, refresh_token)이 있다. 클래스 하나로 합치면 이 테이블들에 컬럼을 추가하는 마이그레이션이 필요하다.
- **`@SuperBuilder`**: 테스트가 엔티티 빌더로 시각을 넣는 곳이 있다. 대상 10개 엔티티 기준으로 `.createdAt(...)` 14곳, `.updatedAt(...)` 5곳이고, #162 KST 회귀 테스트(`DailyQuestionServiceTest`)의 고정 시각도 여기에 들어 있다. 하위 클래스의 `@Builder`는 상위 필드를 다루지 못한다. `@SuperBuilder`를 쓰면 이 호출을 그대로 둘 수 있고, 테스트를 문자열 필드명 기반 `ReflectionTestUtils`로 바꾸지 않아도 된다.
- **콜백 메서드를 두지 않음**: 상위 클래스에 `onCreate()` 같은 콜백을 두면, 같은 이름을 쓰는 하위 클래스(TeamMember, Media, MediaUpload, ObjectDeleteOutbox)가 이를 오버라이드해 한쪽이 실행되지 않는다. 시각 외의 일(`lastActivityCheckedAt`, `publicId`, `status` 기본값)은 각 엔티티의 콜백에 남긴다.
- **`DateTimeProvider` 미도입**: KST 결정([기록](2026-11-04-kst-boundaries-without-tz-change.md))은 "시스템 zone 기준 시각을 저장한다"를 전제로 한다. KST provider를 넣으면 그 기록에서 기각한 B안처럼 저장 시각이 끊긴다.
- **`deletedAt` 제외**: `deletedAt` 컬럼을 가진 엔티티는 일부(Feed, Media, Member, Team, TeamMember)뿐이고, FeedComment는 `is_deleted` boolean으로 soft delete하는 등 삭제 방식과 조회 조건이 엔티티마다 다르다.

## 2단계로 미룬 것
- **Feed, FeedComment**: 수정 직후 같은 트랜잭션에서 flush 전에 `getUpdatedAt()`으로 응답을 만든다(`FeedCommentService.updateComment` → `toCommentItem`, `FeedService.updateFeed` → `toMutation`). 도메인 메서드의 수동 `updatedAt = now()`를 지우면 `@LastModifiedDate`가 flush 때 들어가므로 응답에 수정 전 시각이 나간다.
- **Member, MemberDeviceToken, Notification, RefreshToken**: 팩토리 메서드에서 빌더로 시각을 넣는다. Member와 MemberDeviceToken은 도메인 메서드에서도 `updatedAt`을 직접 바꾼다.
- 2단계 계약을 쓸 때 확인할 것: 응답이 flush 전 `updatedAt`에 기대는 경로, 그리고 아래 `@SuperBuilder` 타입 추론 문제가 생기는 호출부.

## 트레이드오프 / 남은 리스크
- **`@SuperBuilder` 타입 추론**: `X.builder()`가 `XBuilder<?, ?>`를 돌려주기 때문에 `stream().map(m -> X.builder()...build()).toList()`의 결과가 `List<X>`로 추론되지 않는다. 1단계에서는 `MediaService`와 `FeedCleanupService`의 `.map(`에 타입 인자(`.<Media>map(`, `.<ObjectDeleteOutbox>map(`)를 붙여 해결했다. 앞으로 대상 엔티티의 빌더를 람다 안에서 쓸 때도 같은 처리가 필요하다.
- **미리 넣은 `createdAt`을 덮어쓴다**: `@CreatedDate`는 Spring Data `AuditingHandler`의 알려진 구현 기준으로 이미 값이 있어도 persist할 때 현재 시각으로 덮어쓴다(이번 작업에서 이 동작을 직접 단언하는 테스트는 없다). 예전 `if (createdAt == null)` 가드처럼 "넣은 값을 유지"하는 동작은 없어졌다. main 코드에는 미리 넣는 곳이 없다. 통합 테스트(`@DataJpaTest`)에서도 빌더로 넣은 시각은 저장되지 않으므로, 저장된 시각을 단언하는 테스트는 persist 후 native 쿼리로 값을 바꿔야 한다. 단위 테스트(JPA 없음)에서는 넣은 값이 그대로 유지된다.
- **`updatable = false` 통일**: 대상 10개 중 Team 계열(Team, TeamMember, TeamInvite)을 뺀 7개는 UPDATE문에서 `created_at`이 빠진다. 값은 바뀌지 않는다. 이 속성 자체를 단언하는 테스트는 없고, `BaseTimeEntityIntegrationTest`는 "수정 시 auditing이 createdAt을 덮어쓰지 않는다"까지만 확인한다.
- **auditing이 `@DataJpaTest` 슬라이스에 적용된다는 전제**: `@EnableJpaAuditing`이 `@SpringBootApplication` 클래스에 있어서 슬라이스 테스트에도 함께 적용된다. 이 위치를 옮기면 시각 없이 persist하는 통합 테스트가 NOT NULL 위반으로 실패한다. 이 사실은 `BaseTimeEntityIntegrationTest`로 확인한다.
