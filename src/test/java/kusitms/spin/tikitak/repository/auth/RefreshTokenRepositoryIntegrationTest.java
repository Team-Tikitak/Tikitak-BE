package kusitms.spin.tikitak.repository.auth;

import kusitms.spin.tikitak.domain.auth.entity.RefreshToken;
import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenRepositoryIntegrationTest extends IntegrationTest {

	private static final String TOKEN_HASH = "a".repeat(64);
	private static final String OTHER_TOKEN_HASH = "b".repeat(64);

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Test
	@DisplayName("revokeIfActive는 첫 호출에서 1, 두 번째 호출에서 0을 반환하고 첫 호출 시각을 revoked_at에 기록한다")
	void revokeIfActiveRevokesOnlyOnce() {
		Member member = persist(member("refresh-owner"));
		persist(RefreshToken.issue(member, TOKEN_HASH, BASE_TIME.plusDays(7)));
		persist(RefreshToken.issue(member, OTHER_TOKEN_HASH, BASE_TIME.plusDays(7)));
		flushAndClear();
		LocalDateTime firstRevokedAt = BASE_TIME.plusHours(1);

		int first = refreshTokenRepository.revokeIfActive(TOKEN_HASH, firstRevokedAt);
		int second = refreshTokenRepository.revokeIfActive(TOKEN_HASH, BASE_TIME.plusHours(2));

		assertThat(first).isEqualTo(1);
		assertThat(second).isZero();
		RefreshToken revoked = refreshTokenRepository.findByTokenHash(TOKEN_HASH).orElseThrow();
		assertThat(revoked.isRevoked()).isTrue();
		assertThat(revoked.getRevokedAt()).isEqualTo(firstRevokedAt);
		RefreshToken other = refreshTokenRepository.findByTokenHash(OTHER_TOKEN_HASH).orElseThrow();
		assertThat(other.isRevoked()).isFalse();
		assertThat(other.getRevokedAt()).isNull();
	}

	@Test
	@DisplayName("revokeIfActive는 없는 토큰 해시면 0을 반환한다")
	void revokeIfActiveReturnsZeroForUnknownHash() {
		assertThat(refreshTokenRepository.revokeIfActive("c".repeat(64), BASE_TIME)).isZero();
	}
}
