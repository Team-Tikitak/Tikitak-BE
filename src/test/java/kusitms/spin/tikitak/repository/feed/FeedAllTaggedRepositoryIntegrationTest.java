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

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FeedAllTaggedRepositoryIntegrationTest extends IntegrationTest {

	private static final LocalDate IN_MAY = LocalDate.of(2026, 5, 15);

	@Autowired
	private FeedRepository feedRepository;

	@Test
	@DisplayName("현재 활성 팀원 전원이 태그된 피드를 반환한다")
	void returnsAllTaggedFeeds() {
		Member m1 = persist(member("at-all-1"));
		Member m2 = persist(member("at-all-2"));
		Member author = persist(member("at-all-author"));
		Team team = persist(team("at-all"));

		TeamMember tm1 = persist(teamMember(m1, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
		TeamMember tm2 = persist(teamMember(m2, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
		TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));

		Feed feed = feed(team, authorTm, IN_MAY, BASE_TIME);
		persist(feed);
		tag(feed, tm1);
		tag(feed, tm2);
		tag(feed, authorTm);

		flushAndClear();

		assertThat(feedRepository.findAllTaggedFeedIds(team.getId())).containsExactly(feed.getId());
	}

	@Test
	@DisplayName("일부 팀원만 태그된 피드는 제외한다")
	void excludesPartiallyTaggedFeeds() {
		Member m1 = persist(member("at-partial-1"));
		Member m2 = persist(member("at-partial-2"));
		Member author = persist(member("at-partial-author"));
		Team team = persist(team("at-partial"));

		TeamMember tm1 = persist(teamMember(m1, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
		persist(teamMember(m2, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
		TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));

		Feed feed = feed(team, authorTm, IN_MAY, BASE_TIME);
		persist(feed);
		tag(feed, tm1); // tm2, authorTm 미태그

		flushAndClear();

		assertThat(feedRepository.findAllTaggedFeedIds(team.getId())).isEmpty();
	}

	@Test
	@DisplayName("비활성 팀원은 태그 대상 인원에서 제외한다")
	void excludesInactiveMembersFromTagCount() {
		Member m1 = persist(member("at-inactive-1"));
		Member m2 = persist(member("at-inactive-2"));
		Member author = persist(member("at-inactive-author"));
		Team team = persist(team("at-inactive"));

		TeamMember tm1 = persist(teamMember(m1, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
		persist(teamMember(m2, team, TeamMemberRole.MEMBER, TeamMemberStatus.LEFT)); // 비활성
		TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));

		// 활성 팀원(tm1, authorTm) 2명만 태그 → 활성 팀원 수(2명)와 일치
		Feed feed = feed(team, authorTm, IN_MAY, BASE_TIME);
		persist(feed);
		tag(feed, tm1);
		tag(feed, authorTm);

		flushAndClear();

		assertThat(feedRepository.findAllTaggedFeedIds(team.getId())).containsExactly(feed.getId());
	}

	@Test
	@DisplayName("삭제된 피드는 제외한다")
	void excludesDeletedFeeds() {
		Member author = persist(member("at-del-author"));
		Team team = persist(team("at-del"));
		TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));

		Feed deleted = Feed.builder()
				.team(team).teamMember(authorTm)
				.content("삭제된 피드").meetingDate(IN_MAY)
				.createdAt(BASE_TIME).updatedAt(BASE_TIME).deletedAt(BASE_TIME)
				.build();
		persist(deleted);
		tag(deleted, authorTm);

		flushAndClear();

		assertThat(feedRepository.findAllTaggedFeedIds(team.getId())).isEmpty();
	}

	@Test
	@DisplayName("다른 팀의 피드는 포함하지 않는다")
	void excludesOtherTeamFeeds() {
		Member author = persist(member("at-other-author"));
		Team myTeam = persist(team("at-other-mine"));
		Team otherTeam = persist(team("at-other-theirs"));

		TeamMember otherAuthor = persist(teamMember(author, otherTeam, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));

		Feed otherFeed = feed(otherTeam, otherAuthor, IN_MAY, BASE_TIME);
		persist(otherFeed);
		tag(otherFeed, otherAuthor);

		flushAndClear();

		assertThat(feedRepository.findAllTaggedFeedIds(myTeam.getId())).isEmpty();
	}

	@Test
	@DisplayName("최신순(created_at desc, id desc)으로 정렬해서 반환한다")
	void returnsSortedByCreatedAtDesc() {
		Member author = persist(member("at-sort-author"));
		Team team = persist(team("at-sort"));
		TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));

		Feed older = feed(team, authorTm, IN_MAY, BASE_TIME);
		Feed newer = feed(team, authorTm, IN_MAY, BASE_TIME.plusHours(1));
		persist(older);
		persist(newer);
		tag(older, authorTm);
		tag(newer, authorTm);

		flushAndClear();

		List<Long> result = feedRepository.findAllTaggedFeedIds(team.getId());
		assertThat(result).hasSize(2);
		assertThat(result.get(0)).isEqualTo(newer.getId());
		assertThat(result.get(1)).isEqualTo(older.getId());
	}

	@Test
	@DisplayName("전원 태그 피드가 10개를 초과하면 최대 10개만 반환한다")
	void limitsResultTo10() {
		Member author = persist(member("at-limit-author"));
		Team team = persist(team("at-limit"));
		TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));

		for (int i = 0; i < 11; i++) {
			Feed feed = feed(team, authorTm, IN_MAY, BASE_TIME.plusMinutes(i));
			persist(feed);
			tag(feed, authorTm);
		}

		flushAndClear();

		assertThat(feedRepository.findAllTaggedFeedIds(team.getId())).hasSize(10);
	}

	@Test
	@DisplayName("전원 태그된 피드와 부분 태그된 피드가 섞여 있으면 전원 태그된 피드만 반환한다")
	void returnsOnlyFullyTaggedAmongMixed() {
		Member m1 = persist(member("at-mix-1"));
		Member m2 = persist(member("at-mix-2"));
		Member author = persist(member("at-mix-author"));
		Team team = persist(team("at-mix"));

		TeamMember tm1 = persist(teamMember(m1, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
		TeamMember tm2 = persist(teamMember(m2, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
		TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));

		Feed full = feed(team, authorTm, IN_MAY, BASE_TIME.plusHours(1));
		persist(full);
		tag(full, tm1);
		tag(full, tm2);
		tag(full, authorTm);

		Feed partial = feed(team, authorTm, IN_MAY, BASE_TIME);
		persist(partial);
		tag(partial, tm1);

		flushAndClear();

		List<Long> result = feedRepository.findAllTaggedFeedIds(team.getId());
		assertThat(result).containsExactly(full.getId());
	}

	// --- helpers ---

	private Feed feed(Team team, TeamMember author, LocalDate meetingDate,
			java.time.LocalDateTime createdAt) {
		return Feed.builder()
				.team(team).teamMember(author)
				.content("테스트 피드").meetingDate(meetingDate)
				.createdAt(createdAt).updatedAt(createdAt)
				.build();
	}

	private void tag(Feed feed, TeamMember tm) {
		entityManager().persist(FeedTag.builder().feed(feed).teamMember(tm).build());
	}
}
