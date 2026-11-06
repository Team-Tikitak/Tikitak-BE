package kusitms.spin.tikitak.domain.feed.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kusitms.spin.tikitak.domain.common.BaseCreatedTimeEntity;
import kusitms.spin.tikitak.domain.feed.enums.FeedReactionType;
import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
		name = "feed_reaction",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_feed_reaction_feed_member",
				columnNames = { "feed_id", "team_member_id" }
		)
)
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeedReaction extends BaseCreatedTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "feed_id", nullable = false)
	private Feed feed;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "team_member_id", nullable = false)
	private TeamMember teamMember;

	@Enumerated(EnumType.STRING)
	@Column(name = "reaction_type", nullable = false, length = 50)
	private FeedReactionType reactionType;

	void setFeed(Feed feed) {
		this.feed = feed;
	}

	public void updateReactionType(FeedReactionType reactionType) {
		this.reactionType = reactionType;
	}
}
