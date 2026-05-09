package kusitms.spin.tikitak.domain.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import kusitms.spin.tikitak.domain.member.entity.Member;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_token")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@Column(nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(nullable = false)
	private LocalDateTime expiresAt;

	@Column(nullable = false)
	private boolean revoked;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	private LocalDateTime revokedAt;

	public static RefreshToken issue(Member member, String tokenHash, LocalDateTime expiresAt) {
		return RefreshToken.builder()
				.member(member)
				.tokenHash(tokenHash)
				.expiresAt(expiresAt)
				.revoked(false)
				.createdAt(LocalDateTime.now())
				.build();
	}

	public boolean isUsable(LocalDateTime now) {
		return !revoked && expiresAt.isAfter(now);
	}

	public void revoke() {
		if (!this.revoked) {
			this.revoked = true;
			this.revokedAt = LocalDateTime.now();
		}
	}
}
