# 동적 조건 쿼리는 QueryDSL로 쓰고, 피드 목록 10개 메서드부터 전환한다

- **날짜**: 2026-11-19
- **관련 이슈**: #158
- **상태**: 결정됨 (2026-11-19 계약 승인). `FeedRepository` 피드 목록 10개 메서드 전환 완료. #205로 `NotificationRepository` 목록·개수·일괄 읽음 5개 메서드 전환 완료(2026-11-20)
- **계약**: [2026-11-19-feed-list-querydsl](../contracts/2026-11-19-feed-list-querydsl.md), [2026-11-20-notification-querydsl](../contracts/2026-11-20-notification-querydsl.md)(#205)

## 상황 (Context)
- `FeedRepository`의 피드 목록 조회는 필터 조합(없음 / region / placeId / 태그) × (첫 페이지 / 커서)마다 `@Query`를 따로 둔 8개 메서드와 count 2개(`countActiveFeeds`, `countActiveFeedsByTaggedTeamMemberIds`)로 되어 있다. 서비스(`FeedService.findFeedPage`/`countFeeds`)가 조건을 보고 그중 하나를 고른다.
- 필터가 하나 늘 때마다 메서드 수가 곱으로 늘고, 같은 where·정렬·fetch 경로를 10곳에서 맞춰야 한다.
- 일부 쿼리는 `:x is null or ...` 형태로 동적 조건을 JPQL 안에 넣고 있다. 이 형태는 조건이 빠져도 술어가 SQL에 남는다.

## 검토한 선택지 (Options considered)
1. **QueryDSL(`com.querydsl` 5.1.0, jakarta classifier)** (채택)
2. (기각) **OpenFeign 포크(`io.github.openfeign.querydsl`)**: Spring Boot 4.0.6 BOM에 없어(pom에 `openfeign` 0건) 버전을 직접 관리해야 한다. 공식판에 호환 문제가 생기면 옮길 대안으로 남긴다(아래 리스크).
3. (기각) **Spring Data JPA Specification / Criteria API**: 타입 안전하지만 fetch join·서브쿼리(태그 AND의 `group by ... having count(distinct)`)·커서 술어를 쓰기가 장황하다.
4. (기각) **지금처럼 조합별 `@Query` 유지**: 위 "상황"의 중복이 남는다.

## 결정 (Decision)
1번.

- **의존성**: `com.querydsl:querydsl-jpa::jakarta`(implementation), `com.querydsl:querydsl-apt::jakarta` + `jakarta.persistence-api` + `jakarta.annotation-api`(annotationProcessor). 버전은 Spring Boot 4.0.6 BOM이 관리하는 5.1.0을 따르고 `build.gradle`에 직접 쓰지 않는다(`spring-boot-dependencies-4.0.6.pom` 167, 3401-3403행. `./gradlew dependencies`로 5.1.0, jakarta classifier jar 확인). 테스트 소스는 Q-클래스를 만들 필요가 없어 `testAnnotationProcessor`는 추가하지 않았다.
- **적용 범위(이번 이슈)**: `FeedRepository`의 피드 목록 10개(`findActiveFirstPage*`/`findActiveCursorPage*` 8개, `countActiveFeeds`, `countActiveFeedsByTaggedTeamMemberIds`)를 `findActivePage(condition, limit)`/`countActive(condition)` 2개로 합친다. 나머지 리포지토리는 이번에 바꾸지 않는다.
  - **#205 (2026-11-20)**: `NotificationRepository`의 `(:teamId is null or n.teamId = :teamId)` `@Query` 5개(`findFirstPage`, `findCursorPage`, `countByMemberIdAndTeamId`, `countByMemberIdAndTeamIdAndIsReadFalse`, `updateAllAsReadByMemberId`)를 `NotificationRepositoryCustom`의 `findPage(NotificationListCondition, limit)`, `countAll`/`countUnread(NotificationScope)`, `markAllAsRead(NotificationScope, readAt)`으로 옮겼다. 범위 조건(`NotificationScope`)과 커서를 담는 목록 조건(`NotificationListCondition`)을 나눠, count와 update에는 커서가 들어갈 수 없게 했다. 일괄 읽음 update도 QueryDSL `update`(`JPAUpdateClause`)로 옮겼다.
- **공용 Bean**: `global/config/QuerydslConfig`가 `JPAQueryFactory` Bean을 하나 등록한다. EntityManager는 `@PersistenceContext`로 받은 공유 프록시라 트랜잭션마다 실제 EntityManager로 위임된다. 리포지토리 Impl은 이 Bean을 생성자로 주입받고 `new JPAQueryFactory`를 직접 호출하지 않는다.
  - 이유: 최종적으로 프로젝트 전체에 QueryDSL을 도입하는 것이 전제라, Impl마다 팩토리를 만들면 템플릿 설정 같은 공통 설정이 흩어진다. 설정은 이 Bean 한 곳에서만 한다.
  - `@DataJpaTest`는 사용자 `@Configuration`을 로드하지 않는 것으로 알려져 있어(Spring Boot 4에서는 미확인), 통합 테스트 베이스 `IntegrationTest`에 `@Import(QuerydslConfig.class)`를 붙였다. `@Import`가 있으면 어느 쪽이든 동작한다.
- **Q-클래스 위치**: Gradle 기본 위치 `build/generated/sources/annotationProcessor/java/main`에 생성되고 git에 추적되지 않는다. 패키지는 엔티티와 같다(예: `domain.feed.entity.QFeed`). `@MappedSuperclass`인 `domain.common`의 `BaseTimeEntity`/`BaseCreatedTimeEntity`도 `QBaseTimeEntity`/`QBaseCreatedTimeEntity`가 생긴다.
- **ArchUnit C5 술어 변경**: C5("`domain.<feature>.entity`의 최상위 클래스는 `@Entity`")는 Q-클래스 때문에 실패한다. 예외 상수를 늘리는 대신 술어에 `.and().doNotImplement(EntityPath.class)`를 더해 `com.querydsl.core.types.EntityPath`를 구현한 클래스만 대상에서 뺀다. Q-클래스의 `@Generated`는 `javax.annotation.processing.Generated`(소스 보존)라 바이트코드에 남지 않으므로(`javap -v QFeed.class`에서 확인) 어노테이션이 아니라 `EntityPath` 구현 여부로 판단한다. 다른 ArchUnit 규칙은 Q-클래스로 실패하지 않았다(`./gradlew test`).
- **springdoc 2.8.8 → 3.0.3** (2026-11-19 추가 승인)
  - 원인: springdoc 2.8.8의 `SpringDocConfiguration$QuerydslProvider`는 클래스에 `@ConditionalOnClass(QuerydslBindingsFactory)`, Bean 메서드에 `@ConditionalOnMissingBean`만 있고 `@ConditionalOnProperty`가 없어, 이 설정만 끄는 프로퍼티가 없다(`javap -v`로 `springdoc-openapi-starter-common-2.8.8.jar` 확인). QueryDSL을 추가하자 이 설정이 켜졌고, 이 설정이 쓰는 `QuerydslPredicateOperationCustomizer`가 Spring Data 3 위치의 `org.springframework.data.util.TypeInformation`을 참조한다.
  - 증상: Boot 4의 Spring Data에서 그 클래스를 찾지 못해 `NoClassDefFoundError: org/springframework/data/util/TypeInformation`이 나고, 전체 컨텍스트 테스트 11건(`TikitakApplicationTests` 1, `ExternalCallConnectionTest` 10)이 실패했다. `@DataJpaTest` 슬라이스인 integrationTest는 springdoc을 로드하지 않아 영향이 없었다.
  - 3.0.3을 고른 이유: Boot 4.0.x 기준 라인이고(계약 결정, 3.0.3은 Boot 4.0.5 기준), 3.0.3의 `QuerydslPredicateOperationCustomizer`는 Spring Data 4 위치의 `org.springframework.data.core.TypeInformation`을 참조한다(`javap -v` 확인). 3.1.x 이상은 이번에 쓰지 않는다. 업그레이드 뒤 위 11건을 포함한 `./gradlew clean test` 454건이 통과했다.
  - 유지되는 것: `springdoc.default-flat-param-object` 프로퍼티는 3.0.3에도 있다(`SpringDocConfigProperties.setDefaultFlatParamObject`, `javap` 확인). 기본 경로 상수는 `/v3/api-docs`, `/swagger-ui.html`로 같아 `SecurityConfig` 화이트리스트를 바꾸지 않았다. 컨트롤러·DTO의 `io.swagger` 어노테이션과 `OpenApiConfig`는 수정 없이 컴파일된다.

## 프로젝트 쿼리 작성 규칙
- **동적 조건이 있는 쿼리는 QueryDSL로 쓴다.** 리포지토리에 `XxxRepositoryCustom` 인터페이스와 `XxxRepositoryCustomImpl`을 두고, Impl은 공용 `JPAQueryFactory`를 주입받는다.
- **정적 쿼리는 메서드 이름 기반 쿼리나 `@Query`를 그대로 쓴다.** 동적 조건이 없는 쿼리를 QueryDSL로 옮기지 않는다.
- **네이티브 쿼리는 네이티브로 둔다.**

### 근거 (2026-11-19 grep, `src/main/java/kusitms/spin/tikitak/repository` 기준)
- `*Repository.java` 파일 22개(그중 Spring Data 리포지토리를 상속하는 것 17개, 상속하지 않는 `dailyquestion` 인터페이스 5개)
- `@Query` 82개, 그중 `nativeQuery = true` 11개
- 동적 조건(`:x is null or`, 정규식 `:[A-Za-z]+ is null or`)은 14줄이고 `FeedRepository`(8), `NotificationRepository`(5), `TeamMemberRepository`(1) 3개 파일에만 있다.
  - 2026-11-20 #205 이후 `src/main/java`에서 같은 정규식은 `TeamMemberRepository.java`의 네이티브 쿼리 1건뿐이다(`git grep -nE ":[A-Za-z]+ is null or" -- src/main/java`).
- 동적 조건이 일부 파일에 몰려 있어, 전체를 QueryDSL로 옮기기보다 동적 조건 쿼리부터 옮기는 것이 변경 대비 효과가 크다.

## 후속 전환 후보
- `FeedCommentRepository` 커서 4개(`findActiveFirstPageByFeedId`, `findActiveCursorPageByFeedId`, `findActiveFirstPageByFeedImageId`, `findActiveCursorPageByFeedImageId`)
- ~~`NotificationRepository`의 동적 조건(`:x is null or`) 쿼리~~: #205에서 전환 완료(2026-11-20)
- `TeamMemberRepository`의 동적 조건 1건(`:selectedTeamId is null or`)은 네이티브 쿼리라 "네이티브 쿼리는 네이티브로 둔다" 규칙에 따라 후보에서 뺀다. "네이티브 쿼리마다 Testcontainers 통합 테스트를 둔다"는 규칙은 이번에 추가하지 않기로 2026-11-20에 정했다(#205 계약).

## 트레이드오프 / 남은 리스크
- **라이브러리 유지보수**: 공식 `com.querydsl`은 5.1.0 이후 업데이트가 정체돼 있다(계약의 사람 판단. 릴리스 이력은 이 기록에서 다시 확인하지 않음). Hibernate 7.2.12와 함께 쓸 때 피드 목록 쿼리(fetch join, 서브쿼리 + group by/having, 복합 커서, countDistinct)는 integrationTest 128건에서 오류나 경고 없이 동작했다(2026-11-19). #205(2026-11-20)부터 update DML(`JPAUpdateClause`)도 쓴다. `NotificationQueryIntegrationTest`의 일괄 읽음 3건으로 확인했고, 생성 SQL은 `update notification n1_0 set is_read=?,read_at=? where n1_0.member_id=? [and n1_0.team_id=?] and n1_0.is_read=?`이다(teamId가 null이면 대괄호 부분이 빠진다). 그 밖의 기능(delete DML, insert 등)은 아직 쓰지 않았다. 앞으로 쿼리 생성 오류나 템플릿 문제가 나오면 OpenFeign 포크로 옮기는 것이 대안이며, 옮길지는 사람이 정한다.
- **update와 영속성 컨텍스트**: QueryDSL update는 영속성 컨텍스트를 직접 flush·clear하지 않는다. querydsl-jpa 5.1.0 `JPAUpdateClause.execute()`를 `javap -c`로 보면 `EntityManager.createQuery` → 파라미터 설정 → `Query.executeUpdate()`만 호출한다. 전환 전 `@Modifying`도 옵션이 없어 `flushAutomatically`·`clearAutomatically`가 false였으므로(spring-data-jpa 4.0.5 `Modifying`의 AnnotationDefault, `javap -v` 확인) 동작은 같다. 지금은 `NotificationService.markAllAsRead`가 update 한 줄뿐이라 update 뒤 같은 트랜잭션에서 Notification을 다시 읽는 경로가 없다. 앞으로 그런 경로가 생기면 이미 로드된 엔티티가 갱신 전 상태로 남으므로 다시 읽기 전에 clear가 필요하다. Hibernate가 `executeUpdate` 전에 auto-flush하는지는 확인하지 않았다(미확인. 전환 전과 같은 `createQuery().executeUpdate()` 경로라 차이는 없을 것으로 추정).
- **템플릿 설정**: `QuerydslConfig`는 템플릿을 명시하지 않는다(`new JPAQueryFactory(entityManager)`). 피드 목록 쿼리가 이 설정으로 integrationTest를 통과했기 때문이다. 앞으로 다른 쿼리에서 템플릿 문제가 나오면 `JPQLTemplates.DEFAULT` 명시를 검토하며, 바꾸게 되면 이 Bean 한 곳에서만 바꾼다.
- **springdoc 3.0.3**: 2026-11-19에 로컬에서 기동해 확인했다(dev 프로필, 로컬 Postgres·Redis, 외부 연동 값은 가짜).
  - 엔드포인트: `/v3/api-docs` 200, `/swagger-ui.html`은 302로 `/swagger-ui/index.html`(200)에 연결된다.
  - 같은 DB로 develop(2.8.8)도 띄워 `/v3/api-docs`를 비교했다. `openapi`는 둘 다 3.1.0이고, paths 45개와 schemas 128개도 같다. 차이는 4곳이다(서버 URL 포트 제외).
    - `LoginResponse.activeTeamId`, `MediaUploadRequest.teamId`, `PageInfoDTO.nextCursor`의 `type`이 단일 타입에서 `[타입, "null"]`로 바뀌었다.
    - `GET /api/v1/auth/oauth/{provider}/start`의 두 번째 쿼리 파라미터에 `default: false`가 추가됐다.
  - 이 스펙으로 클라이언트 코드를 생성하는 쪽이 있으면 세 필드의 타입이 nullable로 바뀐다.
  - 3.0.3은 기동 시 `/swagger-ui.html`, `/v3/api-docs`가 기본으로 켜져 있다는 WARN 로그를 남긴다(`SpringDocAppInitializer`, 2.8.8 jar에는 없는 클래스). 운영에서 끌지는 이번 범위가 아니다.
- **빌드**: Q-클래스는 컴파일 때마다 생성되므로, IDE가 Gradle 생성 소스 경로를 인식하지 않으면 Q-클래스가 없다고 표시될 수 있다. `./gradlew compileJava`로 생성된다.
- **ArchUnit**: C5는 `EntityPath`를 구현한 클래스를 모두 뺀다. 엔티티 패키지에 사람이 직접 `EntityPath` 구현 클래스를 두면 C5가 잡지 못한다. 그런 클래스를 손으로 둘 이유는 확인된 것이 없다.
