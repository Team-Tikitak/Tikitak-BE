package kusitms.spin.tikitak.domain.feed.entity;

import jakarta.persistence.*;
import kusitms.spin.tikitak.domain.feed.enums.FeedType;
import kusitms.spin.tikitak.domain.place.entity.Place;
import kusitms.spin.tikitak.domain.question.entity.Question;
import kusitms.spin.tikitak.domain.team.entity.Team;
import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "feed")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class Feed {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "team_member_id", nullable = false)
	private TeamMember teamMember;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "team_id", nullable = false)
	private Team team;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "place_id")
	private Place place;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "question_id")
	private Question question;

	@Column(columnDefinition = "text")
	private String content;

	private LocalDate meetingDate;

	private LocalDate questionAnswerDate;

	@Column(length = 100, unique = true)
	private String dailyAnswerActiveKey;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	private LocalDateTime deletedAt;

	@Builder.Default
	@OneToMany(mappedBy = "feed", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<FeedComment> comments = new ArrayList<>();

	@Builder.Default
	@OneToMany(mappedBy = "feed", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<FeedImage> images = new ArrayList<>();

	@Builder.Default
	@OneToMany(mappedBy = "feed", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<FeedTag> tags = new ArrayList<>();

	@Builder.Default
	@OneToMany(mappedBy = "feed", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<FeedReaction> reactions = new ArrayList<>();

	public FeedType getType() {
		return FeedType.from(this);
	}

	public boolean hasQuestion() {
		return question != null;
	}

	public void addImage(FeedImage image) {
		images.add(image);
		image.setFeed(this);
	}

	public void addTag(FeedTag tag) {
		tags.add(tag);
		tag.setFeed(this);
	}

	public void updateGeneralFeed(String content, Place place) {
		this.content = content;
		this.place = place;
		this.updatedAt = LocalDateTime.now();
	}

	public void updateDailyAnswerContent(String content) {
		this.content = content;
		this.updatedAt = LocalDateTime.now();
	}

	public void setDailyAnswer(Question question, LocalDate questionAnswerDate, String dailyAnswerActiveKey) {
		this.question = question;
		this.questionAnswerDate = questionAnswerDate;
		this.dailyAnswerActiveKey = dailyAnswerActiveKey;
	}

	public void replaceImages(List<FeedImage> images) {
		this.images.clear();
		images.forEach(this::addImage);
		this.updatedAt = LocalDateTime.now();
	}

	public void replaceTags(List<FeedTag> tags) {
		this.tags.clear();
		tags.forEach(this::addTag);
		this.updatedAt = LocalDateTime.now();
	}

	public void syncTags(List<TeamMember> teamMembers) {
		Set<Long> nextTeamMemberIds = teamMembers.stream()
				.map(TeamMember::getId)
				.collect(Collectors.toSet());
		this.tags.removeIf(tag -> !nextTeamMemberIds.contains(tag.getTeamMember().getId()));

		Set<Long> currentTeamMemberIds = this.tags.stream()
				.map(tag -> tag.getTeamMember().getId())
				.collect(Collectors.toSet());
		teamMembers.stream()
				.filter(teamMember -> !currentTeamMemberIds.contains(teamMember.getId()))
				.map(teamMember -> FeedTag.builder().teamMember(teamMember).build())
				.forEach(this::addTag);
		this.updatedAt = LocalDateTime.now();
	}

	public void delete() {
		LocalDateTime now = LocalDateTime.now();
		this.deletedAt = now;
		this.dailyAnswerActiveKey = null;
		this.updatedAt = now;
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
