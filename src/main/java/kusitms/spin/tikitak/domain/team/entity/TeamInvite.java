package kusitms.spin.tikitak.domain.team.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "team_invite")
@Getter
@Builder
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamInvite {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "team_id", nullable = false)
	private Team team;

	@Column(columnDefinition = "text")
	private String inviteToken;

	@Column(nullable = false)
	private LocalDateTime expiresAt;

	@Column(name = "is_active", nullable = false)
	private boolean active;

	@CreatedDate
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	public boolean isExpired() {
		return expiresAt.isBefore(LocalDateTime.now());
	}

	public void update(String newToken, LocalDateTime newExpiresAt) {
		this.inviteToken = newToken;
		this.expiresAt = newExpiresAt;
		this.active = true;
	}
}
