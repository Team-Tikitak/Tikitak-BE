package kusitms.spin.tikitak.repository.auth;

import kusitms.spin.tikitak.domain.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	List<RefreshToken> findAllByMemberIdAndRevokedFalse(Long memberId);
}
