package kusitms.spin.tikitak.repository.team;

import kusitms.spin.tikitak.domain.team.entity.Team;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberRole;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus;
import kusitms.spin.tikitak.domain.team.enums.TeamStatus;
import kusitms.spin.tikitak.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class TeamRepositoryIntegrationTest extends IntegrationTest {

	@Autowired
	private TeamRepository teamRepository;

	@Test
	@DisplayName("deletes only inactive teams older than cutoff")
	void bulkDeleteInactiveTeamsDeletesOnlyMatchingTeams() {
		Team oldInactiveTeam = persist(team("old-inactive", TeamStatus.INACTIVE, BASE_TIME.minusDays(10)));
		Team recentInactiveTeam = persist(team("recent-inactive", TeamStatus.INACTIVE, BASE_TIME.minusDays(1)));
		Team activeTeam = persist(team("active", TeamStatus.ACTIVE, null));
		flushAndClear();

		teamRepository.bulkDeleteInactiveTeams(TeamStatus.INACTIVE, BASE_TIME.minusDays(3));
		flushAndClear();

		assertThat(teamRepository.findById(oldInactiveTeam.getId())).isEmpty();
		assertThat(teamRepository.findById(recentInactiveTeam.getId())).isPresent();
		assertThat(teamRepository.findById(activeTeam.getId())).isPresent();
	}

	@Test
	@DisplayName("loads team members with member entity graph")
	void findTeamWithTeamMembersByIdLoadsMembers() {
		Team team = persist(team("team-members"));
		var owner = persist(member("owner"));
		var participant = persist(member("participant"));
		persist(teamMember(owner, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));
		persist(teamMember(participant, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE));
		flushAndClear();

		Team foundTeam = teamRepository.findTeamWithTeamMembersById(team.getId()).orElseThrow();

		var persistenceUnitUtil = entityManager().getEntityManagerFactory().getPersistenceUnitUtil();
		assertThat(foundTeam.getTeamMembers()).hasSize(2);
		assertThat(persistenceUnitUtil.isLoaded(foundTeam, "teamMembers")).isTrue();
		assertThat(foundTeam.getTeamMembers())
				.allSatisfy(teamMember -> assertThat(persistenceUnitUtil.isLoaded(teamMember, "member")).isTrue());
		assertThat(foundTeam.getTeamMembers())
				.extracting(teamMember -> teamMember.getMember().getId())
				.containsExactlyInAnyOrder(owner.getId(), participant.getId());
	}

}
