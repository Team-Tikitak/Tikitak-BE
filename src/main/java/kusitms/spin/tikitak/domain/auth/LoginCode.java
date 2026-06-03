package kusitms.spin.tikitak.domain.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "login_code")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class LoginCode {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 64)
	private String code;

	@Column(nullable = false)
	private Long memberId;

	@Column(name = "is_new_member", nullable = false)
	private boolean newMember;

	@Column(name = "has_agreed_required_terms", nullable = false)
	private boolean agreedRequiredTerms;

	@Column
	private Long activeTeamId;

	@Column(nullable = false)
	private LocalDateTime expiresAt;

	@Column(nullable = false)
	private boolean used;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	public static LoginCode issue(String code, Long memberId, boolean newMember, boolean agreedRequiredTerms, Long activeTeamId) {
		LocalDateTime now = LocalDateTime.now();
		return LoginCode.builder()
				.code(code)
				.memberId(memberId)
				.newMember(newMember)
				.agreedRequiredTerms(agreedRequiredTerms)
				.activeTeamId(activeTeamId)
				.expiresAt(now.plusMinutes(5))
				.used(false)
				.createdAt(now)
				.build();
	}

	public boolean isExpired() {
		return LocalDateTime.now().isAfter(expiresAt);
	}

	public void markUsed() {
		this.used = true;
	}
}
