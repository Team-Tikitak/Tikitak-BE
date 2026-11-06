package kusitms.spin.tikitak.domain.feed.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import kusitms.spin.tikitak.domain.common.BaseTimeEntity;
import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Entity
@Table(name = "feed_comment")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeedComment extends BaseTimeEntity {

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

	public void updateContent(String content) {
		this.content = content;
		touch();
	}

	public void movePosition(BigDecimal positionX, BigDecimal positionY) {
		this.positionX = positionX;
		this.positionY = positionY;
		touch();
	}

	void setFeed(Feed feed) {
		this.feed = feed;
	}
}
