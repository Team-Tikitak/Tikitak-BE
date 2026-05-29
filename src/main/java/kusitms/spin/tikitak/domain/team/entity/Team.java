package kusitms.spin.tikitak.domain.team.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import kusitms.spin.tikitak.domain.team.enums.TeamStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "team")
@Getter
@Builder
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class Team {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(length = 50)
	private String name;

	@Column(columnDefinition = "text")
	private String description;

	@Column(columnDefinition = "text")
	private String teamImgUrl;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50)
	private TeamStatus status;

	@CreatedDate
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@LastModifiedDate
	@Column(nullable = false)
	private LocalDateTime updatedAt;

	private LocalDateTime deletedAt;

	@Builder.Default
	@OneToMany(mappedBy = "team", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<TeamMember> teamMembers = new ArrayList<>();

	public void update(String name, String description) {
		if (name != null) this.name = name;
		if (description != null) this.description = description;
	}

	public void inactive() {
		this.status = TeamStatus.INACTIVE;
		this.deletedAt = LocalDateTime.now();
	}

	public void recover() {
		this.status = TeamStatus.ACTIVE;
		this.deletedAt = null;
	}
}
