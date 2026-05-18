package kusitms.spin.tikitak.domain.feed.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "feed_comment")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class FeedComment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "feed_id", nullable = false)
	private Feed feed;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "feed_image_id", nullable = false)
	private FeedImage feedImage;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "team_member_id", nullable = false)
	private TeamMember teamMember;

	@Column(columnDefinition = "text")
	private String content;

	@Column(name = "position_x", nullable = false, precision = 8, scale = 6)
	private BigDecimal positionX;

	@Column(name = "position_y", nullable = false, precision = 8, scale = 6)
	private BigDecimal positionY;

	@Column(name = "is_deleted", nullable = false)
	private boolean deleted;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	public void updateContent(String content) {
		this.content = content;
		this.updatedAt = LocalDateTime.now();
	}

	public void movePosition(BigDecimal positionX, BigDecimal positionY) {
		this.positionX = positionX;
		this.positionY = positionY;
		this.updatedAt = LocalDateTime.now();
	}

	void setFeed(Feed feed) {
		this.feed = feed;
	}

	@PrePersist
	protected void onCreate() {
		LocalDateTime now = LocalDateTime.now();
		if (createdAt == null) {
			createdAt = now;
		}
		if (updatedAt == null) {
			updatedAt = now;
		}
	}

	@PreUpdate
	protected void onUpdate() {
		updatedAt = LocalDateTime.now();
	}
}
