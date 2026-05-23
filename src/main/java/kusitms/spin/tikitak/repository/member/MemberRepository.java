package kusitms.spin.tikitak.repository.member;

import jakarta.persistence.LockModeType;
import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.member.enums.MemberStatus;
import kusitms.spin.tikitak.domain.member.enums.SocialProvider;
import org.springframework.data.domain.Pageable;
import kusitms.spin.tikitak.domain.team.enums.TeamStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

	Optional<Member> findBySocialProviderAndProviderIdAndStatus(
			SocialProvider socialProvider,
			String providerId,
			MemberStatus status
	);

	Optional<Member> findByIdAndStatus(Long memberId, MemberStatus status);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select m from Member m where m.id = :memberId")
	Optional<Member> findByIdForUpdate(@Param("memberId") Long memberId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select m from Member m where m.id = :memberId")
	Optional<Member> findProfileCleanupTargetForUpdate(@Param("memberId") Long memberId);

	@Query("""
			select distinct m.id
			from Member m
			left join TeamMember tm on tm.member = m
			where m.status = :status
				and m.deletedAt <= :cutoff
				and (m.profileImgUrl is not null or tm.profileImgUrl is not null)
			order by m.id asc
			""")
	List<Long> findWithdrawnProfileCleanupTargetIds(
			@Param("status") MemberStatus status,
			@Param("cutoff") LocalDateTime cutoff,
			Pageable pageable
	);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update Member m set m.activeTeamId = null where m.activeTeamId = :teamId")
	void clearActiveTeamIdByTeamId(@Param("teamId") Long teamId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update Member m
			set m.activeTeamId = :teamId
			where m.id = :memberId
				and m.activeTeamId is null
	""")
	int setActiveTeamIdIfNull(@Param("memberId") Long memberId, @Param("teamId") Long teamId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update Member m
			set m.activeTeamId = null
			where m.activeTeamId in (
				select t.id
				from Team t
				where t.status = :status
					and t.deletedAt < :cutoff
			)
			""")
	void clearActiveTeamIdsByInactiveTeamPredicate(
			@Param("status") TeamStatus status,
			@Param("cutoff") LocalDateTime cutoff
	);
}
