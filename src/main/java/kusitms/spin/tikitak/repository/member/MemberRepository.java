package kusitms.spin.tikitak.repository.member;

import jakarta.persistence.LockModeType;
import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.member.enums.SocialProvider;
import kusitms.spin.tikitak.domain.team.enums.TeamStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

	Optional<Member> findBySocialProviderAndProviderId(SocialProvider socialProvider, String providerId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select m from Member m where m.id = :memberId")
	Optional<Member> findByIdForUpdate(@Param("memberId") Long memberId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update Member m set m.activeTeamId = null where m.activeTeamId = :teamId")
	void clearActiveTeamIdByTeamId(@Param("teamId") Long teamId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update Member m set m.activeTeamId = null where m.activeTeamId in :teamIds")
	void clearActiveTeamIdByTeamIds(@Param("teamIds") Collection<Long> teamIds);

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
