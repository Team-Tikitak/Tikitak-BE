package kusitms.spin.tikitak.repository.feed;

import kusitms.spin.tikitak.domain.feed.entity.Feed;
import kusitms.spin.tikitak.domain.feed.entity.FeedComment;
import kusitms.spin.tikitak.domain.feed.entity.FeedImage;
import kusitms.spin.tikitak.domain.feed.entity.FeedReaction;
import kusitms.spin.tikitak.domain.feed.enums.FeedReactionType;
import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.team.entity.Team;
import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberRole;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus;
import kusitms.spin.tikitak.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FeedEveryonePickRepositoryIntegrationTest extends IntegrationTest {

	private static final LocalDateTime MAY_START = LocalDateTime.of(2026, 5, 1, 0, 0);
	private static final LocalDateTime JUNE_START = LocalDateTime.of(2026, 6, 1, 0, 0);
	private static final LocalDateTime MAY_TIME = LocalDateTime.of(2026, 5, 15, 12, 0);
	private static final LocalDateTime APRIL_TIME = LocalDateTime.of(2026, 4, 30, 12, 0);

	@Autowired
	private FeedRepository feedRepository;

	@Test
	@DisplayName("반응 수 + 댓글 수 합산 내림차순, 동점이면 최신 피드 순으로 정렬한다")
	void sortsByTotalScoreDescThenCreatedAtDesc() {
		Member m1 = persist(member("ep-sort-1"));
		Member m2 = persist(member("ep-sort-2"));
		Member author = persist(member("ep-sort-author"));
		Team team = persist(team("ep-sort"));

		TeamMember tm1 = persist(teamMember(m1, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
		TeamMember tm2 = persist(teamMember(m2, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
		TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));

		// feedA (older): 반응 2개 → score 2
		Feed feedA = feedWithCreatedAt(team, authorTm, MAY_TIME);
		persist(feedA);
		addReaction(feedA, tm1, FeedReactionType.TAK_LEADER);
		addReaction(feedA, tm2, FeedReactionType.TAK_SPARK);

		// feedB (newer, 동점): 반응 1개 + 댓글 1개 → score 2, 최신이므로 feedA 앞
		Feed feedB = feedWithCreatedAt(team, authorTm, MAY_TIME.plusHours(1));
		FeedImage imgB = feedImage("https://b.jpg");
		feedB.addImage(imgB);
		persist(feedB);
		addReaction(feedB, tm1, FeedReactionType.TAK_LEADER);
		addComment(feedB, imgB, tm1);

		// feedC: 댓글 1개 → score 1
		Feed feedC = feedWithCreatedAt(team, authorTm, MAY_TIME);
		FeedImage imgC = feedImage("https://c.jpg");
		feedC.addImage(imgC);
		persist(feedC);
		addComment(feedC, imgC, tm1);

		flushAndClear();

		List<Long> result = feedRepository.findEveryonePickFeedIds(team.getId(), MAY_START, JUNE_START);

		assertThat(result).hasSize(3);
		assertThat(result.get(0)).isEqualTo(feedB.getId());
		assertThat(result.get(1)).isEqualTo(feedA.getId());
		assertThat(result.get(2)).isEqualTo(feedC.getId());
	}

	@Test
	@DisplayName("createdAt이 당월 범위 밖인 피드는 포함하지 않는다")
	void excludesFeedsOutsideMonth() {
		Member m = persist(member("ep-out-1"));
		Member author = persist(member("ep-out-author"));
		Team team = persist(team("ep-out"));

		TeamMember tm = persist(teamMember(m, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
		TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));

		Feed feed = feedWithCreatedAt(team, authorTm, APRIL_TIME);
		persist(feed);
		addReaction(feed, tm, FeedReactionType.TAK_LEADER);

		flushAndClear();

		assertThat(feedRepository.findEveryonePickFeedIds(team.getId(), MAY_START, JUNE_START)).isEmpty();
	}

	@Test
	@DisplayName("삭제된 피드는 포함하지 않는다")
	void excludesDeletedFeeds() {
		Member m = persist(member("ep-del-1"));
		Member author = persist(member("ep-del-author"));
		Team team = persist(team("ep-del"));

		TeamMember tm = persist(teamMember(m, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
		TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));

		Feed deletedFeed = Feed.builder()
				.team(team).teamMember(authorTm)
				.content("삭제된 피드")
				.createdAt(MAY_TIME).updatedAt(MAY_TIME).deletedAt(MAY_TIME)
				.build();
		persist(deletedFeed);
		addReaction(deletedFeed, tm, FeedReactionType.TAK_LEADER);

		flushAndClear();

		assertThat(feedRepository.findEveryonePickFeedIds(team.getId(), MAY_START, JUNE_START)).isEmpty();
	}

	@Test
	@DisplayName("is_deleted = true인 댓글은 점수 집계에 포함하지 않는다")
	void excludesDeletedComments() {
		Member m = persist(member("ep-cmt-1"));
		Member author = persist(member("ep-cmt-author"));
		Team team = persist(team("ep-cmt"));

		TeamMember tm = persist(teamMember(m, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
		TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));

		// 활성 댓글 1개, 삭제된 댓글 1개 → score 1
		Feed feed = feedWithCreatedAt(team, authorTm, MAY_TIME);
		FeedImage img = feedImage("https://img.jpg");
		feed.addImage(img);
		persist(feed);
		addComment(feed, img, tm);
		addDeletedComment(feed, img, tm);

		flushAndClear();

		List<Long> result = feedRepository.findEveryonePickFeedIds(team.getId(), MAY_START, JUNE_START);
		assertThat(result).hasSize(1);
		assertThat(result.get(0)).isEqualTo(feed.getId());
	}

	@Test
	@DisplayName("상위 10개만 반환한다")
	void limitsResultTo10() {
		Member author = persist(member("ep-limit-author"));
		Team team = persist(team("ep-limit"));
		TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));

		Member reactor = persist(member("ep-limit-reactor"));
		TeamMember reactorTm = persist(teamMember(reactor, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));

		for (int i = 0; i < 11; i++) {
			Feed feed = feedWithCreatedAt(team, authorTm, MAY_TIME.plusMinutes(i));
			persist(feed);
			addReaction(feed, reactorTm, FeedReactionType.TAK_LEADER);
		}

		flushAndClear();

		assertThat(feedRepository.findEveryonePickFeedIds(team.getId(), MAY_START, JUNE_START)).hasSize(10);
	}

	@Test
	@DisplayName("다른 팀의 피드는 포함하지 않는다")
	void excludesOtherTeamFeeds() {
		Member m = persist(member("ep-other-1"));
		Member author = persist(member("ep-other-author"));
		Team myTeam = persist(team("ep-other-mine"));
		Team otherTeam = persist(team("ep-other-theirs"));

		TeamMember otherTm = persist(teamMember(m, otherTeam, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
		TeamMember otherAuthor = persist(teamMember(author, otherTeam, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));

		Feed otherFeed = feedWithCreatedAt(otherTeam, otherAuthor, MAY_TIME);
		persist(otherFeed);
		addReaction(otherFeed, otherTm, FeedReactionType.TAK_LEADER);

		flushAndClear();

		assertThat(feedRepository.findEveryonePickFeedIds(myTeam.getId(), MAY_START, JUNE_START)).isEmpty();
	}

	@Test
	@DisplayName("findActiveByIds는 지정한 ID의 피드를 반환한다")
	void findActiveByIdsReturnsFeedsForGivenIds() {
		Member author = persist(member("ep-ids-author"));
		Team team = persist(team("ep-ids"));
		TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));

		Feed feed1 = feedWithCreatedAt(team, authorTm, MAY_TIME);
		Feed feed2 = feedWithCreatedAt(team, authorTm, MAY_TIME.plusHours(1));
		persist(feed1);
		persist(feed2);

		flushAndClear();

		List<Feed> result = feedRepository.findActiveByIds(team.getId(), List.of(feed1.getId(), feed2.getId()));

		assertThat(result).hasSize(2);
		assertThat(result).extracting(Feed::getId).containsExactlyInAnyOrder(feed1.getId(), feed2.getId());
	}

	@Test
	@DisplayName("findActiveByIds는 삭제된 피드를 반환하지 않는다")
	void findActiveByIdsExcludesDeletedFeeds() {
		Member author = persist(member("ep-ids-del-author"));
		Team team = persist(team("ep-ids-del"));
		TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));

		Feed active = feedWithCreatedAt(team, authorTm, MAY_TIME);
		Feed deleted = Feed.builder()
				.team(team).teamMember(authorTm)
				.content("삭제됨")
				.createdAt(MAY_TIME).updatedAt(MAY_TIME).deletedAt(MAY_TIME)
				.build();
		persist(active);
		persist(deleted);

		flushAndClear();

		List<Feed> result = feedRepository.findActiveByIds(
				team.getId(), List.of(active.getId(), deleted.getId()));

		assertThat(result).hasSize(1);
		assertThat(result.get(0).getId()).isEqualTo(active.getId());
	}

	// --- helpers ---

	private Feed feedWithCreatedAt(Team team, TeamMember author, LocalDateTime createdAt) {
		return Feed.builder()
				.team(team).teamMember(author)
				.content("테스트 피드")
				.createdAt(createdAt).updatedAt(createdAt)
				.build();
	}

	private FeedImage feedImage(String imgUrl) {
		return FeedImage.builder().imgUrl(imgUrl).orderIndex(0).build();
	}

	private void addReaction(Feed feed, TeamMember tm, FeedReactionType type) {
		entityManager().persist(
				FeedReaction.builder().feed(feed).teamMember(tm).reactionType(type).build());
	}

	private void addComment(Feed feed, FeedImage feedImage, TeamMember tm) {
		entityManager().persist(FeedComment.builder()
				.feed(feed).feedImage(feedImage).teamMember(tm)
				.content("댓글")
				.positionX(new BigDecimal("0.500000")).positionY(new BigDecimal("0.500000"))
				.deleted(false)
				.build());
	}

	private void addDeletedComment(Feed feed, FeedImage feedImage, TeamMember tm) {
		entityManager().persist(FeedComment.builder()
				.feed(feed).feedImage(feedImage).teamMember(tm)
				.content("삭제된 댓글")
				.positionX(new BigDecimal("0.500000")).positionY(new BigDecimal("0.500000"))
				.deleted(true)
				.build());
	}
}
