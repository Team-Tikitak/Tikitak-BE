package kusitms.spin.tikitak.repository.feed;

import kusitms.spin.tikitak.domain.feed.entity.Feed;
import kusitms.spin.tikitak.domain.feed.entity.FeedImage;
import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.place.entity.Place;
import kusitms.spin.tikitak.domain.team.entity.Team;
import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberRole;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus;
import kusitms.spin.tikitak.service.home.dto.RegionRow;
import kusitms.spin.tikitak.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FeedRegionRepositoryIntegrationTest extends IntegrationTest {

	private static final LocalDate IN_MAY = LocalDate.of(2026, 5, 15);

	@Autowired
	private FeedRepository feedRepository;

	// ===== countActiveFeedsWithRegion =====

	@Nested
	@DisplayName("countActiveFeedsWithRegion")
	class CountActiveFeedsWithRegion {

		@Test
		@DisplayName("region이 있는 장소에 연결된 활성 피드 수를 반환한다")
		void countsFeedsWithRegion() {
			Member author = persist(member("rg-cnt-author"));
			Team team = persist(team("rg-cnt"));
			TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));

			Place p1 = persist(place("서울 강남구"));
			Place p2 = persist(place("경기 성남시 분당구"));

			persist(feed(team, authorTm, p1, BASE_TIME));
			persist(feed(team, authorTm, p1, BASE_TIME.plusHours(1)));
			persist(feed(team, authorTm, p2, BASE_TIME.plusHours(2)));

			flushAndClear();

			assertThat(feedRepository.countActiveFeedsWithRegion(team.getId())).isEqualTo(3);
		}

		@Test
		@DisplayName("장소 없는 피드는 제외한다")
		void excludesFeedsWithoutPlace() {
			Member author = persist(member("rg-cnt-noplace-author"));
			Team team = persist(team("rg-cnt-noplace"));
			TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));

			persist(feed(team, authorTm, null, BASE_TIME));

			flushAndClear();

			assertThat(feedRepository.countActiveFeedsWithRegion(team.getId())).isZero();
		}

		@Test
		@DisplayName("region이 null인 장소는 제외한다")
		void excludesFeedsWithNullRegion() {
			Member author = persist(member("rg-cnt-nullrg-author"));
			Team team = persist(team("rg-cnt-nullrg"));
			TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));

			Place noRegion = persist(Place.builder()
					.name("지역 없는 장소")
					.latitude(new BigDecimal("37.5665"))
					.longitude(new BigDecimal("126.9780"))
					.address("알 수 없는 주소")
					.build());

			persist(feed(team, authorTm, noRegion, BASE_TIME));

			flushAndClear();

			assertThat(feedRepository.countActiveFeedsWithRegion(team.getId())).isZero();
		}

		@Test
		@DisplayName("삭제된 피드는 제외한다")
		void excludesDeletedFeeds() {
			Member author = persist(member("rg-cnt-del-author"));
			Team team = persist(team("rg-cnt-del"));
			TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));
			Place place = persist(place("서울 강남구"));

			Feed deleted = Feed.builder()
					.team(team).teamMember(authorTm).place(place)
					.content("삭제됨").meetingDate(IN_MAY)
					.createdAt(BASE_TIME).updatedAt(BASE_TIME).deletedAt(BASE_TIME)
					.build();
			persist(deleted);

			flushAndClear();

			assertThat(feedRepository.countActiveFeedsWithRegion(team.getId())).isZero();
		}
	}

	// ===== findRegionSummaries =====

	@Nested
	@DisplayName("findRegionSummaries")
	class FindRegionSummaries {

		@Test
		@DisplayName("피드 수 내림차순으로 지역을 반환한다")
		void returnsSortedByFeedCountDesc() {
			Member author = persist(member("rg-sort-author"));
			Team team = persist(team("rg-sort"));
			TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));

			Place gangnam = persist(place("서울 강남구"));
			Place bundang = persist(place("경기 성남시 분당구"));

			persist(feed(team, authorTm, gangnam, BASE_TIME));
			persist(feed(team, authorTm, gangnam, BASE_TIME.plusHours(1)));
			persist(feed(team, authorTm, gangnam, BASE_TIME.plusHours(2)));
			persist(feed(team, authorTm, bundang, BASE_TIME));

			flushAndClear();

			List<RegionRow> result = feedRepository.findRegionSummaries(team.getId());

			assertThat(result).hasSize(2);
			assertThat(result.get(0).getRegion()).isEqualTo("서울 강남구");
			assertThat(result.get(0).getFeedCount()).isEqualTo(3L);
			assertThat(result.get(1).getRegion()).isEqualTo("경기 성남시 분당구");
			assertThat(result.get(1).getFeedCount()).isEqualTo(1L);
		}

		@Test
		@DisplayName("각 지역의 가장 최근 피드 이미지를 썸네일로 반환한다")
		void returnsThumbnailFromMostRecentFeed() {
			Member author = persist(member("rg-thumb-author"));
			Team team = persist(team("rg-thumb"));
			TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));
			Place gangnam = persist(place("서울 강남구"));

			Feed older = feed(team, authorTm, gangnam, BASE_TIME);
			older.addImage(feedImage("https://older.jpg"));
			persist(older);

			Feed newer = feed(team, authorTm, gangnam, BASE_TIME.plusHours(1));
			newer.addImage(feedImage("https://newer.jpg"));
			persist(newer);

			flushAndClear();

			List<RegionRow> result = feedRepository.findRegionSummaries(team.getId());

			assertThat(result).hasSize(1);
			assertThat(result.get(0).getFeedId()).isEqualTo(newer.getId());
			assertThat(result.get(0).getThumbnailUrl()).isEqualTo("https://newer.jpg");
		}

		@Test
		@DisplayName("이미지 없는 피드의 썸네일은 null이다")
		void returnsNullThumbnailWhenNoImage() {
			Member author = persist(member("rg-noimg-author"));
			Team team = persist(team("rg-noimg"));
			TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));
			Place place = persist(place("서울 강남구"));

			persist(feed(team, authorTm, place, BASE_TIME));

			flushAndClear();

			List<RegionRow> result = feedRepository.findRegionSummaries(team.getId());

			assertThat(result).hasSize(1);
			assertThat(result.get(0).getThumbnailUrl()).isNull();
		}

		@Test
		@DisplayName("region이 null인 장소의 피드는 제외한다")
		void excludesFeedsWithNullRegion() {
			Member author = persist(member("rg-nullrg-author"));
			Team team = persist(team("rg-nullrg"));
			TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));

			Place noRegion = persist(Place.builder()
					.name("지역 없는 장소")
					.latitude(new BigDecimal("37.5665"))
					.longitude(new BigDecimal("126.9780"))
					.address("알 수 없는 주소")
					.build());
			persist(feed(team, authorTm, noRegion, BASE_TIME));

			flushAndClear();

			assertThat(feedRepository.findRegionSummaries(team.getId())).isEmpty();
		}

		@Test
		@DisplayName("삭제된 피드는 제외한다")
		void excludesDeletedFeeds() {
			Member author = persist(member("rg-del-author"));
			Team team = persist(team("rg-del"));
			TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));
			Place place = persist(place("서울 강남구"));

			Feed deleted = Feed.builder()
					.team(team).teamMember(authorTm).place(place)
					.content("삭제됨").meetingDate(IN_MAY)
					.createdAt(BASE_TIME).updatedAt(BASE_TIME).deletedAt(BASE_TIME)
					.build();
			persist(deleted);

			flushAndClear();

			assertThat(feedRepository.findRegionSummaries(team.getId())).isEmpty();
		}

		@Test
		@DisplayName("다른 팀의 피드는 제외한다")
		void excludesOtherTeamFeeds() {
			Member author = persist(member("rg-other-author"));
			Team myTeam = persist(team("rg-other-mine"));
			Team otherTeam = persist(team("rg-other-theirs"));
			TeamMember otherAuthor = persist(teamMember(author, otherTeam, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));
			Place place = persist(place("서울 강남구"));

			persist(feed(otherTeam, otherAuthor, place, BASE_TIME));

			flushAndClear();

			assertThat(feedRepository.findRegionSummaries(myTeam.getId())).isEmpty();
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

	private FeedImage feedImage(String imgUrl) {
		return FeedImage.builder().imgUrl(imgUrl).orderIndex(0).build();
	}
}
