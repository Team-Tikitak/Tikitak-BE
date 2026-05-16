package kusitms.spin.tikitak.repository.member;

import jakarta.persistence.LockModeType;
import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.member.enums.SocialProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

	Optional<Member> findBySocialProviderAndProviderId(SocialProvider socialProvider, String providerId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select m from Member m where m.id = :memberId")
	Optional<Member> findByIdForUpdate(@Param("memberId") Long memberId);

	@Modifying
	@Query("update Member m set m.activeTeamId = null where m.activeTeamId = :teamId")
	void clearActiveTeamIdByTeamId(@Param("teamId") Long teamId);

	@Modifying
	@Query("update Member m set m.activeTeamId = null where m.activeTeamId in :teamIds")
	void clearActiveTeamIdByTeamIds(@Param("teamIds") Collection<Long> teamIds);
}
