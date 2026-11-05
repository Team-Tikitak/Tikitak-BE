package kusitms.spin.tikitak.domain.team.entity;

import jakarta.persistence.*;
import kusitms.spin.tikitak.domain.common.BaseCreatedTimeEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Entity
@Table(name = "team_invite")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamInvite extends BaseCreatedTimeEntity {

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

	public boolean isExpired() {
		return expiresAt.isBefore(LocalDateTime.now());
	}

	public void update(String newToken, LocalDateTime newExpiresAt) {
		this.inviteToken = newToken;
		this.expiresAt = newExpiresAt;
		this.active = true;
	}
}
