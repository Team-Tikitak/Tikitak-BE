package kusitms.spin.tikitak.repository.feed;

import kusitms.spin.tikitak.domain.feed.entity.Feed;
import kusitms.spin.tikitak.domain.feed.entity.FeedComment;
import kusitms.spin.tikitak.domain.feed.entity.FeedImage;
import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.team.entity.Team;
import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberRole;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus;
import kusitms.spin.tikitak.support.IntegrationTest;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link FeedCommentRepositoryCustom#findActivePage}의 필터(피드 전체/이미지별), 커서, 삭제 제외, limit, fetch 경로 검증.
 */
class FeedCommentListQueryIntegrationTest extends IntegrationTest {

	private static final int LIMIT = 20;

	@Autowired
	private FeedCommentRepository feedCommentRepository;

	/**
	 * 공통 시나리오. persist 순서(= id 오름차순)와 createdAt 순서가 다르도록 섞는다.
	 * <pre>
	 * persist 순서  댓글     이미지        createdAt
	 * 1            c3       image1        t+3
	 * 2            c1       image2        t+1
	 * 3            c2       image1        t+2
	 * 4            c4       image2        t+4
	 * 5            deleted  image1, 삭제   t+0
	 * 6            other    다른 피드      t+0
	 * </pre>
	 * 피드 전체 기대 순서: c1, c2, c3, c4 / image1: c2, c3 / image2: c1, c4
	 */
	private Scenario scenario() {
		Member authorMember = persist(member("fc-author"));
		Team team = persist(team("fc"));
		TeamMember author = persist(teamMember(authorMember, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));

		Feed feed = persist(feed(team, author));
		FeedImage image1 = addImage(feed, 0);
		FeedImage image2 = addImage(feed, 1);
		Feed otherFeed = persist(feed(team, author));
		FeedImage otherImage = addImage(otherFeed, 0);

		FeedComment c3 = persistComment(feed, image1, author, false, BASE_TIME.plusHours(3));
		FeedComment c1 = persistComment(feed, image2, author, false, BASE_TIME.plusHours(1));
		FeedComment c2 = persistComment(feed, image1, author, false, BASE_TIME.plusHours(2));
		FeedComment c4 = persistComment(feed, image2, author, false, BASE_TIME.plusHours(4));
		FeedComment deleted = persistComment(feed, image1, author, true, BASE_TIME);
		FeedComment other = persistComment(otherFeed, otherImage, author, false, BASE_TIME);

		flushAndClear();
		return new Scenario(feed.getId(), image1.getId(), image2.getId(),
				c1.getId(), c2.getId(), c3.getId(), c4.getId(), deleted.getId(), other.getId(),
				c1.getCreatedAt(), c2.getCreatedAt());
	}

	@Nested
	@DisplayName("필터")
	class Filters {

		@Test
		@DisplayName("피드 전체: 같은 피드의 모든 이미지 댓글을 createdAt 오름차순으로 반환하고, 다른 피드·삭제 댓글은 제외한다")
		void wholeFeed() {
			Scenario s = scenario();

			FeedCommentListCondition condition = new FeedCommentListCondition(s.feedId(), null, null, null);

			assertThat(ids(feedCommentRepository.findActivePage(condition, LIMIT)))
					.containsExactly(s.c1(), s.c2(), s.c3(), s.c4())
					.doesNotContain(s.deleted(), s.other());
		}

		@Test
		@DisplayName("이미지별: 해당 이미지의 댓글만 반환한다")
		void byImage() {
			Scenario s = scenario();

			assertThat(ids(feedCommentRepository.findActivePage(
					new FeedCommentListCondition(s.feedId(), s.image1Id(), null, null), LIMIT)))
					.containsExactly(s.c2(), s.c3())
					.doesNotContain(s.deleted());
			assertThat(ids(feedCommentRepository.findActivePage(
					new FeedCommentListCondition(s.feedId(), s.image2Id(), null, null), LIMIT)))
					.containsExactly(s.c1(), s.c4());
		}

		@Test
		@DisplayName("삭제된 댓글은 가장 이른 createdAt이어도 제외한다")
		void excludesDeleted() {
			Scenario s = scenario();

			List<Long> result = ids(feedCommentRepository.findActivePage(
					new FeedCommentListCondition(s.feedId(), s.image1Id(), null, null), LIMIT));

			assertThat(result).doesNotContain(s.deleted());
			assertThat(result).hasSize(2);
		}

		@Test
		@DisplayName("limit만큼만 반환한다")
		void respectsLimit() {
			Scenario s = scenario();

			FeedCommentListCondition condition = new FeedCommentListCondition(s.feedId(), null, null, null);

			assertThat(ids(feedCommentRepository.findActivePage(condition, 3)))
					.containsExactly(s.c1(), s.c2(), s.c3());
		}
	}

	@Nested
	@DisplayName("커서")
	class Cursor {

		@Test
		@DisplayName("피드 전체: 커서 댓글보다 뒤(createdAt이 늦은) 댓글만 반환한다")
		void wholeFeedNextPage() {
			Scenario s = scenario();

			FeedCommentListCondition condition = new FeedCommentListCondition(
					s.feedId(), null, s.c2CreatedAt(), s.c2());

			assertThat(ids(feedCommentRepository.findActivePage(condition, LIMIT)))
					.containsExactly(s.c3(), s.c4());
		}

		@Test
		@DisplayName("이미지별: 커서 댓글보다 뒤의 같은 이미지 댓글만 반환한다")
		void byImageNextPage() {
			Scenario s = scenario();

			assertThat(ids(feedCommentRepository.findActivePage(
					new FeedCommentListCondition(s.feedId(), s.image1Id(), s.c2CreatedAt(), s.c2()), LIMIT)))
					.containsExactly(s.c3());
			assertThat(ids(feedCommentRepository.findActivePage(
					new FeedCommentListCondition(s.feedId(), s.image2Id(), s.c1CreatedAt(), s.c1()), LIMIT)))
					.containsExactly(s.c4());
		}

		@Test
		@DisplayName("createdAt이 같으면 id가 큰 댓글만 다음 페이지에 온다(오름차순)")
		void sameCreatedAtUsesIdTieBreaker() {
			Member authorMember = persist(member("fc-tie-author"));
			Team team = persist(team("fc-tie"));
			TeamMember author = persist(teamMember(authorMember, team, TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE));
			Feed feed = persist(feed(team, author));
			FeedImage image = addImage(feed, 0);

			LocalDateTime same = BASE_TIME;
			// persist 순서 = id 오름차순. createdAt은 명시적으로 덮어쓴다.
			// laterSmallId: id는 커서보다 작지만 createdAt이 더 늦어 포함돼야 하고, 정렬상 sameAfterCursor 뒤에 와야 한다.
			FeedComment laterSmallId = persistComment(feed, image, author, false, same.plusHours(1));
			// sameBeforeCursor: createdAt은 같고 id가 커서보다 작아 제외돼야 한다.
			FeedComment sameBeforeCursor = persistComment(feed, image, author, false, same);
			FeedComment cursorComment = persistComment(feed, image, author, false, same);
			// sameAfterCursor: createdAt은 같고 id가 커서보다 커서 포함돼야 한다.
			FeedComment sameAfterCursor = persistComment(feed, image, author, false, same);
			// earlierLargeId: id는 커서보다 크지만 createdAt이 더 이르므로 제외돼야 한다.
			FeedComment earlierLargeId = persistComment(feed, image, author, false, same.minusHours(1));
			flushAndClear();

			assertThat(sameBeforeCursor.getCreatedAt()).isEqualTo(same);
			assertThat(cursorComment.getCreatedAt()).isEqualTo(same);
			assertThat(sameAfterCursor.getCreatedAt()).isEqualTo(same);
			assertThat(sameBeforeCursor.getId()).isLessThan(cursorComment.getId());
			assertThat(sameAfterCursor.getId()).isGreaterThan(cursorComment.getId());
			assertThat(laterSmallId.getId()).isLessThan(sameAfterCursor.getId());

			List<Long> wholeFeed = ids(feedCommentRepository.findActivePage(
					new FeedCommentListCondition(feed.getId(), null, same, cursorComment.getId()), LIMIT));
			assertThat(wholeFeed)
					.containsExactly(sameAfterCursor.getId(), laterSmallId.getId())
					.doesNotContain(sameBeforeCursor.getId(), cursorComment.getId(), earlierLargeId.getId());

			List<Long> byImage = ids(feedCommentRepository.findActivePage(
					new FeedCommentListCondition(feed.getId(), image.getId(), same, cursorComment.getId()), LIMIT));
			assertThat(byImage).containsExactly(sameAfterCursor.getId(), laterSmallId.getId());
		}
	}

	@Test
	@DisplayName("teamMember, feedImage를 함께 가져오고 teamMember.member는 가져오지 않는다")
	void fetchesTeamMemberAndFeedImage() {
		Scenario s = scenario();

		List<FeedComment> result = feedCommentRepository.findActivePage(
				new FeedCommentListCondition(s.feedId(), null, null, null), LIMIT);

		assertThat(ids(result)).containsExactly(s.c1(), s.c2(), s.c3(), s.c4());
		for (FeedComment comment : result) {
			assertThat(Hibernate.isInitialized(comment.getTeamMember())).isTrue();
			assertThat(Hibernate.isInitialized(comment.getFeedImage())).isTrue();
			assertThat(Hibernate.isInitialized(comment.getTeamMember().getMember())).isFalse();
		}
	}

	@Nested
	@DisplayName("조건 객체 생성자 검증")
	class ConditionValidation {

		@Test
		@DisplayName("feedId가 null이면 NullPointerException")
		void feedIdRequired() {
			assertThatNullPointerException()
					.isThrownBy(() -> new FeedCommentListCondition(null, null, null, null));
		}

		@Test
		@DisplayName("커서 createdAt만 있으면 IllegalArgumentException")
		void cursorCreatedAtOnly() {
			assertThatThrownBy(() -> new FeedCommentListCondition(1L, null, BASE_TIME, null))
					.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		@DisplayName("커서 commentId만 있으면 IllegalArgumentException")
		void cursorCommentIdOnly() {
			assertThatThrownBy(() -> new FeedCommentListCondition(1L, 2L, null, 10L))
					.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		@DisplayName("커서 두 필드가 모두 null이면 hasCursor false, 모두 있으면 true")
		void hasCursor() {
			assertThat(new FeedCommentListCondition(1L, null, null, null).hasCursor()).isFalse();
			assertThat(new FeedCommentListCondition(1L, null, BASE_TIME, 10L).hasCursor()).isTrue();
		}
	}

	// --- helpers ---

	private record Scenario(
			Long feedId, Long image1Id, Long image2Id,
			Long c1, Long c2, Long c3, Long c4, Long deleted, Long other,
			LocalDateTime c1CreatedAt, LocalDateTime c2CreatedAt
	) {
	}

	private static List<Long> ids(List<FeedComment> comments) {
		return comments.stream().map(FeedComment::getId).toList();
	}

	private Feed feed(Team team, TeamMember author) {
		return Feed.builder()
				.team(team).teamMember(author)
				.content("테스트 피드")
				.createdAt(BASE_TIME).updatedAt(BASE_TIME)
				.build();
	}

	private FeedImage addImage(Feed feed, int orderIndex) {
		FeedImage image = FeedImage.builder()
				.imgUrl("https://example.com/feed-image-" + orderIndex + ".png")
				.orderIndex(orderIndex)
				.createdAt(BASE_TIME)
				.build();
		feed.addImage(image);
		return persist(image);
	}

	private FeedComment persistComment(
			Feed feed, FeedImage feedImage, TeamMember author, boolean deleted, LocalDateTime createdAt) {
		FeedComment comment = FeedComment.builder()
				.feed(feed).feedImage(feedImage).teamMember(author)
				.content("댓글")
				.positionX(new BigDecimal("0.500000")).positionY(new BigDecimal("0.500000"))
				.deleted(deleted)
				.createdAt(createdAt).updatedAt(createdAt)
				.build();
		return persistWithCreatedAt(comment, createdAt);
	}
}
