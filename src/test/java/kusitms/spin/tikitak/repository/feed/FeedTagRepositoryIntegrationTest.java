package kusitms.spin.tikitak.repository.feed;

import kusitms.spin.tikitak.domain.feed.entity.Feed;
import kusitms.spin.tikitak.domain.feed.entity.FeedTag;
import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.team.entity.Team;
import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberRole;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus;
import kusitms.spin.tikitak.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FeedTagRepositoryIntegrationTest extends IntegrationTest {

	private static final LocalDateTime MAY_START = LocalDateTime.of(2026, 5, 1, 0, 0);
	private static final LocalDateTime JUNE_START = LocalDateTime.of(2026, 6, 1, 0, 0);
	private static final LocalDateTime MAY_TIME = LocalDateTime.of(2026, 5, 15, 12, 0);
	private static final LocalDateTime APRIL_TIME = LocalDateTime.of(2026, 4, 30, 12, 0);

	@Autowired
	private FeedTagRepository feedTagRepository;

	@Test
	@DisplayName("태그 수 내림차순, 동점이면 닉네임 오름차순(가나다)으로 정렬한다")
	void sortsByTagCountDescThenNicknameAsc() {
		Member m1 = persist(member("sort-1"));
		Member m2 = persist(member("sort-2"));
		Member m3 = persist(member("sort-3"));
		Member author = persist(member("sort-author"));
		Team team = persist(team("sort"));

		TeamMember tmDaDa = persist(teamMemberWithNickname(m1, team, "다다"));
		TeamMember tmNaNa = persist(teamMemberWithNickname(m2, team, "나나"));
		TeamMember tmGaGa = persist(teamMemberWithNickname(m3, team, "가가"));
		TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));

		// 다다: 3 tags, 나나 · 가가: 각 1 tag → 동점 시 가나다 순
		Feed feed1 = feedWithCreatedAt(team, authorTm);
		feed1.addTag(FeedTag.builder().teamMember(tmDaDa).build());
		feed1.addTag(FeedTag.builder().teamMember(tmNaNa).build());
		feed1.addTag(FeedTag.builder().teamMember(tmGaGa).build());
		persist(feed1);

		Feed feed2 = feedWithCreatedAt(team, authorTm);
		feed2.addTag(FeedTag.builder().teamMember(tmDaDa).build());
		persist(feed2);

		Feed feed3 = feedWithCreatedAt(team, authorTm);
		feed3.addTag(FeedTag.builder().teamMember(tmDaDa).build());
		persist(feed3);

		flushAndClear();

		List<Object[]> result = feedTagRepository.findTopTaggedMembersByTeamAndMonth(
				team.getId(), MAY_START, JUNE_START, TeamMemberStatus.ACTIVE);

		assertThat(result).hasSize(3);
		assertThat(((TeamMember) result.get(0)[0]).getNickname()).isEqualTo("다다");
		assertThat((Long) result.get(0)[1]).isEqualTo(3L);
		assertThat(((TeamMember) result.get(1)[0]).getNickname()).isEqualTo("가가");
		assertThat((Long) result.get(1)[1]).isEqualTo(1L);
		assertThat(((TeamMember) result.get(2)[0]).getNickname()).isEqualTo("나나");
		assertThat((Long) result.get(2)[1]).isEqualTo(1L);
	}

	@Test
	@DisplayName("createdAt이 당월 범위 밖인 피드의 태그는 집계하지 않는다")
	void excludesTagsFromFeedsOutsideMonth() {
		Member m = persist(member("outside-1"));
		Member author = persist(member("outside-author"));
		Team team = persist(team("outside"));

		TeamMember tagged = persist(teamMember(m, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
		TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));

		Feed feed = feedWithCreatedAt(team, authorTm, APRIL_TIME);
		feed.addTag(FeedTag.builder().teamMember(tagged).build());
		persist(feed);

		flushAndClear();

		List<Object[]> result = feedTagRepository.findTopTaggedMembersByTeamAndMonth(
				team.getId(), MAY_START, JUNE_START, TeamMemberStatus.ACTIVE);

		assertThat(result).isEmpty();
	}

	@Test
	@DisplayName("삭제된 피드의 태그는 집계하지 않는다")
	void excludesTagsFromDeletedFeeds() {
		Member m = persist(member("deleted-1"));
		Member author = persist(member("deleted-author"));
		Team team = persist(team("deleted"));

		TeamMember tagged = persist(teamMember(m, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
		TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));

		Feed deletedFeed = Feed.builder()
				.team(team).teamMember(authorTm)
				.content("삭제된 피드")
				.createdAt(MAY_TIME).updatedAt(MAY_TIME).deletedAt(MAY_TIME)
				.build();
		deletedFeed.addTag(FeedTag.builder().teamMember(tagged).build());
		persist(deletedFeed);

		flushAndClear();

		List<Object[]> result = feedTagRepository.findTopTaggedMembersByTeamAndMonth(
				team.getId(), MAY_START, JUNE_START, TeamMemberStatus.ACTIVE);

		assertThat(result).isEmpty();
	}

	@Test
	@DisplayName("탈퇴한 팀원의 태그는 집계하지 않는다")
	void excludesTagsFromLeftMembers() {
		Member m = persist(member("left-1"));
		Member author = persist(member("left-author"));
		Team team = persist(team("left"));

		TeamMember leftMember = persist(teamMember(m, team, TeamMemberRole.MEMBER, TeamMemberStatus.LEFT));
		TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));

		Feed feed = feedWithCreatedAt(team, authorTm);
		feed.addTag(FeedTag.builder().teamMember(leftMember).build());
		persist(feed);

		flushAndClear();

		List<Object[]> result = feedTagRepository.findTopTaggedMembersByTeamAndMonth(
				team.getId(), MAY_START, JUNE_START, TeamMemberStatus.ACTIVE);

		assertThat(result).isEmpty();
	}

	@Test
	@DisplayName("다른 팀의 피드 태그는 집계하지 않는다")
	void excludesTagsFromOtherTeam() {
		Member m = persist(member("other-1"));
		Member author = persist(member("other-author"));
		Team myTeam = persist(team("other-mine"));
		Team otherTeam = persist(team("other-theirs"));

		TeamMember otherTagged = persist(teamMember(m, otherTeam, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
		TeamMember otherAuthor = persist(teamMember(author, otherTeam, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));

		Feed otherFeed = feedWithCreatedAt(otherTeam, otherAuthor);
		otherFeed.addTag(FeedTag.builder().teamMember(otherTagged).build());
		persist(otherFeed);

		flushAndClear();

		List<Object[]> result = feedTagRepository.findTopTaggedMembersByTeamAndMonth(
				myTeam.getId(), MAY_START, JUNE_START, TeamMemberStatus.ACTIVE);

		assertThat(result).isEmpty();
	}

	// --- helpers ---

	private TeamMember teamMemberWithNickname(Member member, Team team, String nickname) {
		return TeamMember.builder()
				.team(team).member(member)
				.nickname(nickname)
				.profileImgUrl("https://example.com/profile.png")
				.role(TeamMemberRole.MEMBER)
				.status(TeamMemberStatus.ACTIVE)
				.createdAt(BASE_TIME)
				.build();
	}

	private Feed feedWithCreatedAt(Team team, TeamMember author) {
		return feedWithCreatedAt(team, author, MAY_TIME);
	}

	private Feed feedWithCreatedAt(Team team, TeamMember author, LocalDateTime createdAt) {
		return Feed.builder()
				.team(team).teamMember(author)
				.content("테스트 피드")
				.createdAt(createdAt).updatedAt(createdAt)
				.build();
	}
}
