package kusitms.spin.tikitak.domain.feed;

import jakarta.persistence.*;
import kusitms.spin.tikitak.domain.place.Place;
import kusitms.spin.tikitak.domain.question.Question;
import kusitms.spin.tikitak.domain.team.Team;
import kusitms.spin.tikitak.domain.team.TeamMember;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "place_id")
	private Place place;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "question_id")
	private Question question;

	@Column(columnDefinition = "text")
	private String content;

	private LocalDate meetingDate;

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
}
