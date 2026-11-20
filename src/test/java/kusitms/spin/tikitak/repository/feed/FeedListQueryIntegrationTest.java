package kusitms.spin.tikitak.repository.feed;

import kusitms.spin.tikitak.domain.feed.entity.Feed;
import kusitms.spin.tikitak.domain.feed.entity.FeedTag;
import kusitms.spin.tikitak.domain.feed.enums.FeedType;
import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.place.entity.Place;
import kusitms.spin.tikitak.domain.question.entity.Question;
import kusitms.spin.tikitak.domain.team.entity.Team;
import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberRole;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus;
import kusitms.spin.tikitak.support.IntegrationTest;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link FeedRepositoryCustom#findActivePage}/{@link FeedRepositoryCustom#countActive}의 필터 조합, 커서, fetch 경로 검증.
 */
class FeedListQueryIntegrationTest extends IntegrationTest {

	private static final String GANGNAM = "서울 강남구";
	private static final String BUNDANG = "경기 성남시 분당구";
	private static final String PLACE_X = "qd-place-x";
	private static final String PLACE_Y = "qd-place-y";
	private static final String PLACE_Z = "qd-place-z";
	private static final int LIMIT = 20;

	@Autowired
	private FeedRepository feedRepository;

	/**
	 * 공통 시나리오. 태그 A·B 기준으로 피드 6개와 제외 대상 2개를 만든다.
	 * <pre>
	 * f1: placeX(강남), GENERAL,        태그 A,B  t+1
	 * f2: placeY(분당), GENERAL,        태그 A,B  t+2
	 * f3: placeX(강남), DAILY_QUESTION, 태그 A    t+3
	 * f4: 장소 없음,    GENERAL,        태그 A,B  t+4
	 * f5: placeX(강남), DAILY_QUESTION, 태그 A,B  t+5
	 * f6: placeZ(강남), GENERAL,        태그 A    t+6
	 * deleted: placeX(강남), GENERAL, 태그 A,B, 삭제됨  t+7
	 * other:   다른 팀, placeX(강남), GENERAL       t+8
	 * </pre>
	 */
	private Scenario scenario() {
		Member authorMember = persist(member("fl-author"));
		Member memberA = persist(member("fl-a"));
		Member memberB = persist(member("fl-b"));
		Member otherMember = persist(member("fl-other"));
		Team team = persist(team("fl"));
		Team otherTeam = persist(team("fl-other"));
		TeamMember author = persist(teamMember(authorMember, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));
		TeamMember tagA = persist(teamMember(memberA, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
		TeamMember tagB = persist(teamMember(memberB, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
		TeamMember otherAuthor = persist(teamMember(otherMember, otherTeam, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));

		Place placeX = persist(place(PLACE_X, GANGNAM));
		Place placeY = persist(place(PLACE_Y, BUNDANG));
		Place placeZ = persist(place(PLACE_Z, GANGNAM));
		Question question = persist(question());

		Feed f1 = persistFeed(feed(team, author, placeX, null, BASE_TIME.plusHours(1)), BASE_TIME.plusHours(1), tagA, tagB);
		Feed f2 = persistFeed(feed(team, author, placeY, null, BASE_TIME.plusHours(2)), BASE_TIME.plusHours(2), tagA, tagB);
		Feed f3 = persistFeed(feed(team, author, placeX, question, BASE_TIME.plusHours(3)), BASE_TIME.plusHours(3), tagA);
		Feed f4 = persistFeed(feed(team, author, null, null, BASE_TIME.plusHours(4)), BASE_TIME.plusHours(4), tagA, tagB);
		Feed f5 = persistFeed(feed(team, author, placeX, question, BASE_TIME.plusHours(5)), BASE_TIME.plusHours(5), tagA, tagB);
		Feed f6 = persistFeed(feed(team, author, placeZ, null, BASE_TIME.plusHours(6)), BASE_TIME.plusHours(6), tagA);

		Feed deleted = Feed.builder()
				.team(team).teamMember(author).place(placeX)
				.content("삭제된 피드")
				.createdAt(BASE_TIME.plusHours(7)).updatedAt(BASE_TIME.plusHours(7)).deletedAt(BASE_TIME.plusHours(7))
				.build();
		deleted = persistFeed(deleted, BASE_TIME.plusHours(7), tagA, tagB);
		Feed other = persistFeed(feed(otherTeam, otherAuthor, placeX, null, BASE_TIME.plusHours(8)), BASE_TIME.plusHours(8));

		flushAndClear();
		return new Scenario(team.getId(), tagA.getId(), tagB.getId(),
				f1.getId(), f2.getId(), f3.getId(), f4.getId(), f5.getId(), f6.getId(), deleted.getId(), other.getId());
	}

	@Nested
	@DisplayName("필터 조합")
	class Filters {

		@Test
		@DisplayName("필터가 없으면 팀의 활성 피드 전체를 최신순으로 반환하고, 삭제·다른 팀 피드는 제외한다")
		void noFilter() {
			Scenario s = scenario();

			FeedListCondition condition = condition(s.teamId(), null, null, null, List.of());

			assertThat(ids(feedRepository.findActivePage(condition, LIMIT)))
					.containsExactly(s.f6(), s.f5(), s.f4(), s.f3(), s.f2(), s.f1())
					.doesNotContain(s.deleted(), s.other());
			assertThat(feedRepository.countActive(condition)).isEqualTo(6L);
		}

		@Test
		@DisplayName("태그 ID가 null이면 빈 리스트로 바꿔 태그 필터를 적용하지 않는다")
		void nullTaggedIdsMeansNoTagFilter() {
			Scenario s = scenario();

			FeedListCondition condition = new FeedListCondition(s.teamId(), null, null, null, null, null, null);

			assertThat(condition.taggedTeamMemberIds()).isEmpty();
			assertThat(ids(feedRepository.findActivePage(condition, LIMIT)))
					.containsExactly(s.f6(), s.f5(), s.f4(), s.f3(), s.f2(), s.f1());
		}

		@Test
		@DisplayName("feedType GENERAL은 question이 없는 피드만 반환한다")
		void generalOnly() {
			Scenario s = scenario();

			FeedListCondition condition = condition(s.teamId(), null, null, FeedType.GENERAL, List.of());

			assertThat(ids(feedRepository.findActivePage(condition, LIMIT)))
					.containsExactly(s.f6(), s.f4(), s.f2(), s.f1());
			assertThat(feedRepository.countActive(condition)).isEqualTo(4L);
		}

		@Test
		@DisplayName("feedType DAILY_QUESTION은 question이 있는 피드만 반환한다")
		void dailyQuestionOnly() {
			Scenario s = scenario();

			FeedListCondition condition = condition(s.teamId(), null, null, FeedType.DAILY_QUESTION, List.of());

			assertThat(ids(feedRepository.findActivePage(condition, LIMIT)))
					.containsExactly(s.f5(), s.f3());
			assertThat(feedRepository.countActive(condition)).isEqualTo(2L);
		}

		@Test
		@DisplayName("placeId는 place.externalPlaceId가 같은 피드만 반환한다")
		void placeId() {
			Scenario s = scenario();

			FeedListCondition condition = condition(s.teamId(), PLACE_X, null, null, List.of());

			assertThat(ids(feedRepository.findActivePage(condition, LIMIT)))
					.containsExactly(s.f5(), s.f3(), s.f1());
			assertThat(feedRepository.countActive(condition)).isEqualTo(3L);
		}

		@Test
		@DisplayName("placeId와 feedType GENERAL을 함께 적용한다")
		void placeIdWithGeneral() {
			Scenario s = scenario();

			FeedListCondition condition = condition(s.teamId(), PLACE_X, null, FeedType.GENERAL, List.of());

			assertThat(ids(feedRepository.findActivePage(condition, LIMIT)))
					.containsExactly(s.f1());
			assertThat(feedRepository.countActive(condition)).isEqualTo(1L);
		}

		@Test
		@DisplayName("region은 place.region이 같은 피드만 반환하고 장소 없는 피드는 제외한다")
		void region() {
			Scenario s = scenario();

			FeedListCondition condition = condition(s.teamId(), null, GANGNAM, null, List.of());

			assertThat(ids(feedRepository.findActivePage(condition, LIMIT)))
					.containsExactly(s.f6(), s.f5(), s.f3(), s.f1());
			assertThat(feedRepository.countActive(condition)).isEqualTo(4L);
		}

		@Test
		@DisplayName("region과 feedType DAILY_QUESTION을 함께 적용한다")
		void regionWithDailyQuestion() {
			Scenario s = scenario();

			FeedListCondition condition = condition(s.teamId(), null, GANGNAM, FeedType.DAILY_QUESTION, List.of());

			assertThat(ids(feedRepository.findActivePage(condition, LIMIT)))
					.containsExactly(s.f5(), s.f3());
			assertThat(feedRepository.countActive(condition)).isEqualTo(2L);
		}

		@Test
		@DisplayName("태그 필터는 요청한 멤버가 모두 태그된 피드만 반환한다(2명 중 1명만 태그된 피드 제외)")
		void taggedAnd() {
			Scenario s = scenario();

			FeedListCondition condition = condition(s.teamId(), null, null, null, List.of(s.tagA(), s.tagB()));

			assertThat(ids(feedRepository.findActivePage(condition, LIMIT)))
					.containsExactly(s.f5(), s.f4(), s.f2(), s.f1())
					.doesNotContain(s.f3(), s.f6(), s.deleted());
			assertThat(feedRepository.countActive(condition)).isEqualTo(4L);
		}

		@Test
		@DisplayName("태그 1명이면 그 멤버가 태그된 피드를 모두 반환한다")
		void taggedSingle() {
			Scenario s = scenario();

			FeedListCondition condition = condition(s.teamId(), null, null, null, List.of(s.tagA()));

			assertThat(ids(feedRepository.findActivePage(condition, LIMIT)))
					.containsExactly(s.f6(), s.f5(), s.f4(), s.f3(), s.f2(), s.f1());
			assertThat(feedRepository.countActive(condition)).isEqualTo(6L);
		}

		@Test
		@DisplayName("태그 필터와 feedType GENERAL을 함께 적용한다")
		void taggedWithGeneral() {
			Scenario s = scenario();

			FeedListCondition condition = condition(s.teamId(), null, null, FeedType.GENERAL, List.of(s.tagA(), s.tagB()));

			assertThat(ids(feedRepository.findActivePage(condition, LIMIT)))
					.containsExactly(s.f4(), s.f2(), s.f1());
			assertThat(feedRepository.countActive(condition)).isEqualTo(3L);
		}

		@Test
		@DisplayName("태그 필터와 region을 함께 적용한다")
		void taggedWithRegion() {
			Scenario s = scenario();

			FeedListCondition condition = condition(s.teamId(), null, GANGNAM, null, List.of(s.tagA(), s.tagB()));

			assertThat(ids(feedRepository.findActivePage(condition, LIMIT)))
					.containsExactly(s.f5(), s.f1());
			assertThat(feedRepository.countActive(condition)).isEqualTo(2L);
		}

		@Test
		@DisplayName("태그 필터와 placeId를 함께 적용한다")
		void taggedWithPlaceId() {
			Scenario s = scenario();

			FeedListCondition condition = condition(s.teamId(), PLACE_Y, null, null, List.of(s.tagA(), s.tagB()));

			assertThat(ids(feedRepository.findActivePage(condition, LIMIT)))
					.containsExactly(s.f2());
			assertThat(feedRepository.countActive(condition)).isEqualTo(1L);
		}

		@Test
		@DisplayName("태그 필터, placeId, feedType DAILY_QUESTION을 함께 적용한다")
		void taggedWithPlaceIdAndDailyQuestion() {
			Scenario s = scenario();

			FeedListCondition condition = condition(
					s.teamId(), PLACE_X, null, FeedType.DAILY_QUESTION, List.of(s.tagA(), s.tagB()));

			assertThat(ids(feedRepository.findActivePage(condition, LIMIT)))
					.containsExactly(s.f5());
			assertThat(feedRepository.countActive(condition)).isEqualTo(1L);
		}

		@Test
		@DisplayName("limit만큼만 반환한다")
		void respectsLimit() {
			Scenario s = scenario();

			FeedListCondition condition = condition(s.teamId(), null, null, null, List.of());

			assertThat(ids(feedRepository.findActivePage(condition, 3)))
					.containsExactly(s.f6(), s.f5(), s.f4());
		}
	}

	@Nested
	@DisplayName("커서")
	class Cursor {

		@Test
		@DisplayName("커서보다 이전 피드만 반환하고, count는 커서와 무관하게 전체 조건 개수를 낸다")
		void returnsFeedsBeforeCursor() {
			Scenario s = scenario();
			Feed cursorFeed = entityManager().find(Feed.class, s.f5());

			FeedListCondition condition = new FeedListCondition(
					s.teamId(), null, null, null, List.of(s.tagA(), s.tagB()), cursorFeed.getCreatedAt(), cursorFeed.getId());

			assertThat(ids(feedRepository.findActivePage(condition, LIMIT)))
					.containsExactly(s.f4(), s.f2(), s.f1());
			assertThat(feedRepository.countActive(condition)).isEqualTo(4L);
		}

		@Test
		@DisplayName("createdAt이 같으면 id가 작은 피드만 다음 페이지에 온다(태그 필터와 함께)")
		void sameCreatedAtUsesIdTieBreaker() {
			Member authorMember = persist(member("fl-tie-author"));
			Member memberA = persist(member("fl-tie-a"));
			Member memberB = persist(member("fl-tie-b"));
			Team team = persist(team("fl-tie"));
			TeamMember author = persist(teamMember(authorMember, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));
			TeamMember tagA = persist(teamMember(memberA, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
			TeamMember tagB = persist(teamMember(memberB, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));

			LocalDateTime same = BASE_TIME;
			// persist 순서 = id 오름차순. createdAt은 명시적으로 덮어쓴다.
			// newerSmallId: id는 커서보다 작지만 createdAt이 더 늦어 제외돼야 한다.
			Feed newerSmallId = persistFeed(feed(team, author, null, null, same.plusHours(1)), same.plusHours(1), tagA, tagB);
			Feed sameFirst = persistFeed(feed(team, author, null, null, same), same, tagA, tagB);
			// untaggedSame: createdAt·id 조건은 맞지만 B가 태그되지 않아 제외돼야 한다.
			Feed untaggedSame = persistFeed(feed(team, author, null, null, same), same, tagA);
			Feed cursorFeed = persistFeed(feed(team, author, null, null, same), same, tagA, tagB);
			Feed sameAfterCursor = persistFeed(feed(team, author, null, null, same), same, tagA, tagB);
			// olderLargeId: id는 커서보다 크지만 createdAt이 더 이르므로 포함돼야 한다.
			Feed olderLargeId = persistFeed(feed(team, author, null, null, same.minusHours(1)), same.minusHours(1), tagA, tagB);
			flushAndClear();

			assertThat(sameFirst.getCreatedAt()).isEqualTo(same);
			assertThat(cursorFeed.getCreatedAt()).isEqualTo(same);
			assertThat(sameAfterCursor.getCreatedAt()).isEqualTo(same);
			assertThat(sameFirst.getId()).isLessThan(cursorFeed.getId());
			assertThat(sameAfterCursor.getId()).isGreaterThan(cursorFeed.getId());

			FeedListCondition condition = new FeedListCondition(
					team.getId(), null, null, null, List.of(tagA.getId(), tagB.getId()), same, cursorFeed.getId());

			assertThat(ids(feedRepository.findActivePage(condition, LIMIT)))
					.containsExactly(sameFirst.getId(), olderLargeId.getId())
					.doesNotContain(newerSmallId.getId(), untaggedSame.getId(), sameAfterCursor.getId());
			assertThat(feedRepository.countActive(condition)).isEqualTo(5L);
		}
	}

	@Test
	@DisplayName("teamMember, teamMember.member, place, question을 함께 가져오고 images는 가져오지 않는다")
	void fetchesToOneAssociationsOnly() {
		Scenario s = scenario();

		FeedListCondition condition = condition(s.teamId(), PLACE_X, null, FeedType.DAILY_QUESTION, List.of());
		List<Feed> result = feedRepository.findActivePage(condition, LIMIT);

		assertThat(ids(result)).containsExactly(s.f5(), s.f3());
		for (Feed feed : result) {
			assertThat(Hibernate.isInitialized(feed.getTeamMember())).isTrue();
			assertThat(Hibernate.isInitialized(feed.getTeamMember().getMember())).isTrue();
			assertThat(Hibernate.isInitialized(feed.getPlace())).isTrue();
			assertThat(Hibernate.isInitialized(feed.getQuestion())).isTrue();
			assertThat(Hibernate.isInitialized(feed.getImages())).isFalse();
			assertThat(Hibernate.isInitialized(feed.getTags())).isFalse();
		}
	}

	// --- helpers ---

	private record Scenario(
			Long teamId, Long tagA, Long tagB,
			Long f1, Long f2, Long f3, Long f4, Long f5, Long f6, Long deleted, Long other
	) {
	}

	private static FeedListCondition condition(
			Long teamId, String placeId, String region, FeedType feedType, List<Long> taggedTeamMemberIds) {
		return new FeedListCondition(teamId, placeId, region, feedType, taggedTeamMemberIds, null, null);
	}

	private static List<Long> ids(List<Feed> feeds) {
		return feeds.stream().map(Feed::getId).toList();
	}

	private Feed persistFeed(Feed feed, LocalDateTime createdAt, TeamMember... taggedTeamMembers) {
		for (TeamMember tagged : taggedTeamMembers) {
			feed.addTag(FeedTag.builder().teamMember(tagged).build());
		}
		return persistWithCreatedAt(feed, createdAt);
	}

	private Place place(String externalPlaceId, String region) {
		return Place.builder()
				.externalPlaceId(externalPlaceId)
				.name(externalPlaceId + " 테스트 장소")
				.latitude(new BigDecimal("37.5665"))
				.longitude(new BigDecimal("126.9780"))
				.address(region + " 테스트동")
				.region(region)
				.build();
	}

	private Question question() {
		return Question.builder()
				.content("오늘의 질문")
				.active(true)
				.createdAt(BASE_TIME)
				.updatedAt(BASE_TIME)
				.build();
	}

	private Feed feed(Team team, TeamMember author, Place place, Question question, LocalDateTime createdAt) {
		Feed feed = Feed.builder()
				.team(team).teamMember(author).place(place)
				.content("테스트 피드").meetingDate(LocalDate.of(2026, 5, 15))
				.createdAt(createdAt).updatedAt(createdAt)
				.build();
		if (question != null) {
			feed.setDailyAnswer(question, createdAt.toLocalDate(), null);
		}
		return feed;
	}
}
