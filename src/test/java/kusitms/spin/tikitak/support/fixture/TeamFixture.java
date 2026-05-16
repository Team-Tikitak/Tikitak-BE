package kusitms.spin.tikitak.support.fixture;

import kusitms.spin.tikitak.domain.team.entity.Team;
import kusitms.spin.tikitak.domain.team.enums.TeamStatus;

import java.time.LocalDateTime;

public final class TeamFixture {

	private TeamFixture() {
	}

	public static Team activeTeam(Long id) {
		return team(id, TeamStatus.ACTIVE);
	}

	public static Team inactiveTeam(Long id) {
		return team(id, TeamStatus.INACTIVE);
	}

	public static Team team(Long id, TeamStatus status) {
		LocalDateTime now = LocalDateTime.of(2026, 3, 4, 20, 30);
		return Team.builder()
				.id(id)
				.name("Team " + id)
				.description("Team description " + id)
				.teamImgUrl("https://example.com/team" + id + ".png")
				.status(status)
				.createdAt(now)
				.updatedAt(now)
				.build();
	}
}
