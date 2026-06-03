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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FeedCombinationRepositoryIntegrationTest extends IntegrationTest {

	@Autowired
	private FeedRepository feedRepository;

	// ===== findTopCombinationPair =====

	@Nested
	@DisplayName("findTopCombinationPair")
	class FindTopCombinationPair {

		@Test
		@DisplayName("가장 많이 함께 태그된 페어를 반환한다")
		void returnsMostFrequentPair() {
			Member m1 = persist(member("cp-top-1"));
			Member m2 = persist(member("cp-top-2"));
			Member m3 = persist(member("cp-top-3"));
			Member author = persist(member("cp-top-author"));
			Team team = persist(team("cp-top"));

			TeamMember tm1 = persist(teamMember(m1, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
			TeamMember tm2 = persist(teamMember(m2, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
			TeamMember tm3 = persist(teamMember(m3, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
			TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));

			// tm1, tm2: 3번 함께
			for (int i = 0; i < 3; i++) {
				Feed feed = feed(team, authorTm, BASE_TIME.plusMinutes(i));
				persist(feed);
				tag(feed, tm1);
				tag(feed, tm2);
			}
			// tm2, tm3: 1번 함께
			Feed other = feed(team, authorTm, BASE_TIME.plusHours(1));
			persist(other);
			tag(other, tm2);
			tag(other, tm3);

			flushAndClear();

			List<Object[]> result = feedRepository.findTopCombinationPair(team.getId());

			assertThat(result).hasSize(1);
			long mA = ((Number) result.get(0)[0]).longValue();
			long mB = ((Number) result.get(0)[1]).longValue();
			assertThat(mA).isEqualTo(tm1.getId());
			assertThat(mB).isEqualTo(tm2.getId());
		}

		@Test
		@DisplayName("태그가 1개 이하인 피드만 있으면 빈 리스트를 반환한다")
		void returnsEmptyWhenNoMultiTagFeeds() {
			Member author = persist(member("cp-single-author"));
			Team team = persist(team("cp-single"));
			TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));

			Feed feed = feed(team, authorTm, BASE_TIME);
			persist(feed);
			tag(feed, authorTm);

			flushAndClear();

			assertThat(feedRepository.findTopCombinationPair(team.getId())).isEmpty();
		}

		@Test
		@DisplayName("동점 페어가 있으면 team_member_id ASC 기준으로 반환한다")
		void tiebreaksByMemberIdAsc() {
			Member m1 = persist(member("cp-tie-1"));
			Member m2 = persist(member("cp-tie-2"));
			Member m3 = persist(member("cp-tie-3"));
			Member author = persist(member("cp-tie-author"));
			Team team = persist(team("cp-tie"));

			TeamMember tm1 = persist(teamMember(m1, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
			TeamMember tm2 = persist(teamMember(m2, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
			TeamMember tm3 = persist(teamMember(m3, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
			TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));

			// (tm1,tm2), (tm1,tm3) 각 1번씩 동점
			Feed feed1 = feed(team, authorTm, BASE_TIME);
			persist(feed1);
			tag(feed1, tm1);
			tag(feed1, tm2);

			Feed feed2 = feed(team, authorTm, BASE_TIME.plusHours(1));
			persist(feed2);
			tag(feed2, tm1);
			tag(feed2, tm3);

			flushAndClear();

			List<Object[]> result = feedRepository.findTopCombinationPair(team.getId());

			assertThat(result).hasSize(1);
			long mA = ((Number) result.get(0)[0]).longValue();
			long mB = ((Number) result.get(0)[1]).longValue();
			// 동점 시 mA < mB이고 mA가 최소 → tm1이 mA, mB는 tm1보다 id 작은 쪽 없으므로 tm2
			assertThat(mA).isEqualTo(tm1.getId());
			assertThat(mB).isEqualTo(tm2.getId());
		}

		@Test
		@DisplayName("삭제된 피드는 집계에서 제외한다")
		void excludesDeletedFeeds() {
			Member m1 = persist(member("cp-del-1"));
			Member m2 = persist(member("cp-del-2"));
			Member author = persist(member("cp-del-author"));
			Team team = persist(team("cp-del"));

			TeamMember tm1 = persist(teamMember(m1, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
			TeamMember tm2 = persist(teamMember(m2, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
			TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));

			Feed deleted = Feed.builder()
					.team(team).teamMember(authorTm)
					.content("삭제됨")
					.createdAt(BASE_TIME).updatedAt(BASE_TIME).deletedAt(BASE_TIME)
					.build();
			persist(deleted);
			tag(deleted, tm1);
			tag(deleted, tm2);

			flushAndClear();

			assertThat(feedRepository.findTopCombinationPair(team.getId())).isEmpty();
		}

		@Test
		@DisplayName("탈퇴한 멤버가 포함된 페어는 선정에서 제외한다")
		void excludesPairsWithInactiveMembers() {
			Member m1 = persist(member("cp-left-1"));
			Member m2 = persist(member("cp-left-2"));
			Member m3 = persist(member("cp-left-3"));
			Member author = persist(member("cp-left-author"));
			Team team = persist(team("cp-left"));

			TeamMember tm1 = persist(teamMember(m1, team, TeamMemberRole.MEMBER, TeamMemberStatus.LEFT));
			TeamMember tm2 = persist(teamMember(m2, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
			TeamMember tm3 = persist(teamMember(m3, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
			TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));

			// tm1(탈퇴), tm2: 3번 — 점수 높지만 탈퇴 멤버 포함
			for (int i = 0; i < 3; i++) {
				Feed feed = feed(team, authorTm, BASE_TIME.plusMinutes(i));
				persist(feed);
				tag(feed, tm1);
				tag(feed, tm2);
			}
			// tm2, tm3: 1번 — 활성 멤버끼리
			Feed active = feed(team, authorTm, BASE_TIME.plusHours(1));
			persist(active);
			tag(active, tm2);
			tag(active, tm3);

			flushAndClear();

			List<Object[]> result = feedRepository.findTopCombinationPair(team.getId());

			assertThat(result).hasSize(1);
			long mA = ((Number) result.get(0)[0]).longValue();
			long mB = ((Number) result.get(0)[1]).longValue();
			assertThat(mA).isEqualTo(tm2.getId());
			assertThat(mB).isEqualTo(tm3.getId());
		}

		@Test
		@DisplayName("다른 팀 피드는 집계에서 제외한다")
		void excludesOtherTeamFeeds() {
			Member m1 = persist(member("cp-other-1"));
			Member m2 = persist(member("cp-other-2"));
			Member author = persist(member("cp-other-author"));
			Team myTeam = persist(team("cp-other-mine"));
			Team otherTeam = persist(team("cp-other-theirs"));

			TeamMember otherTm1 = persist(teamMember(m1, otherTeam, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
			TeamMember otherTm2 = persist(teamMember(m2, otherTeam, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
			TeamMember otherAuthor = persist(teamMember(author, otherTeam, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));

			Feed otherFeed = feed(otherTeam, otherAuthor, BASE_TIME);
			persist(otherFeed);
			tag(otherFeed, otherTm1);
			tag(otherFeed, otherTm2);

			flushAndClear();

			assertThat(feedRepository.findTopCombinationPair(myTeam.getId())).isEmpty();
		}
	}

	// ===== findAlwaysCoTaggedMemberIds =====

	@Nested
	@DisplayName("findAlwaysCoTaggedMemberIds")
	class FindAlwaysCoTaggedMemberIds {

		@Test
		@DisplayName("페어(mA, mB) 자신을 포함해 항상 동반 등장한 멤버를 반환한다")
		void returnsPairMembersAndAlwaysPresentOthers() {
			Member m1 = persist(member("ac-basic-1"));
			Member m2 = persist(member("ac-basic-2"));
			Member m3 = persist(member("ac-basic-3"));
			Member author = persist(member("ac-basic-author"));
			Team team = persist(team("ac-basic"));

			TeamMember tm1 = persist(teamMember(m1, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
			TeamMember tm2 = persist(teamMember(m2, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
			TeamMember tm3 = persist(teamMember(m3, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
			TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));

			// tm1, tm2, tm3 항상 함께
			for (int i = 0; i < 3; i++) {
				Feed feed = feed(team, authorTm, BASE_TIME.plusMinutes(i));
				persist(feed);
				tag(feed, tm1);
				tag(feed, tm2);
				tag(feed, tm3);
			}

			flushAndClear();

			List<Long> result = feedRepository.findAlwaysCoTaggedMemberIds(
					team.getId(), tm1.getId(), tm2.getId());

			assertThat(result).containsExactlyInAnyOrder(tm1.getId(), tm2.getId(), tm3.getId());
		}

		@Test
		@DisplayName("일부 피드에만 등장한 멤버는 제외한다")
		void excludesPartiallyPresentMembers() {
			Member m1 = persist(member("ac-partial-1"));
			Member m2 = persist(member("ac-partial-2"));
			Member m3 = persist(member("ac-partial-3"));
			Member author = persist(member("ac-partial-author"));
			Team team = persist(team("ac-partial"));

			TeamMember tm1 = persist(teamMember(m1, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
			TeamMember tm2 = persist(teamMember(m2, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
			TeamMember tm3 = persist(teamMember(m3, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
			TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));

			// feed1: tm1, tm2, tm3
			Feed feed1 = feed(team, authorTm, BASE_TIME);
			persist(feed1);
			tag(feed1, tm1);
			tag(feed1, tm2);
			tag(feed1, tm3);

			// feed2: tm1, tm2만 (tm3 없음)
			Feed feed2 = feed(team, authorTm, BASE_TIME.plusHours(1));
			persist(feed2);
			tag(feed2, tm1);
			tag(feed2, tm2);

			flushAndClear();

			List<Long> result = feedRepository.findAlwaysCoTaggedMemberIds(
					team.getId(), tm1.getId(), tm2.getId());

			assertThat(result).containsExactlyInAnyOrder(tm1.getId(), tm2.getId());
			assertThat(result).doesNotContain(tm3.getId());
		}

		@Test
		@DisplayName("페어만 있고 추가 동반 멤버가 없으면 페어 2명만 반환한다")
		void returnsPairOnlyWhenNoAlwaysCoTagged() {
			Member m1 = persist(member("ac-pair-1"));
			Member m2 = persist(member("ac-pair-2"));
			Member author = persist(member("ac-pair-author"));
			Team team = persist(team("ac-pair"));

			TeamMember tm1 = persist(teamMember(m1, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
			TeamMember tm2 = persist(teamMember(m2, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
			TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));

			Feed feed = feed(team, authorTm, BASE_TIME);
			persist(feed);
			tag(feed, tm1);
			tag(feed, tm2);

			flushAndClear();

			List<Long> result = feedRepository.findAlwaysCoTaggedMemberIds(
					team.getId(), tm1.getId(), tm2.getId());

			assertThat(result).containsExactlyInAnyOrder(tm1.getId(), tm2.getId());
		}
	}

	// ===== findCombinationFeedIds =====

	@Nested
	@DisplayName("findCombinationFeedIds")
	class FindCombinationFeedIds {

		@Test
		@DisplayName("두 멤버가 모두 태그된 피드를 반환한다")
		void returnsFeedsWithBothMembersTagged() {
			Member m1 = persist(member("cf-both-1"));
			Member m2 = persist(member("cf-both-2"));
			Member author = persist(member("cf-both-author"));
			Team team = persist(team("cf-both"));

			TeamMember tm1 = persist(teamMember(m1, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
			TeamMember tm2 = persist(teamMember(m2, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
			TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));

			Feed feed = feed(team, authorTm, BASE_TIME);
			persist(feed);
			tag(feed, tm1);
			tag(feed, tm2);

			flushAndClear();

			List<Long> result = feedRepository.findCombinationFeedIds(
					team.getId(), tm1.getId(), tm2.getId());

			assertThat(result).containsExactly(feed.getId());
		}

		@Test
		@DisplayName("한 명만 태그된 피드는 제외한다")
		void excludesFeedsWithOnlyOneMember() {
			Member m1 = persist(member("cf-one-1"));
			Member m2 = persist(member("cf-one-2"));
			Member author = persist(member("cf-one-author"));
			Team team = persist(team("cf-one"));

			TeamMember tm1 = persist(teamMember(m1, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
			TeamMember tm2 = persist(teamMember(m2, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
			TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));

			Feed onlyTm1 = feed(team, authorTm, BASE_TIME);
			persist(onlyTm1);
			tag(onlyTm1, tm1);

			flushAndClear();

			assertThat(feedRepository.findCombinationFeedIds(
					team.getId(), tm1.getId(), tm2.getId())).isEmpty();
		}

		@Test
		@DisplayName("삭제된 피드는 제외한다")
		void excludesDeletedFeeds() {
			Member m1 = persist(member("cf-del-1"));
			Member m2 = persist(member("cf-del-2"));
			Member author = persist(member("cf-del-author"));
			Team team = persist(team("cf-del"));

			TeamMember tm1 = persist(teamMember(m1, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
			TeamMember tm2 = persist(teamMember(m2, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
			TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));

			Feed deleted = Feed.builder()
					.team(team).teamMember(authorTm)
					.content("삭제됨")
					.createdAt(BASE_TIME).updatedAt(BASE_TIME).deletedAt(BASE_TIME)
					.build();
			persist(deleted);
			tag(deleted, tm1);
			tag(deleted, tm2);

			flushAndClear();

			assertThat(feedRepository.findCombinationFeedIds(
					team.getId(), tm1.getId(), tm2.getId())).isEmpty();
		}

		@Test
		@DisplayName("최신순(created_at desc, id desc)으로 정렬해서 반환한다")
		void returnsSortedByCreatedAtDesc() {
			Member m1 = persist(member("cf-sort-1"));
			Member m2 = persist(member("cf-sort-2"));
			Member author = persist(member("cf-sort-author"));
			Team team = persist(team("cf-sort"));

			TeamMember tm1 = persist(teamMember(m1, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
			TeamMember tm2 = persist(teamMember(m2, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
			TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));

			Feed older = feed(team, authorTm, BASE_TIME);
			Feed newer = feed(team, authorTm, BASE_TIME.plusHours(1));
			persist(older);
			persist(newer);
			tag(older, tm1);
			tag(older, tm2);
			tag(newer, tm1);
			tag(newer, tm2);

			flushAndClear();

			List<Long> result = feedRepository.findCombinationFeedIds(
					team.getId(), tm1.getId(), tm2.getId());

			assertThat(result).hasSize(2);
			assertThat(result.get(0)).isEqualTo(newer.getId());
			assertThat(result.get(1)).isEqualTo(older.getId());
		}

		@Test
		@DisplayName("페어 피드가 10개를 초과하면 최대 10개만 반환한다")
		void limitsResultTo10() {
			Member m1 = persist(member("cf-limit-1"));
			Member m2 = persist(member("cf-limit-2"));
			Member author = persist(member("cf-limit-author"));
			Team team = persist(team("cf-limit"));

			TeamMember tm1 = persist(teamMember(m1, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
			TeamMember tm2 = persist(teamMember(m2, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
			TeamMember authorTm = persist(teamMember(author, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));

			for (int i = 0; i < 11; i++) {
				Feed feed = feed(team, authorTm, BASE_TIME.plusMinutes(i));
				persist(feed);
				tag(feed, tm1);
				tag(feed, tm2);
			}

			flushAndClear();

			assertThat(feedRepository.findCombinationFeedIds(
					team.getId(), tm1.getId(), tm2.getId())).hasSize(10);
		}

		@Test
		@DisplayName("다른 팀의 피드는 제외한다")
		void excludesOtherTeamFeeds() {
			Member m1 = persist(member("cf-other-1"));
			Member m2 = persist(member("cf-other-2"));
			Member author = persist(member("cf-other-author"));
			Team myTeam = persist(team("cf-other-mine"));
			Team otherTeam = persist(team("cf-other-theirs"));

			TeamMember otherTm1 = persist(teamMember(m1, otherTeam, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
			TeamMember otherTm2 = persist(teamMember(m2, otherTeam, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
			TeamMember otherAuthor = persist(teamMember(author, otherTeam, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));

			Feed otherFeed = feed(otherTeam, otherAuthor, BASE_TIME);
			persist(otherFeed);
			tag(otherFeed, otherTm1);
			tag(otherFeed, otherTm2);

			flushAndClear();

			assertThat(feedRepository.findCombinationFeedIds(
					myTeam.getId(), otherTm1.getId(), otherTm2.getId())).isEmpty();
		}
	}

	// --- helpers ---

	private Feed feed(Team team, TeamMember author, java.time.LocalDateTime createdAt) {
		return Feed.builder()
				.team(team).teamMember(author)
				.content("테스트 피드")
				.createdAt(createdAt).updatedAt(createdAt)
				.build();
	}

	private void tag(Feed feed, TeamMember tm) {
		entityManager().persist(FeedTag.builder().feed(feed).teamMember(tm).build());
	}
}
