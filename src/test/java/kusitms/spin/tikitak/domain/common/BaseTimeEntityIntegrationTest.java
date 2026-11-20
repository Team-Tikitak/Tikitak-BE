package kusitms.spin.tikitak.domain.common;

import kusitms.spin.tikitak.domain.team.entity.Team;
import kusitms.spin.tikitak.domain.team.entity.TeamInvite;
import kusitms.spin.tikitak.domain.team.enums.TeamStatus;
import kusitms.spin.tikitak.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class BaseTimeEntityIntegrationTest extends IntegrationTest {

	@Test
	@DisplayName("BaseTimeEntity: persist 시 createdAt/updatedAt이 같은 값으로 채워지고, 수정 시 updatedAt만 갱신된다")
	void baseTimeEntityAuditing() {
		Team team = persist(Team.builder()
				.name("Team audit")
				.description("audit")
				.status(TeamStatus.ACTIVE)
				.build());
		flushAndClear();

		Team created = entityManager().find(Team.class, team.getId());
		LocalDateTime createdAt = created.getCreatedAt();
		LocalDateTime updatedAtOnCreate = created.getUpdatedAt();
		assertThat(createdAt).isNotNull();
		assertThat(updatedAtOnCreate).isNotNull();
		assertThat(updatedAtOnCreate).isEqualTo(createdAt);

		// 시계 정밀도에 기대지 않도록, 저장된 updated_at을 생성 시각보다 확실히 이전 값으로 되돌린 뒤 수정한다.
		LocalDateTime backdatedUpdatedAt = createdAt.minusDays(1);
		entityManager().createNativeQuery("UPDATE team SET updated_at = :updatedAt WHERE id = :id")
				.setParameter("updatedAt", backdatedUpdatedAt)
				.setParameter("id", team.getId())
				.executeUpdate();
		flushAndClear();

		Team loaded = entityManager().find(Team.class, team.getId());
		assertThat(loaded.getUpdatedAt()).isEqualTo(backdatedUpdatedAt);
		loaded.update("Team audit renamed", null);
		flushAndClear();

		Team updated = entityManager().find(Team.class, team.getId());
		assertThat(updated.getName()).isEqualTo("Team audit renamed");
		assertThat(updated.getUpdatedAt()).isAfter(backdatedUpdatedAt);
		assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(createdAt);
		assertThat(updated.getCreatedAt()).isEqualTo(createdAt);
	}

	@Test
	@DisplayName("BaseCreatedTimeEntity: 시각 없이 persist해도 createdAt이 채워진다")
	void baseCreatedTimeEntityAuditing() {
		Team team = persist(team("invite-audit"));
		TeamInvite invite = persist(TeamInvite.builder()
				.team(team)
				.inviteToken("invite-audit-token")
				.expiresAt(LocalDateTime.now().plusDays(1))
				.active(true)
				.build());
		flushAndClear();

		TeamInvite loaded = entityManager().find(TeamInvite.class, invite.getId());
		assertThat(loaded.getCreatedAt()).isNotNull();
	}
}
