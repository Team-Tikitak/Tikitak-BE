package kusitms.spin.tikitak.repository.team;

import kusitms.spin.tikitak.domain.team.entity.Team;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {

    @EntityGraph(attributePaths = {"teamMembers", "teamMembers.member"})
    Optional<Team> findTeamWithTeamMembersById(Long teamId);
}
