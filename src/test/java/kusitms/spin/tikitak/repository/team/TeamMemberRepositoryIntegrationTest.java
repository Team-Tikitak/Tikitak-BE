package kusitms.spin.tikitak.repository.team;

import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.feed.entity.Feed;
import kusitms.spin.tikitak.domain.team.entity.Team;
import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberRole;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus;
import kusitms.spin.tikitak.domain.team.enums.TeamStatus;
import kusitms.spin.tikitak.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TeamMemberRepositoryIntegrationTest extends IntegrationTest {

	@Autowired
	private TeamMemberRepository teamMemberRepository;

	@Test
	@DisplayName("checks active owner team membership against member and team status")
	void existsActiveOwnerTeamChecksActiveOwnerMembership() {
		Member owner = persist(member("owner"));
		Member normalMember = persist(member("normal"));
		Team activeTeam = persist(team("active"));
		Team inactiveTeam = persist(team("inactive", TeamStatus.INACTIVE, BASE_TIME.minusDays(1)));
		persist(teamMember(owner, activeTeam, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));
		persist(teamMember(normalMember, activeTeam, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
		persist(teamMember(owner, inactiveTeam, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));
		flushAndClear();

		assertThat(teamMemberRepository.existsActiveOwnerTeam(
				owner.getId(),
				TeamMemberRole.OWNER,
				TeamMemberStatus.ACTIVE,
				TeamStatus.ACTIVE
		)).isTrue();
		assertThat(teamMemberRepository.existsActiveOwnerTeam(
				normalMember.getId(),
				TeamMemberRole.OWNER,
				TeamMemberStatus.ACTIVE,
				TeamStatus.ACTIVE
		)).isFalse();
	}

	@Test
	@DisplayName("finds active team memberships and counts active members by team ids")
	void findsActiveMembershipsAndCountsActiveMembers() {
		Member member = persist(member("member"));
		Member otherMember = persist(member("other"));
		Team activeTeam = persist(team("active"));
		Team otherActiveTeam = persist(team("other-active"));
		Team inactiveTeam = persist(team("inactive", TeamStatus.INACTIVE, BASE_TIME.minusDays(1)));
		persist(teamMember(member, activeTeam, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));
		persist(teamMember(otherMember, activeTeam, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
		persist(teamMember(member, otherActiveTeam, TeamMemberRole.MEMBER, TeamMemberStatus.LEFT));
		persist(teamMember(member, inactiveTeam, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
		flushAndClear();

		List<TeamMember> memberships = teamMemberRepository.findActiveTeamMemberships(
				member.getId(),
				TeamMemberStatus.ACTIVE,
				TeamStatus.ACTIVE
		);
		List<Object[]> counts = teamMemberRepository.countActiveMembersByTeamIds(
				List.of(activeTeam.getId(), otherActiveTeam.getId(), inactiveTeam.getId()),
				TeamMemberStatus.ACTIVE
		);

		assertThat(memberships)
				.extracting(teamMember -> teamMember.getTeam().getId())
				.containsExactly(activeTeam.getId());
		assertThat(counts)
				.anySatisfy(row -> {
					assertThat(row[0]).isEqualTo(activeTeam.getId());
					assertThat(row[1]).isEqualTo(2L);
				})
				.anySatisfy(row -> {
					assertThat(row[0]).isEqualTo(inactiveTeam.getId());
					assertThat(row[1]).isEqualTo(1L);
				});
		assertThat(counts)
				.extracting(row -> row[0])
				.doesNotContain(otherActiveTeam.getId());
	}

	@Test
	@DisplayName("checks membership by team id, member id, member status, and team status")
	void existsByTeamIdAndMemberIdAndStatusAndTeamStatusChecksAllPredicates() {
		Member member = persist(member("member-status"));
		Team team = persist(team("team-status"));
		persist(teamMember(member, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
		flushAndClear();

		assertThat(teamMemberRepository.existsByTeamIdAndMemberIdAndStatusAndTeamStatus(
				team.getId(),
				member.getId(),
				TeamMemberStatus.ACTIVE,
				TeamStatus.ACTIVE
		)).isTrue();
		assertThat(teamMemberRepository.existsByTeamIdAndMemberIdAndStatusAndTeamStatus(
				team.getId(),
				member.getId(),
				TeamMemberStatus.LEFT,
				TeamStatus.ACTIVE
		)).isFalse();
	}

	@Test
	@DisplayName("counts new feed activities by inactive selected teams excluding my feeds and deleted feeds")
	void countNewActivitiesByMemberTeams() {
		Member member = persist(member("activity-member", kusitms.spin.tikitak.domain.member.enums.MemberStatus.ACTIVE, null));
		Member other = persist(member("activity-other"));
		Team selectedTeam = persist(team("selected"));
		Team targetTeam = persist(team("target"));
		Team emptyTeam = persist(team("empty"));
		TeamMember selectedMembership = persist(teamMember(member, selectedTeam, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
		TeamMember targetMembership = persist(teamMember(member, targetTeam, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
		persist(teamMember(member, emptyTeam, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
		TeamMember otherTargetMembership = persist(teamMember(other, targetTeam, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));

		persist(feed(targetTeam, otherTargetMembership, BASE_TIME.plusMinutes(1), null));
		persist(feed(targetTeam, otherTargetMembership, BASE_TIME.plusMinutes(2), null));
		persist(feed(targetTeam, targetMembership, BASE_TIME.plusMinutes(3), null));
		persist(feed(targetTeam, otherTargetMembership, BASE_TIME.minusMinutes(1), null));
		persist(feed(targetTeam, otherTargetMembership, BASE_TIME.plusMinutes(4), BASE_TIME.plusMinutes(5)));
		persist(feed(selectedTeam, selectedMembership, BASE_TIME.plusMinutes(1), null));
		flushAndClear();

		List<Object[]> counts = teamMemberRepository.countNewActivitiesByMemberTeams(
				member.getId(),
				selectedTeam.getId(),
				TeamMemberStatus.ACTIVE.name(),
				TeamStatus.ACTIVE.name()
		);

		assertThat(counts)
				.anySatisfy(row -> {
					assertThat(row[0]).isEqualTo(targetTeam.getId());
					assertThat(row[1]).isEqualTo(2L);
				})
				.anySatisfy(row -> {
					assertThat(row[0]).isEqualTo(emptyTeam.getId());
					assertThat(row[1]).isEqualTo(0L);
				});
		assertThat(counts)
				.extracting(row -> row[0])
				.doesNotContain(selectedTeam.getId());
	}

	private Feed feed(Team team, TeamMember author, java.time.LocalDateTime createdAt, java.time.LocalDateTime deletedAt) {
		return Feed.builder()
				.team(team)
				.teamMember(author)
				.content("feed")
				.createdAt(createdAt)
				.updatedAt(createdAt)
				.deletedAt(deletedAt)
				.build();
	}

}
