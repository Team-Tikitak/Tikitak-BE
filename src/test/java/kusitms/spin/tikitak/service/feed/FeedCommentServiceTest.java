package kusitms.spin.tikitak.service.feed;

import kusitms.spin.tikitak.domain.feed.entity.Feed;
import kusitms.spin.tikitak.domain.feed.entity.FeedComment;
import kusitms.spin.tikitak.domain.feed.entity.FeedImage;
import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.notification.event.FeedCommentCreatedEvent;
import kusitms.spin.tikitak.domain.team.entity.Team;
import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import kusitms.spin.tikitak.domain.member.enums.ProfileCharacterType;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus;
import kusitms.spin.tikitak.domain.team.enums.TeamStatus;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.service.me.DefaultProfileImageResolver;
import kusitms.spin.tikitak.repository.feed.FeedCommentRepository;
import kusitms.spin.tikitak.repository.feed.FeedImageRepository;
import kusitms.spin.tikitak.repository.feed.FeedRepository;
import kusitms.spin.tikitak.repository.team.TeamMemberRepository;
import kusitms.spin.tikitak.repository.team.TeamRepository;
import kusitms.spin.tikitak.service.feed.dto.FeedCommentRequestDTO;
import kusitms.spin.tikitak.service.feed.dto.FeedCommentResponseDTO;
import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static kusitms.spin.tikitak.support.fixture.MemberFixture.activeMemberWithCharacterType;
import static kusitms.spin.tikitak.support.fixture.TeamFixture.activeTeam;
import static kusitms.spin.tikitak.support.fixture.TeamMemberFixture.activeMemberWithoutProfileImg;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FeedCommentServiceTest extends UnitTest {

	private static final Long MEMBER_ID = 1L;
	private static final Long OTHER_MEMBER_ID = 2L;
	private static final Long TEAM_ID = 10L;
	private static final Long FEED_ID = 25L;
	private static final Long FEED_IMAGE_ID = 33L;
	private static final Long TEAM_MEMBER_ID = 101L;
	private static final Long OTHER_TEAM_MEMBER_ID = 102L;

	@Mock
	private FeedRepository feedRepository;

	@Mock
	private FeedImageRepository feedImageRepository;

	@Mock
	private FeedCommentRepository feedCommentRepository;

	@Mock
	private TeamRepository teamRepository;

	@Mock
	private TeamMemberRepository teamMemberRepository;

	@Mock
	private DefaultProfileImageResolver defaultProfileImageResolver;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	private FeedCommentService feedCommentService;
	private Team team;
	private TeamMember viewer;
	private TeamMember otherTeamMember;
	private Feed feed;
	private FeedImage feedImage;

	@BeforeEach
	void setUp() {
		feedCommentService = new FeedCommentService(
				feedRepository,
				feedImageRepository,
				feedCommentRepository,
				teamRepository,
				teamMemberRepository,
				defaultProfileImageResolver,
				eventPublisher
		);
		team = activeTeam(TEAM_ID);
		Member member = kusitms.spin.tikitak.support.fixture.MemberFixture.activeMember(MEMBER_ID);
		Member otherMember = kusitms.spin.tikitak.support.fixture.MemberFixture.activeMember(OTHER_MEMBER_ID);
		viewer = kusitms.spin.tikitak.support.fixture.TeamMemberFixture.activeMember(TEAM_MEMBER_ID, member, team);
		otherTeamMember = kusitms.spin.tikitak.support.fixture.TeamMemberFixture.activeMember(
				OTHER_TEAM_MEMBER_ID, otherMember, team);
		feed = feed(FEED_ID, viewer);
		feedImage = feedImage(FEED_IMAGE_ID, feed);
	}

	@Test
	@DisplayName("이미지 앵커 댓글을 작성한다")
	void createComment() {
		stubActiveViewer();
		when(feedRepository.findActiveDetail(TEAM_ID, FEED_ID)).thenReturn(Optional.of(feed));
		when(feedImageRepository.findByIdAndFeedId(FEED_IMAGE_ID, FEED_ID)).thenReturn(Optional.of(feedImage));
		when(feedCommentRepository.save(any(FeedComment.class))).thenAnswer(invocation -> invocation.getArgument(0));

		FeedCommentResponseDTO.CommentItemDTO response = feedCommentService.createComment(
				MEMBER_ID,
				TEAM_ID,
				FEED_ID,
				new FeedCommentRequestDTO.CommentCreateRequestDTO(
						FEED_IMAGE_ID,
						"  좋다!  ",
						new BigDecimal("0.420000"),
						new BigDecimal("0.680000")
				)
		);

		ArgumentCaptor<FeedComment> captor = ArgumentCaptor.forClass(FeedComment.class);
		verify(feedCommentRepository).save(captor.capture());
		assertThat(captor.getValue().getContent()).isEqualTo("좋다!");
		assertThat(captor.getValue().getFeedImage()).isEqualTo(feedImage);
		assertThat(response.isMine()).isTrue();
		assertThat(response.getContent()).isEqualTo("좋다!");
		verifyNoInteractions(eventPublisher);
	}

	@Test
	@DisplayName("다른 사람의 피드에 댓글을 작성하면 피드 작성자에게 알림 이벤트가 발행된다")
	void createCommentPublishesNotificationEventToFeedOwner() {
		Feed otherFeed = feed(FEED_ID, otherTeamMember);
		FeedImage otherFeedImage = feedImage(FEED_IMAGE_ID, otherFeed);
		stubActiveViewer();
		when(feedRepository.findActiveDetail(TEAM_ID, FEED_ID)).thenReturn(Optional.of(otherFeed));
		when(feedImageRepository.findByIdAndFeedId(FEED_IMAGE_ID, FEED_ID)).thenReturn(Optional.of(otherFeedImage));
		when(feedCommentRepository.save(any(FeedComment.class))).thenAnswer(invocation -> invocation.getArgument(0));

		feedCommentService.createComment(
				MEMBER_ID,
				TEAM_ID,
				FEED_ID,
				new FeedCommentRequestDTO.CommentCreateRequestDTO(
						FEED_IMAGE_ID,
						"좋다!",
						new BigDecimal("0.420000"),
						new BigDecimal("0.680000")
				)
		);

		ArgumentCaptor<FeedCommentCreatedEvent> captor = ArgumentCaptor.forClass(FeedCommentCreatedEvent.class);
		verify(eventPublisher).publishEvent(captor.capture());
		FeedCommentCreatedEvent event = captor.getValue();
		assertThat(event.recipientMemberId()).isEqualTo(OTHER_MEMBER_ID);
		assertThat(event.actorNickname()).isEqualTo(viewer.getNickname());
		assertThat(event.feedId()).isEqualTo(FEED_ID);
		assertThat(event.teamId()).isEqualTo(TEAM_ID);
	}

	@Test
	@DisplayName("댓글 작성 시 피드 이미지가 피드에 속하지 않으면 예외가 발생한다")
	void createCommentThrowsWhenFeedImageNotFound() {
		stubActiveViewer();
		when(feedRepository.findActiveDetail(TEAM_ID, FEED_ID)).thenReturn(Optional.of(feed));
		when(feedImageRepository.findByIdAndFeedId(FEED_IMAGE_ID, FEED_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> feedCommentService.createComment(
				MEMBER_ID,
				TEAM_ID,
				FEED_ID,
				validCreateRequest()
		)).isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMENT011));
	}

	@Test
	@DisplayName("댓글 작성 시 좌표 소수 자릿수가 6자리를 넘으면 예외가 발생한다")
	void createCommentThrowsWhenPositionScaleOverLimit() {
		stubActiveViewer();
		when(feedRepository.findActiveDetail(TEAM_ID, FEED_ID)).thenReturn(Optional.of(feed));
		when(feedImageRepository.findByIdAndFeedId(FEED_IMAGE_ID, FEED_ID)).thenReturn(Optional.of(feedImage));

		assertThatThrownBy(() -> feedCommentService.createComment(
				MEMBER_ID,
				TEAM_ID,
				FEED_ID,
				new FeedCommentRequestDTO.CommentCreateRequestDTO(
						FEED_IMAGE_ID,
						"좋다!",
						new BigDecimal("0.1234567"),
						new BigDecimal("0.680000")
				)
		)).isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMENT010));
	}

	@Test
	@DisplayName("댓글 목록은 생성일시 오름차순 커서 페이지로 조회한다")
	void listComments() {
		stubActiveViewer();
		when(feedRepository.findActiveDetail(TEAM_ID, FEED_ID)).thenReturn(Optional.of(feed));
		FeedComment first = comment(10L, viewer, LocalDateTime.of(2026, 3, 4, 20, 30));
		FeedComment second = comment(11L, otherTeamMember, LocalDateTime.of(2026, 3, 4, 20, 31));
		FeedComment extra = comment(12L, otherTeamMember, LocalDateTime.of(2026, 3, 4, 20, 32));
		when(feedCommentRepository.findActiveFirstPageByFeedId(eq(FEED_ID), any(Pageable.class)))
				.thenReturn(List.of(first, second, extra));

		FeedCommentResponseDTO.CommentListResponseDTO response =
				feedCommentService.listComments(MEMBER_ID, TEAM_ID, FEED_ID, null, null, 2);

		assertThat(response.getItems()).hasSize(2);
		assertThat(response.getItems().get(0).getCommentId()).isEqualTo(10L);
		assertThat(response.getItems().get(0).isMine()).isTrue();
		assertThat(response.getItems().get(1).isMine()).isFalse();
		assertThat(response.getPageInfo().isHasNext()).isTrue();
		assertThat(response.getPageInfo().getNextCursor()).isEqualTo("2026-03-04T20:31_11");
	}

	@Test
	@DisplayName("feedImageId가 있으면 특정 이미지 댓글만 조회한다")
	void listCommentsByFeedImage() {
		stubActiveViewer();
		when(feedRepository.findActiveDetail(TEAM_ID, FEED_ID)).thenReturn(Optional.of(feed));
		when(feedImageRepository.findByIdAndFeedId(FEED_IMAGE_ID, FEED_ID)).thenReturn(Optional.of(feedImage));
		when(feedCommentRepository.findActiveFirstPageByFeedImageId(eq(FEED_ID), eq(FEED_IMAGE_ID), any(Pageable.class)))
				.thenReturn(List.of(comment(10L, viewer, LocalDateTime.of(2026, 3, 4, 20, 30))));

		FeedCommentResponseDTO.CommentListResponseDTO response =
				feedCommentService.listComments(MEMBER_ID, TEAM_ID, FEED_ID, FEED_IMAGE_ID, null, 20);

		assertThat(response.getItems()).hasSize(1);
		verify(feedImageRepository).findByIdAndFeedId(FEED_IMAGE_ID, FEED_ID);
		verify(feedCommentRepository).findActiveFirstPageByFeedImageId(eq(FEED_ID), eq(FEED_IMAGE_ID), any(Pageable.class));
	}

	@Test
	@DisplayName("본인 댓글의 내용과 위치를 부분 수정한다")
	void updateComment() {
		stubActiveViewer();
		when(feedRepository.findActiveDetail(TEAM_ID, FEED_ID)).thenReturn(Optional.of(feed));
		FeedComment comment = comment(10L, viewer, LocalDateTime.of(2026, 3, 4, 20, 30));
		when(feedCommentRepository.findActiveByIdAndFeedId(10L, FEED_ID)).thenReturn(Optional.of(comment));

		FeedCommentResponseDTO.CommentItemDTO response = feedCommentService.updateComment(
				MEMBER_ID,
				TEAM_ID,
				FEED_ID,
				10L,
				new FeedCommentRequestDTO.CommentUpdateRequestDTO(
						"  수정됨  ",
						new BigDecimal("0.500000"),
						new BigDecimal("0.700000")
				)
		);

		assertThat(response.getContent()).isEqualTo("수정됨");
		assertThat(response.getPositionX()).isEqualByComparingTo("0.500000");
		assertThat(response.getPositionY()).isEqualByComparingTo("0.700000");
	}

	@Test
	@DisplayName("위치 수정 시 x, y 중 하나만 전달되면 예외가 발생한다")
	void updateCommentThrowsWhenOnlyOnePositionProvided() {
		stubActiveViewer();
		when(feedRepository.findActiveDetail(TEAM_ID, FEED_ID)).thenReturn(Optional.of(feed));
		when(feedCommentRepository.findActiveByIdAndFeedId(10L, FEED_ID))
				.thenReturn(Optional.of(comment(10L, viewer, LocalDateTime.of(2026, 3, 4, 20, 30))));

		assertThatThrownBy(() -> feedCommentService.updateComment(
				MEMBER_ID,
				TEAM_ID,
				FEED_ID,
				10L,
				new FeedCommentRequestDTO.CommentUpdateRequestDTO(null, new BigDecimal("0.500000"), null)
		)).isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMENT010));
	}

	@Test
	@DisplayName("다른 사람이 작성한 댓글은 수정할 수 없다")
	void updateCommentThrowsWhenNotAuthor() {
		stubActiveViewer();
		when(feedRepository.findActiveDetail(TEAM_ID, FEED_ID)).thenReturn(Optional.of(feed));
		when(feedCommentRepository.findActiveByIdAndFeedId(10L, FEED_ID))
				.thenReturn(Optional.of(comment(10L, otherTeamMember, LocalDateTime.of(2026, 3, 4, 20, 30))));

		assertThatThrownBy(() -> feedCommentService.updateComment(
				MEMBER_ID,
				TEAM_ID,
				FEED_ID,
				10L,
				new FeedCommentRequestDTO.CommentUpdateRequestDTO("수정", null, null)
		)).isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMENT008));
	}

	@Test
	@DisplayName("본인 댓글을 hard delete 한다")
	void deleteComment() {
		stubActiveViewer();
		when(feedRepository.findActiveDetail(TEAM_ID, FEED_ID)).thenReturn(Optional.of(feed));
		FeedComment comment = comment(10L, viewer, LocalDateTime.of(2026, 3, 4, 20, 30));
		when(feedCommentRepository.findActiveByIdAndFeedId(10L, FEED_ID)).thenReturn(Optional.of(comment));

		feedCommentService.deleteComment(MEMBER_ID, TEAM_ID, FEED_ID, 10L);

		verify(feedCommentRepository).delete(comment);
	}

	@Test
	@DisplayName("댓글 목록 조회 시 작성자의 profileImgUrl이 없으면 캐릭터 기본 이미지로 fallback된다")
	void listCommentsUsesDefaultProfileImageWhenProfileImgUrlIsNull() {
		Member memberWithCharacter = activeMemberWithCharacterType(OTHER_MEMBER_ID, ProfileCharacterType.TAK_SPARK);
		TeamMember authorWithoutImg = activeMemberWithoutProfileImg(OTHER_TEAM_MEMBER_ID, memberWithCharacter, team);
		String defaultImgUrl = "https://cdn.example.com/default-profiles/tak-spark.png";

		stubActiveViewer();
		when(feedRepository.findActiveDetail(TEAM_ID, FEED_ID)).thenReturn(Optional.of(feed));
		FeedComment commentByAuthorWithoutImg = comment(10L, authorWithoutImg, LocalDateTime.of(2026, 3, 4, 20, 30));
		when(feedCommentRepository.findActiveFirstPageByFeedId(eq(FEED_ID), any(Pageable.class)))
				.thenReturn(List.of(commentByAuthorWithoutImg));
		when(defaultProfileImageResolver.resolveForTeamMember(authorWithoutImg)).thenReturn(defaultImgUrl);

		FeedCommentResponseDTO.CommentListResponseDTO response =
				feedCommentService.listComments(MEMBER_ID, TEAM_ID, FEED_ID, null, null, 10);

		assertThat(response.getItems()).hasSize(1);
		assertThat(response.getItems().get(0).getAuthor().getProfileImageUrl()).isEqualTo(defaultImgUrl);
	}

	private void stubActiveViewer() {
		when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team));
		when(teamMemberRepository.findActiveByMemberIdAndTeamId(
				eq(MEMBER_ID),
				eq(TEAM_ID),
				eq(TeamMemberStatus.ACTIVE),
				eq(TeamStatus.ACTIVE)
		)).thenReturn(Optional.of(viewer));
	}

	private FeedCommentRequestDTO.CommentCreateRequestDTO validCreateRequest() {
		return new FeedCommentRequestDTO.CommentCreateRequestDTO(
				FEED_IMAGE_ID,
				"좋다!",
				new BigDecimal("0.420000"),
				new BigDecimal("0.680000")
		);
	}

	private Feed feed(Long id, TeamMember author) {
		return Feed.builder()
				.id(id)
				.team(team)
				.teamMember(author)
				.content("피드")
				.createdAt(LocalDateTime.of(2026, 3, 4, 20, 0))
				.updatedAt(LocalDateTime.of(2026, 3, 4, 20, 0))
				.build();
	}

	private FeedImage feedImage(Long id, Feed feed) {
		return FeedImage.builder()
				.id(id)
				.feed(feed)
				.imgUrl("https://example.com/feed.png")
				.orderIndex(0)
				.createdAt(LocalDateTime.of(2026, 3, 4, 20, 0))
				.build();
	}

	private FeedComment comment(Long id, TeamMember author, LocalDateTime createdAt) {
		return FeedComment.builder()
				.id(id)
				.feed(feed)
				.feedImage(feedImage)
				.teamMember(author)
				.content("댓글 " + id)
				.positionX(new BigDecimal("0.420000"))
				.positionY(new BigDecimal("0.680000"))
				.deleted(false)
				.createdAt(createdAt)
				.updatedAt(createdAt)
				.build();
	}
}
