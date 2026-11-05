# 작업 계약: 공통 감사 필드(BaseTimeEntity) 도입 1단계

- **관련 이슈**: #159
- **브랜치**: `Refactor/#159-base-time-entity` (Orca worktree, develop a25010b 기준)
- **상태**: 승인됨 (2026-11-05)

## 목표
- `createdAt`/`updatedAt`의 선언과 값 세팅을 `@MappedSuperclass` 상위 클래스와 Spring Data auditing(`AuditingEntityListener`)으로 모은다.
- 1단계는 **동작이 바뀌지 않는 엔티티 10개만** 옮긴다. `deletedAt`은 다루지 않는다.
- 2단계(별도 이슈/PR)로 미루는 것: Feed, FeedComment(flush 전에 `updatedAt`을 읽어 응답함), Member, MemberDeviceToken, Notification, RefreshToken(팩토리에서 시각 세팅).

## 사람이 정한 것 (2026-11-05)
- 위치: `domain/common` 패키지 (entity 하위 패키지 아님 → ArchUnit C5 변경 없음, domain → global 의존 없음)
- 테스트 빌더 호환: 대상 엔티티의 `@Builder` → `@SuperBuilder` (테스트의 `.createdAt(...)` 20곳 유지)
- FeedComment: 2단계로 미룸
- 오케스트레이터 추천으로 확정(이의 시 변경): 2단계 계층, `createdAt`은 `updatable = false`로 통일, FeedReaction/Place 포함, 커스텀 `DateTimeProvider` 도입 안 함(KST 결정 기록의 B안과 같은 저장 시각 불연속이 생김)

## 항목별 작업

| # | 작업 |
|---|---|
| 1 | `domain/common/BaseCreatedTimeEntity` (신규) |
|   | - `@MappedSuperclass`, `@EntityListeners(AuditingEntityListener.class)`, `@Getter`, `@SuperBuilder`, `@NoArgsConstructor(access = PROTECTED)` |
|   | - `@CreatedDate @Column(nullable = false, updatable = false) private LocalDateTime createdAt;` |
|   | - JPA 콜백 메서드(`@PrePersist` 등)는 두지 않는다. 하위 클래스의 같은 이름 메서드를 오버라이드하는 문제를 피하기 위해서다. |
| 2 | `domain/common/BaseTimeEntity extends BaseCreatedTimeEntity` (신규) |
|   | - 같은 Lombok 구성, `@LastModifiedDate @Column(nullable = false) private LocalDateTime updatedAt;` |
| 3 | createdAt만 있는 5개 → `BaseCreatedTimeEntity` 상속: TeamMember, TeamInvite, FeedImage, FeedReaction, Place |
| 4 | createdAt+updatedAt 5개 → `BaseTimeEntity` 상속: Team, Question, Media, MediaUpload, ObjectDeleteOutbox |
| 5 | 3·4번 엔티티 공통 정리 |
|   | - `@Builder` → `@SuperBuilder`. `@Builder.Default`는 그대로 둔다. |
|   | - `@AllArgsConstructor`는 직접 호출하는 곳이 없으면 제거하고, 있으면 남긴 뒤 보고한다. |
|   | - 엔티티의 `createdAt`/`updatedAt` 필드 선언, 엔티티에 붙은 `@EntityListeners(AuditingEntityListener.class)`, 콜백 안의 시각 세팅 줄을 제거한다. |
|   | - 시각 세팅만 하던 `@PrePersist`/`@PreUpdate` 메서드는 통째로 지운다. |
|   | - **다른 일을 하는 콜백은 그 부분을 남긴다**: TeamMember `lastActivityCheckedAt`, Media/MediaUpload `publicId`, ObjectDeleteOutbox `status` 기본값. |
| 6 | auditing 통합 테스트 (신규, `IntegrationTest` 상속) |
|   | - `BaseTimeEntity` 대표 엔티티 1개: 시각 없이 persist → flush/clear 후 다시 읽으면 `createdAt`/`updatedAt`이 null이 아니고 서로 같다. 값을 수정해 flush/clear → `updatedAt`은 이전보다 늦고, `createdAt`은 그대로다. |
|   | - `BaseCreatedTimeEntity` 대표 엔티티 1개: 시각 없이 persist → `createdAt`이 null이 아니다. |
|   | - 위 테스트는 `@DataJpaTest` 슬라이스에서 `@EnableJpaAuditing`(TikitakApplication)이 실제로 적용되는지도 함께 증명한다. |
| 7 | 결정 기록 `docs/decisions/2026-11-05-base-time-entity.md`, `docs/decisions/README.md` 목록 한 줄 추가 |
|   | - 내용: 위치 선택(C5/의존 방향), 2단계 계층, `@SuperBuilder`, `DateTimeProvider` 미도입, `deletedAt` 제외, 2단계 대상과 그 이유(FeedComment/Feed는 flush 전에 `updatedAt`을 읽음) |

## 의도된 동작 변경
API 응답, 저장 값, 쿼리 결과는 **변경 없음**. 내부 차이만 있다.

| 경우 | 이전 | 이후 |
|---|---|---|
| Media/MediaUpload/ObjectDeleteOutbox INSERT | `now()`를 두 번 불러 createdAt과 updatedAt이 µs 단위로 다를 수 있음 | 같은 값 |
| 8개 엔티티 UPDATE문 | `created_at`을 같은 값으로 다시 씀 | `created_at`이 UPDATE문에서 빠짐 (Team/TeamMember/TeamInvite는 원래 빠져 있음) |
| FeedImage/FeedReaction/Place/Question에 createdAt을 미리 넣고 persist | 넣은 값 유지 (`if null` 가드) | auditing이 now로 덮어씀. main에는 이런 호출이 없고, 테스트 중 `FeedImageRepositoryIntegrationTest:109`는 시각을 단언하지 않음 |
| 단위 테스트(JPA 없음)에서 빌더로 넣은 시각 | 유지 | 유지 (`DailyQuestionServiceTest:324` #162 회귀 값 포함) |

## 범위: 건드려도 되는 파일
- 신규: `src/main/java/kusitms/spin/tikitak/domain/common/BaseCreatedTimeEntity.java`, `BaseTimeEntity.java`
- `src/main/java/kusitms/spin/tikitak/domain/team/entity/{Team,TeamMember,TeamInvite}.java`
- `src/main/java/kusitms/spin/tikitak/domain/feed/entity/{FeedImage,FeedReaction}.java`
- `src/main/java/kusitms/spin/tikitak/domain/place/entity/Place.java`
- `src/main/java/kusitms/spin/tikitak/domain/question/entity/Question.java`
- `src/main/java/kusitms/spin/tikitak/domain/media/entity/{Media,MediaUpload,ObjectDeleteOutbox}.java`
- 신규 테스트: `src/test/java/kusitms/spin/tikitak/domain/common/BaseTimeEntityIntegrationTest.java`
- `docs/decisions/2026-11-05-base-time-entity.md`, `docs/decisions/README.md`
- 기존 테스트 파일은 **수정하지 않는다**. 컴파일이나 테스트가 깨지면 고치지 말고 멈춰서 보고한다.

## 완료 조건
- [ ] `./gradlew test`, `./gradlew integrationTest`, `./gradlew spotlessCheck` 통과 (ArchUnit 포함, 통합 프로필은 `ddl-auto: validate`라 매핑 불일치도 잡힘)
- [ ] 대상 10개 엔티티 파일에 `createdAt`/`updatedAt` 필드 선언과 `AuditingEntityListener` 문자열이 0건이다.
- [ ] 대상 10개 중 `@PrePersist`/`@PreUpdate`가 남은 파일은 TeamMember, Media, MediaUpload, ObjectDeleteOutbox뿐이고, 그 콜백 본문에 `createdAt`/`updatedAt` 대입이 없다.
- [ ] 대상 10개 엔티티 모두 `@SuperBuilder`를 쓰고 `@Builder`(`@Builder.Default` 제외)가 없다.
- [ ] `git diff --stat origin/develop -- src/test`에 신규 테스트 파일 1개만 나온다.
- [ ] `src/main/resources/db/migration`에 변경이 없다.
- [ ] 6번 통합 테스트가 위 단언을 모두 포함하고 통과한다. 수정 후 `updatedAt` 비교는 DB에서 다시 읽은 값으로 한다(`flushAndClear` 후 조회).
- [ ] 범위 밖 엔티티(Feed, FeedComment, Member, MemberDeviceToken, Notification, RefreshToken)에 diff가 없다.

## 커밋 구성
1. 상위 클래스 2개 + 이미 `@CreatedDate`를 쓰던 Team/TeamMember/TeamInvite 적용 + 6번 통합 테스트
2. `@PrePersist` 기반 7개(FeedImage, FeedReaction, Place, Question, Media, MediaUpload, ObjectDeleteOutbox) 적용
3. 결정 기록 + README

## 하지 말 것
- `deletedAt` 이동, 2단계 엔티티 수정, 커스텀 `DateTimeProvider`/`AuditorAware` 추가, `@EnableJpaAuditing` 위치 변경
- Flyway 마이그레이션 추가, ArchUnit 규칙/예외 변경
- 기존 테스트 수정, 범위 밖 리팩터링, 손으로 하는 포맷 정리 (Spotless 결과는 허용)
- 사람 승인 없이 커밋/푸시

## 참고
- 조사: 2026-11-05 researcher. 1단계 대상 테이블 컬럼은 모두 `TIMESTAMP NOT NULL`이고 이름이 같아 마이그레이션은 필요 없다(V1, V3, V7, V8, V13).
- `FeedCommentService.updateComment`(:140-153)가 flush 전에 `toCommentItem`으로 `getUpdatedAt()`을 읽는다. 이것이 FeedComment를 2단계로 미룬 이유다.
- `ConventionArchTest` C5(:118-127): `domain.*.entity`의 최상위 클래스는 `@Entity`여야 한다.
- 확인 못 한 것: spring-data-commons 4.0.5 `AuditingHandler`의 덮어쓰기 동작(바이트코드 미확인, 알려진 구현 기준)과 `@DataJpaTest`의 auditing 적용 여부. 6번 테스트와 `integrationTest`로 판정한다.
- 결정 기록: `docs/decisions/2026-11-04-kst-boundaries-without-tz-change.md` (시스템 zone 벽시계 저장 전제 유지)
