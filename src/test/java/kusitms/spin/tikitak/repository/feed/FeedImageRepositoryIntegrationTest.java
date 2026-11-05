package kusitms.spin.tikitak.repository.feed;

import kusitms.spin.tikitak.domain.feed.entity.Feed;
import kusitms.spin.tikitak.domain.feed.entity.FeedImage;
import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.team.entity.Team;
import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberRole;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus;
import kusitms.spin.tikitak.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FeedImageRepositoryIntegrationTest extends IntegrationTest {

	@Autowired
	private FeedImageRepository feedImageRepository;

	@Test
	@DisplayName("여러 피드에 걸친 이미지를 feedId in (...)으로 한 번에 조회한다")
	void findsActiveImagesAcrossMultipleFeeds() {
		Member author = persist(member("images-author"));
		Team team = persist(team("images"));
		TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));

		Feed feed1 = persist(feed(team, authorTm));
		addImage(feed1, 0);
		addImage(feed1, 1);

		Feed feed2 = persist(feed(team, authorTm));
		addImage(feed2, 0);

		Feed feed3 = persist(feed(team, authorTm));
		addImage(feed3, 0);

		flushAndClear();

		List<FeedImage> result = feedImageRepository.findActiveByFeedIds(List.of(feed1.getId(), feed2.getId()));

		assertThat(result).hasSize(3);
		assertThat(result.stream().filter(image -> image.getFeed().getId().equals(feed1.getId())).count())
				.isEqualTo(2);
		assertThat(result.stream().filter(image -> image.getFeed().getId().equals(feed2.getId())).count())
				.isEqualTo(1);
		assertThat(result.stream().map(image -> image.getFeed().getId()))
				.doesNotContain(feed3.getId());
	}

	@Test
	@DisplayName("orderIndex 값을 그대로 반환해 호출부에서 정렬할 수 있다")
	void returnsImagesWithOrderIndexIntact() {
		Member author = persist(member("images-order-author"));
		Team team = persist(team("images-order"));
		TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));

		Feed feed = persist(feed(team, authorTm));
		addImage(feed, 1);
		addImage(feed, 0);

		flushAndClear();

		List<FeedImage> result = feedImageRepository.findActiveByFeedIds(List.of(feed.getId())).stream()
				.sorted(Comparator.comparing(FeedImage::getOrderIndex))
				.toList();

		assertThat(result).extracting(FeedImage::getOrderIndex).containsExactly(0, 1);
	}

	@Test
	@DisplayName("삭제된 피드의 이미지는 제외한다")
	void excludesImagesFromDeletedFeeds() {
		Member author = persist(member("images-deleted-author"));
		Team team = persist(team("images-deleted"));
		TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));

		Feed deletedFeed = Feed.builder()
				.team(team).teamMember(authorTm)
				.content("삭제된 피드")
				.createdAt(BASE_TIME).updatedAt(BASE_TIME).deletedAt(BASE_TIME)
				.build();
		persist(deletedFeed);
		addImage(deletedFeed, 0);

		flushAndClear();

		List<FeedImage> result = feedImageRepository.findActiveByFeedIds(List.of(deletedFeed.getId()));

		assertThat(result).isEmpty();
	}

	private Feed feed(Team team, TeamMember author) {
		return Feed.builder()
				.team(team).teamMember(author)
				.content("테스트 피드")
				.createdAt(BASE_TIME).updatedAt(BASE_TIME)
				.build();
	}

	private void addImage(Feed feed, int orderIndex) {
		FeedImage image = FeedImage.builder()
				.imgUrl("https://example.com/feed-image.png")
				.orderIndex(orderIndex)
				.createdAt(BASE_TIME)
				.build();
		feed.addImage(image);
		persist(image);
	}
}
