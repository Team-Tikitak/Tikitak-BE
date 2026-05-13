package kusitms.spin.tikitak.repository.team;

import kusitms.spin.tikitak.domain.team.entity.TeamInvite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TeamInviteRepository extends JpaRepository<TeamInvite, Long> {

	Optional<TeamInvite> findByTeamId(Long teamId);
}
