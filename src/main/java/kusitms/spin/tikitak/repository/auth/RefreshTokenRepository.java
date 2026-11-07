package kusitms.spin.tikitak.repository.auth;

import kusitms.spin.tikitak.domain.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	List<RefreshToken> findAllByMemberIdAndRevokedFalse(Long memberId);

	/**
	 * 아직 revoke되지 않은 토큰만 revoke한다. 같은 토큰으로 동시에 들어온 요청 중 한 건만 1을 받는다.
	 * 실행 후 영속성 컨텍스트가 비워지므로, 이전에 조회한 엔티티를 다시 쓰지 않는다.
	 *
	 * @return revoke한 행 수 (0 또는 1)
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update RefreshToken r
			set r.revoked = true, r.revokedAt = :now
			where r.tokenHash = :tokenHash and r.revoked = false
			""")
	int revokeIfActive(@Param("tokenHash") String tokenHash, @Param("now") LocalDateTime now);

	/**
	 * 만료된 토큰을 revoke 여부와 상관없이 삭제한다.
	 * revoke됐지만 아직 만료 전인 토큰은 재사용 탐지에 쓰이므로 남긴다.
	 *
	 * @return 삭제한 행 수
	 */
	@Modifying
	@Query("DELETE FROM RefreshToken r WHERE r.expiresAt < :now")
	int deleteExpired(@Param("now") LocalDateTime now);
}
