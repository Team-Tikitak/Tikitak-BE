package kusitms.spin.tikitak.repository.team;

import kusitms.spin.tikitak.domain.team.entity.Team;
import kusitms.spin.tikitak.domain.team.enums.TeamStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {

    @EntityGraph(attributePaths = {"teamMembers", "teamMembers.member"})
    Optional<Team> findTeamWithTeamMembersById(Long teamId);

    @Modifying
    @Query("DELETE FROM Team t WHERE t.status = :status AND t.deletedAt < :cutoff")
    void bulkDeleteInactiveTeams(@Param("status") TeamStatus status, @Param("deadline") LocalDateTime cutoff);
}
