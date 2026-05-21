package kusitms.spin.tikitak.repository.feed;

import kusitms.spin.tikitak.domain.feed.entity.Feed;
import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.place.entity.Place;
import kusitms.spin.tikitak.domain.team.entity.Team;
import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberRole;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus;
import kusitms.spin.tikitak.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FeedRegionFilterRepositoryIntegrationTest extends IntegrationTest {

	private static final LocalDate IN_MAY = LocalDate.of(2026, 5, 15);
	private static final String GANGNAM = "서울 강남구";
	private static final String BUNDANG = "경기 성남시 분당구";

	@Autowired
	private FeedRepository feedRepository;

	// ===== findActiveFirstPageByRegion =====

	@Nested
	@DisplayName("findActiveFirstPageByRegion")
	class FindActiveFirstPageByRegion {

		@Test
		@DisplayName("해당 region의 피드만 반환한다")
		void returnsOnlyFeedsInRegion() {
			Member author = persist(member("rf-first-author"));
			Team team = persist(team("rf-first"));
			TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));

			Place gangnam = persist(place(GANGNAM));
			Place bundang = persist(place(BUNDANG));

			Feed gangnamFeed = persist(feed(team, authorTm, gangnam, BASE_TIME));
			persist(feed(team, authorTm, bundang, BASE_TIME.plusHours(1)));

			flushAndClear();

			List<Feed> result = feedRepository.findActiveFirstPageByRegion(
					team.getId(), GANGNAM, null, PageRequest.of(0, 10));

			assertThat(result).hasSize(1);
			assertThat(result.get(0).getId()).isEqualTo(gangnamFeed.getId());
		}

		@Test
		@DisplayName("장소 없는 피드는 제외한다")
		void excludesFeedsWithoutPlace() {
			Member author = persist(member("rf-first-noplace-author"));
			Team team = persist(team("rf-first-noplace"));
			TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));

			persist(feed(team, authorTm, null, BASE_TIME));

			flushAndClear();

			assertThat(feedRepository.findActiveFirstPageByRegion(
					team.getId(), GANGNAM, null, PageRequest.of(0, 10))).isEmpty();
		}

		@Test
		@DisplayName("삭제된 피드는 제외한다")
		void excludesDeletedFeeds() {
			Member author = persist(member("rf-first-del-author"));
			Team team = persist(team("rf-first-del"));
			TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));
			Place place = persist(place(GANGNAM));

			Feed deleted = Feed.builder()
					.team(team).teamMember(authorTm).place(place)
					.content("삭제됨").meetingDate(IN_MAY)
					.createdAt(BASE_TIME).updatedAt(BASE_TIME).deletedAt(BASE_TIME)
					.build();
			persist(deleted);

			flushAndClear();

			assertThat(feedRepository.findActiveFirstPageByRegion(
					team.getId(), GANGNAM, null, PageRequest.of(0, 10))).isEmpty();
		}

		@Test
		@DisplayName("최신순(createdAt desc, id desc)으로 반환한다")
		void returnsSortedByCreatedAtDesc() {
			Member author = persist(member("rf-first-sort-author"));
			Team team = persist(team("rf-first-sort"));
			TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));
			Place place = persist(place(GANGNAM));

			Feed older = persist(feed(team, authorTm, place, BASE_TIME));
			Feed newer = persist(feed(team, authorTm, place, BASE_TIME.plusHours(1)));

			flushAndClear();

			List<Feed> result = feedRepository.findActiveFirstPageByRegion(
					team.getId(), GANGNAM, null, PageRequest.of(0, 10));

			assertThat(result).hasSize(2);
			assertThat(result.get(0).getId()).isEqualTo(newer.getId());
			assertThat(result.get(1).getId()).isEqualTo(older.getId());
		}

		@Test
		@DisplayName("pageSize+1을 요청해 hasNext를 판단할 수 있다")
		void respectsPageSize() {
			Member author = persist(member("rf-first-page-author"));
			Team team = persist(team("rf-first-page"));
			TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));
			Place place = persist(place(GANGNAM));

			for (int i = 0; i < 5; i++) {
				persist(feed(team, authorTm, place, BASE_TIME.plusMinutes(i)));
			}

			flushAndClear();

			List<Feed> result = feedRepository.findActiveFirstPageByRegion(
					team.getId(), GANGNAM, null, PageRequest.of(0, 3));

			assertThat(result).hasSize(3);
		}

		@Test
		@DisplayName("다른 팀의 피드는 제외한다")
		void excludesOtherTeamFeeds() {
			Member author = persist(member("rf-first-other-author"));
			Team myTeam = persist(team("rf-first-other-mine"));
			Team otherTeam = persist(team("rf-first-other-theirs"));
			TeamMember otherAuthor = persist(teamMember(author, otherTeam, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));
			Place place = persist(place(GANGNAM));

			persist(feed(otherTeam, otherAuthor, place, BASE_TIME));

			flushAndClear();

			assertThat(feedRepository.findActiveFirstPageByRegion(
					myTeam.getId(), GANGNAM, null, PageRequest.of(0, 10))).isEmpty();
		}
	}

	// ===== findActiveCursorPageByRegion =====

	@Nested
	@DisplayName("findActiveCursorPageByRegion")
	class FindActiveCursorPageByRegion {

		@Test
		@DisplayName("커서 이전 피드만 반환한다")
		void returnsOnlyFeedsBeforeCursor() {
			Member author = persist(member("rf-cursor-author"));
			Team team = persist(team("rf-cursor"));
			TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));
			Place place = persist(place(GANGNAM));

			Feed feed1 = persist(feed(team, authorTm, place, BASE_TIME));
			Feed feed2 = persist(feed(team, authorTm, place, BASE_TIME.plusHours(1)));
			Feed feed3 = persist(feed(team, authorTm, place, BASE_TIME.plusHours(2)));

			flushAndClear();

			// feed3이 커서 → feed2, feed1만 반환
			List<Feed> result = feedRepository.findActiveCursorPageByRegion(
					team.getId(), GANGNAM, null,
feed3.getCreatedAt(), feed3.getId(),
					PageRequest.of(0, 10));

			assertThat(result).hasSize(2);
			assertThat(result).extracting(Feed::getId)
					.containsExactly(feed2.getId(), feed1.getId());
		}

		@Test
		@DisplayName("createdAt이 같으면 id가 작은 피드만 반환한다")
		void returnsOnlyFeedsWithSmallerIdWhenSameCreatedAt() {
			Member author = persist(member("rf-cursor-same-author"));
			Team team = persist(team("rf-cursor-same"));
			TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));
			Place place = persist(place(GANGNAM));

			Feed feed1 = persist(feed(team, authorTm, place, BASE_TIME));
			Feed feed2 = persist(feed(team, authorTm, place, BASE_TIME));
			Feed feed3 = persist(feed(team, authorTm, place, BASE_TIME));

			flushAndClear();

			// feed2를 커서로 → id < feed2.id 인 feed1만 반환
			List<Feed> result = feedRepository.findActiveCursorPageByRegion(
					team.getId(), GANGNAM, null,
feed2.getCreatedAt(), feed2.getId(),
					PageRequest.of(0, 10));

			assertThat(result).hasSize(1);
			assertThat(result.get(0).getId()).isEqualTo(feed1.getId());
		}

		@Test
		@DisplayName("해당 region 피드만 커서 범위에서 반환한다")
		void filtersRegionWithinCursor() {
			Member author = persist(member("rf-cursor-region-author"));
			Team team = persist(team("rf-cursor-region"));
			TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));

			Place gangnam = persist(place(GANGNAM));
			Place bundang = persist(place(BUNDANG));

			Feed gangnamFeed = persist(feed(team, authorTm, gangnam, BASE_TIME));
			Feed bundangFeed = persist(feed(team, authorTm, bundang, BASE_TIME.plusHours(1)));
			Feed cursor = persist(feed(team, authorTm, gangnam, BASE_TIME.plusHours(2)));

			flushAndClear();

			List<Feed> result = feedRepository.findActiveCursorPageByRegion(
					team.getId(), GANGNAM, null,
cursor.getCreatedAt(), cursor.getId(),
					PageRequest.of(0, 10));

			assertThat(result).hasSize(1);
			assertThat(result.get(0).getId()).isEqualTo(gangnamFeed.getId());
		}

		@Test
		@DisplayName("삭제된 피드는 커서 범위에서도 제외한다")
		void excludesDeletedFeedsWithinCursor() {
			Member author = persist(member("rf-cursor-del-author"));
			Team team = persist(team("rf-cursor-del"));
			TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));
			Place place = persist(place(GANGNAM));

			Feed deleted = Feed.builder()
					.team(team).teamMember(authorTm).place(place)
					.content("삭제됨").meetingDate(IN_MAY)
					.createdAt(BASE_TIME).updatedAt(BASE_TIME).deletedAt(BASE_TIME)
					.build();
			persist(deleted);

			Feed cursor = persist(feed(team, authorTm, place, BASE_TIME.plusHours(1)));

			flushAndClear();

			List<Feed> result = feedRepository.findActiveCursorPageByRegion(
					team.getId(), GANGNAM, null,
cursor.getCreatedAt(), cursor.getId(),
					PageRequest.of(0, 10));

			assertThat(result).isEmpty();
		}
	}

	// --- helpers ---

	private Place place(String region) {
		return Place.builder()
				.name(region + " 테스트 장소")
				.latitude(new BigDecimal("37.5665"))
				.longitude(new BigDecimal("126.9780"))
				.address(region + " 테스트동")
				.region(region)
				.build();
	}

	private Feed feed(Team team, TeamMember author, Place place,
			java.time.LocalDateTime createdAt) {
		return Feed.builder()
				.team(team).teamMember(author).place(place)
				.content("테스트 피드").meetingDate(IN_MAY)
				.createdAt(createdAt).updatedAt(createdAt)
				.build();
	}
}
