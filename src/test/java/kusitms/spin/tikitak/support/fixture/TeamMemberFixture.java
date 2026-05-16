package kusitms.spin.tikitak.support.fixture;

import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.team.entity.Team;
import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberRole;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus;

import java.time.LocalDateTime;

public final class TeamMemberFixture {

	private TeamMemberFixture() {
	}

	public static TeamMember activeOwner(Long id, Member member, Team team) {
		return teamMember(id, member, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE);
	}

	public static TeamMember activeMember(Long id, Member member, Team team) {
		return teamMember(id, member, team, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE);
	}

	public static TeamMember teamMember(
			Long id,
			Member member,
			Team team,
			TeamMemberRole role,
			TeamMemberStatus status
	) {
		return TeamMember.builder()
				.id(id)
				.team(team)
				.member(member)
				.nickname("nickname-" + id)
				.profileImgUrl("https://example.com/team-member" + id + ".png")
				.role(role)
				.status(status)
				.createdAt(LocalDateTime.of(2026, 3, 4, 20, 30).plusDays(id))
				.build();
	}
}
