package kusitms.spin.tikitak.repository.team;

import kusitms.spin.tikitak.domain.team.enums.TeamMemberRole;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus;
import kusitms.spin.tikitak.domain.team.enums.TeamStatus;
import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

	@Query("""
			select count(tm) > 0
			from TeamMember tm
			join tm.team t
			where tm.member.id = :memberId
				and tm.role = :role
				and tm.status = :memberStatus
				and t.status = :teamStatus
			""")
	boolean existsActiveOwnerTeam(
			@Param("memberId") Long memberId,
			@Param("role") TeamMemberRole role,
			@Param("memberStatus") TeamMemberStatus memberStatus,
			@Param("teamStatus") TeamStatus teamStatus
	);

	@Query("""
			select count(tm) > 0
			from TeamMember tm
			join tm.team t
			where t.id = :teamId
				and tm.member.id = :memberId
				and tm.status = :memberStatus
				and t.status = :teamStatus
			""")
	boolean existsByTeamIdAndMemberIdAndStatusAndTeamStatus(
			@Param("teamId") Long teamId,
			@Param("memberId") Long memberId,
			@Param("memberStatus") TeamMemberStatus memberStatus,
			@Param("teamStatus") TeamStatus teamStatus
	);

	Optional<TeamMember> findByMemberIdAndTeamId(Long memberId, Long teamId);

	@Query("""
			select tm
			from TeamMember tm
			join fetch tm.team t
			where tm.member.id = :memberId
				and tm.status = :memberStatus
				and t.status = :teamStatus
			""")
	List<TeamMember> findActiveTeamMemberships(
			@Param("memberId") Long memberId,
			@Param("memberStatus") TeamMemberStatus memberStatus,
			@Param("teamStatus") TeamStatus teamStatus
	);

	@Query("""
			select tm.team.id, count(tm)
			from TeamMember tm
			where tm.team.id in :teamIds
				and tm.status = :memberStatus
			group by tm.team.id
			""")
	List<Object[]> countActiveMembersByTeamIds(
			@Param("teamIds") Collection<Long> teamIds,
			@Param("memberStatus") TeamMemberStatus memberStatus
	);
}
