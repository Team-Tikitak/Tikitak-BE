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

	@Test
	@DisplayName("deleteExpired는 만료된 행만 revoke 여부와 상관없이 지우고, 만료 전 행은 revoke됐어도 남긴다")
	void deleteExpiredRemovesOnlyExpiredRows() {
		Member member = persist(member("refresh-owner"));
		LocalDateTime now = BASE_TIME;
		String expiredActive = "1".repeat(64);
		String expiredRevoked = "2".repeat(64);
		String expiresAtNow = "3".repeat(64);
		String validRevoked = "4".repeat(64);
		String validActive = "5".repeat(64);
		persist(RefreshToken.issue(member, expiredActive, now.minusSeconds(1)));
		persist(revoked(RefreshToken.issue(member, expiredRevoked, now.minusDays(1))));
		persist(RefreshToken.issue(member, expiresAtNow, now));
		persist(revoked(RefreshToken.issue(member, validRevoked, now.plusDays(1))));
		persist(RefreshToken.issue(member, validActive, now.plusDays(7)));
		flushAndClear();

		int deleted = refreshTokenRepository.deleteExpired(now);
		flushAndClear();

		assertThat(deleted).isEqualTo(2);
		assertThat(refreshTokenRepository.findAll())
				.extracting(RefreshToken::getTokenHash)
				.containsExactlyInAnyOrder(expiresAtNow, validRevoked, validActive);
		assertThat(refreshTokenRepository.findByTokenHash(validRevoked).orElseThrow().isRevoked()).isTrue();
	}

	@Test
	@DisplayName("deleteExpired는 만료된 행이 없으면 0을 반환한다")
	void deleteExpiredReturnsZeroWhenNothingExpired() {
		Member member = persist(member("refresh-owner"));
		persist(RefreshToken.issue(member, TOKEN_HASH, BASE_TIME.plusDays(7)));
		flushAndClear();

		assertThat(refreshTokenRepository.deleteExpired(BASE_TIME)).isZero();
		flushAndClear();
		assertThat(refreshTokenRepository.findByTokenHash(TOKEN_HASH)).isPresent();
	}

	private static RefreshToken revoked(RefreshToken token) {
		token.revoke();
		return token;
	}
}
