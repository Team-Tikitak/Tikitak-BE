package kusitms.spin.tikitak.domain.team.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberRole;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "team_member")
@Getter
@Builder
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamMember {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "team_id", nullable = false)
	private Team team;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@Column(length = 30)
	private String nickname;

	@Column(columnDefinition = "text")
	private String profileImgUrl;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50)
	private TeamMemberRole role;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50)
	private TeamMemberStatus status;

	@CreatedDate
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	private LocalDateTime deletedAt;

	void setTeam(Team team) {
		this.team = team;
	}

	public void updateProfile(String nickname, String profileImgUrl) {
		if (nickname != null) this.nickname = nickname;
		if (profileImgUrl != null) this.profileImgUrl = profileImgUrl;
	}

	public void leaveTeam() {
		this.status = TeamMemberStatus.LEFT;
		this.deletedAt = LocalDateTime.now();
	}

	public void kickTeamMember() {
		this.status = TeamMemberStatus.BANNED;
		this.deletedAt = LocalDateTime.now();
	}

	public void rejoin() {
		this.status = TeamMemberStatus.ACTIVE;
		this.deletedAt = null;
	}
}
