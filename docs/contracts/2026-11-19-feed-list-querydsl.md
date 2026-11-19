# 작업 계약: 피드 목록 동적 쿼리 QueryDSL 전환

- **관련 이슈**: #158
- **브랜치**: `Refactor/#158-querydsl` (develop에서 분기)
- **상태**: 승인됨 (2026-11-19)

## 목표
`FeedRepository`의 피드 목록 `@Query` 8개와 count 2개(필터 조합별 오버로드)를 QueryDSL 동적 쿼리 메서드 2개(`findActivePage`, `countActive`)로 합친다. API 응답과 조회 결과는 바꾸지 않는다.

## 결정 사항 (2026-11-19 사람 선택)
- 라이브러리: `com.querydsl:querydsl-jpa:jakarta` / `querydsl-apt:jakarta`. 버전은 Boot 4.0.6 BOM이 관리하는 5.1.0을 따른다(`spring-boot-dependencies-4.0.6.pom` 167, 3401-3403행).
  - 커밋 1 또는 2의 integrationTest에서 Hibernate 7.2.12와 호환 문제(쿼리 생성 오류, 템플릿 문제)가 나오면 **멈추고 보고한다.** OpenFeign 포크로 바꿀지는 사람이 정한다.
- 범위: `FeedRepository` 10개만. `FeedCommentRepository` 커서 4개는 후속 이슈로 남긴다.
- `JPAQueryFactory`: 공용 Bean으로 둔다. 최종적으로 프로젝트 전체에 QueryDSL을 도입하는 것이 전제이기 때문이다.
  - `global/config/QuerydslConfig`가 `JPAQueryFactory` Bean을 하나 등록하고, Impl은 생성자로 주입받는다.
  - 템플릿 설정(필요하면 `JPQLTemplates.DEFAULT` 명시)은 이 Bean 한 곳에서만 한다.
  - `@DataJpaTest`는 사용자 `@Configuration`을 로드하지 않는다. 그래서 `IntegrationTest` 베이스에 `@Import(QuerydslConfig.class)`를 추가한다.
- SQL 동등성: 조회 결과, 정렬, 커서, to-one fetch 4개 경로가 같고 쿼리 수가 늘지 않으면 된다. distinct 유무나 join 형태처럼 SQL 문자열이 달라지는 것은 허용한다.
- springdoc은 `2.8.8`에서 `3.0.3`으로 올린다(2026-11-19 추가 승인, 커밋 1에 포함).
  - 원인: springdoc 2.8.8의 `SpringDocConfiguration$QuerydslProvider`는 `@ConditionalOnClass(QuerydslBindingsFactory)`와 `@ConditionalOnMissingBean`만으로 켜지고, 끄는 프로퍼티가 없다(javap 확인). QueryDSL이 클래스패스에 올라오면 이 설정이 켜지는데, Boot 4 Spring Data에는 이 설정이 쓰는 `TypeInformation`이 없어 `NoClassDefFoundError`가 난다. 이 때문에 `TikitakApplicationTests` 등 전체 컨텍스트 테스트 11건이 실패했다.
  - 3.0.3을 고른 이유: Maven Central 확인 결과 3.0.3은 Boot 4.0.5 기준이고, 우리는 4.0.6이다. 3.1.x 이상은 Boot 4.1 기준이라 맞지 않는다.
- count는 목록과 같은 조건 빌더를 공유하는 별도 메서드로 둔다. 서비스 흐름은 `FeedService.java:110-111`처럼 목록과 count를 따로 부르는 형태를 유지한다.

## 범위: 건드려도 되는 파일
- `build.gradle`
- `src/main/java/kusitms/spin/tikitak/repository/feed/FeedRepository.java`
- (신규) `src/main/java/kusitms/spin/tikitak/repository/feed/FeedRepositoryCustom.java`, `FeedRepositoryCustomImpl.java`, `FeedListCondition.java`
- (신규) `src/main/java/kusitms/spin/tikitak/global/config/QuerydslConfig.java`
- `src/test/java/kusitms/spin/tikitak/support/IntegrationTest.java`: `@Import(QuerydslConfig.class)` 추가만
- `src/main/java/kusitms/spin/tikitak/service/feed/FeedService.java`: `countFeeds`(850-863), `findFeedPage`(879-928)와 이 둘이 쓰는 매핑만. `FeedTypeFilter`에 `FeedType` 변환 메서드를 추가할 수 있다.
- `src/test/java/kusitms/spin/tikitak/service/feed/FeedServiceTest.java`
- `src/test/java/kusitms/spin/tikitak/repository/feed/FeedRepositoryIntegrationTest.java`, `FeedRegionFilterRepositoryIntegrationTest.java`, (신규) `FeedListQueryIntegrationTest.java`
- `src/test/java/kusitms/spin/tikitak/architecture/ConventionArchTest.java`: C5 술어만
- (신규) `docs/decisions/2026-11-19-querydsl-feed-list.md`, `docs/decisions/README.md` 인덱스 한 줄

## 설계
- **조건 객체** `FeedListCondition` (record, repository 패키지): `teamId`, `placeId`, `region`, `FeedType feedType`(null이면 전체), `List<Long> taggedTeamMemberIds`(빈 리스트면 태그 필터 없음), `LocalDateTime cursorCreatedAt`, `Long cursorFeedId`.
  - 서비스의 private `FeedTypeFilter`를 repository에서 참조하면 ArchUnit L4 위반이다(`LayerDependencyArchTest.java:46-54`). 그래서 도메인 `FeedType`을 쓴다.
- **`findActivePage(condition, int limit)`** 조건:
  - 항상: `team.id = teamId`, `deletedAt is null`
  - feedType: GENERAL이면 `question is null`, DAILY_QUESTION이면 `question is not null`
  - placeId가 있으면: `place.externalPlaceId = placeId`
  - region이 있으면: `place.region = region`
  - 태그가 있으면: `f.id in (select tag.feed.id ... where tag.teamMember.id in :ids group by tag.feed.id having count(distinct tag.teamMember.id) = ids.size())`
  - 커서가 있으면: `createdAt < c or (createdAt = c and id < id)`
  - 정렬과 개수: `order by createdAt desc, id desc`, `limit`
  - fetch: `teamMember`, `teamMember.member`, `place`, `question`을 to-one fetch join한다. place와 question은 left join이다(지금 `@EntityGraph`가 가져오는 4개 경로와 같음, `FeedRepository.java` 49-263). **컬렉션(images, tags)은 fetch join하지 않는다**(`docs/decisions/2026-09-19-feed-n1-batch-fetch-pattern.md`).
- **`countActive(condition)`**: 같은 where 술어를 쓰고 커서는 무시한다. `count(distinct f.id)`이며 fetch join은 하지 않는다.
- **서비스**: `findFeedPage`/`countFeeds`의 분기를 없애고 조건 객체 하나로 호출한다. limit은 지금처럼 `pageSize + 1`이다. placeId와 region 정규화(105-106행)는 그대로 둔다.

## 의도된 동작 변경
- **springdoc 2.8.8 → 3.0.3**: `/v3/api-docs`, `/swagger-ui/**` 경로와 `springdoc.default-flat-param-object` 설정은 유지돼야 한다. 생성되는 OpenAPI 스펙의 세부 표현(스키마 이름, nullable 표기 등)은 달라질 수 있다(미확인).
- **피드 목록 API**: **없음.** `GET` 피드 목록 API의 items 순서·내용·`totalCount`·`nextCursor`는 모든 필터 조합(태그 / placeId / region / feedType / 커서)에서 이전과 같다.
- SQL 문자열은 달라질 수 있다. 태그 쿼리의 distinct 유무, place join이 inner인지 left인지 등이다. 지금 where 조건에서 place가 null이면 결과에서 빠지므로 결과는 같다.
- 근거는 아래 완료 조건의 조합별 통합 테스트다.

## 완료 조건
- [ ] `./gradlew compileJava` 실행 후 `build/generated/sources/annotationProcessor/java/main/.../domain/feed/entity/QFeed.java`가 생성된다. 생성 소스는 git에 추적되지 않는다(`git status`에 나타나지 않음).
- [ ] `QuerydslConfig`가 `JPAQueryFactory` Bean을 하나 등록한다. `FeedRepositoryCustomImpl`은 이를 생성자로 주입받고 `new JPAQueryFactory`를 직접 호출하지 않는다(main에서 `new JPAQueryFactory`는 `QuerydslConfig`에서만 grep 1건). 전체 컨텍스트 테스트 `TikitakApplicationTests`가 통과한다.
- [ ] `build.gradle`의 springdoc 좌표가 `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3`이다. 전체 컨텍스트 테스트(`TikitakApplicationTests`, `ExternalCallConnectionTest`)가 통과한다. `/v3/api-docs` 200 응답은 로컬 기동으로 확인하고, 확인하지 못하면 "미확인"으로 표시한다.
- [ ] `./gradlew spotlessCheck`, `./gradlew test` 통과. ArchUnit은 C5 술어에서 Q-클래스(`com.querydsl.core.types.EntityPath`를 구현한 클래스)만 제외하고, 예외 상수는 추가하지 않는다.
- [ ] `./gradlew integrationTest` 통과.
- [ ] `FeedRepository`에서 다음 10개 메서드가 제거되어 있다(grep 0건): `findActiveFirstPage`, `findActiveCursorPage`, `findActiveFirstPageByRegion`, `findActiveCursorPageByRegion`, `findActiveFirstPageByPlaceId`, `findActiveCursorPageByPlaceId`, `findActiveFirstPageByTaggedTeamMemberIds`, `findActiveCursorPageByTaggedTeamMemberIds`, `countActiveFeeds`, `countActiveFeedsByTaggedTeamMemberIds`. `countActiveFeedsWithRegion` 등 나머지 메서드는 그대로다.
- [ ] `FeedListQueryIntegrationTest`가 `findActivePage`/`countActive`로 다음 조합을 검증한다.
  - 필터 없음 / placeId / region / 태그 AND(2명 중 1명만 태그된 피드는 제외) / 태그+region / 태그+placeId, 각각 feedType GENERAL·DAILY_QUESTION 중 하나 이상 포함
  - 삭제된 피드와 다른 팀 피드 제외
  - 커서 다음 페이지에서 **같은 createdAt의 id tie-breaker**. createdAt을 명시적으로 같게 맞춘다(lessons-log 43행)
  - count가 커서와 무관하게 전체 조건 개수를 낸다
  - flushAndClear 후 조회한 피드의 `teamMember`, `teamMember.member`, `place`, `question`이 `Hibernate.isInitialized`로 초기화돼 있고, `images`는 초기화돼 있지 않다
- [ ] 기존 `FeedRegionFilterRepositoryIntegrationTest`(region 11건)와 `FeedRepositoryIntegrationTest:132-171`(태그 AND)의 검증 내용이 새 메서드 호출로 옮겨져 그대로 유지된다. 테스트를 삭제해 검증 범위가 줄지 않는다.
- [ ] `FeedServiceTest`의 기존 stub/verify(470-552, 632, 663)가 `findActivePage`로 바뀐다. 조건 객체 필드(placeId/region 정규화, feedType, 태그 ID, 커서, limit = pageSize+1)를 `ArgumentCaptor` 또는 일치 검사로 확인한다.
- [ ] 결정 기록 1건과 README 인덱스. 결정 기록에는 다음을 적는다.
  - 라이브러리 선택 이유, 적용 범위, 공용 Bean 방식과 그 이유, Q-클래스 위치와 C5 술어 변경
  - **프로젝트 쿼리 작성 규칙**: 동적 조건이 있는 쿼리는 QueryDSL로 쓴다. 정적 쿼리는 메서드 이름 기반 쿼리나 `@Query`를 그대로 쓴다. 네이티브 쿼리는 네이티브로 둔다.
  - 규칙의 근거(2026-11-19 grep): 리포지토리 22개, `@Query` 82개, 그중 네이티브 11개. 동적 조건(`:x is null or`)은 14줄이고 `FeedRepository`, `NotificationRepository`, `TeamMemberRepository` 3개 파일에만 있다.
  - 후속 전환 후보: `FeedCommentRepository` 커서 4개, `NotificationRepository`, `TeamMemberRepository`의 동적 조건 쿼리
  - 라이브러리 유지보수 위험: 공식 5.1.0 이후 업데이트가 정체돼 있다. 문제가 생기면 OpenFeign 포크로 옮기는 것이 대안이다.

## 커밋 구성
1. `build`: QueryDSL 의존성과 apt 설정, springdoc 3.0.3 업그레이드, `QuerydslConfig` Bean, `IntegrationTest` `@Import`, ConventionArchTest C5 술어 수정, 결정 기록. 기존 리포지토리와 서비스 코드는 바꾸지 않는다.
2. `refactor`: `FeedListCondition`, `FeedRepositoryCustom`/`Impl` 추가, `FeedRepository`가 Custom을 상속, `FeedListQueryIntegrationTest` 추가. 기존 메서드는 아직 남긴다.
3. `refactor`: `FeedService`가 새 메서드를 쓰도록 바꾸고, 기존 10개 `@Query`를 제거한다. 기존 통합 테스트와 `FeedServiceTest`도 옮긴다.

## 하지 말 것
- 범위 밖 리팩터링, 손으로 하는 포맷 정리. `FeedService` 908-927행의 들여쓰기는 해당 블록을 교체하면서 자연스럽게 바뀌는 것만 허용한다.
- 컬렉션 fetch join, `fetchResults()`/`fetchCount()`(deprecated) 사용
- `@Transactional`(C2), `@Value`(C10)를 repository에 쓰는 것
- `FeedCommentRepository` 등 다른 리포지토리 전환, API 응답 필드(`totalCount` 등) 변경
- Impl에서 `JPAQueryFactory`를 직접 생성하는 것, `IntegrationTest`에 `@Import` 한 줄 외의 수정
- ArchUnit 예외 상수 추가
- 사람 승인 없이 커밋/푸시

## 참고
- 조사: `FeedRepository.java` 49-314, `FeedService.java` 89-149 / 850-928 / 1061-1072, `ConventionArchTest.java:118-127`(C5), `IntegrationTest.java:24-28`(`@DataJpaTest`, `@Import` 없음)
- 인덱스: `idx_feed_active_team_created_id ON feed(team_id, created_at DESC, id DESC) WHERE deleted_at IS NULL` (V11:7-9), `idx_feed_tag_team_member_feed` (V11:4-5)
- 확인된 것 (구현 중):
  - com.querydsl 5.1.0은 Hibernate 7.2.12에서 이번 쿼리 범위에 대해 동작한다. 커밋 2·3의 integrationTest 128건이 통과했다.
  - Q-클래스의 `@Generated`(`javax.annotation.processing.Generated`)는 바이트코드에 남지 않는다(javap). 그래서 C5 술어는 `EntityPath` 구현 여부로 판단한다.
  - `JPQLTemplates.DEFAULT`는 명시하지 않는다. 기본 설정으로 integrationTest가 통과했다.
- 미확인:
  - Spring Boot 4의 `@DataJpaTest`가 사용자 `@Configuration`을 제외하는지 (Boot 3 기준 일반 지식. `@Import`가 있으면 어느 쪽이든 동작한다)
- 확인된 것 (2026-11-19 로컬 기동):
  - `/v3/api-docs` 200, Swagger UI 200
  - develop(2.8.8)과의 스펙 차이는 결정 기록 "springdoc 3.0.3" 항목에 적었다. nullable 표기 3곳, 파라미터 default 1곳이다.
