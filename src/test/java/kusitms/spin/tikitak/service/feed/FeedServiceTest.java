package kusitms.spin.tikitak.service.feed;

import kusitms.spin.tikitak.domain.feed.entity.Feed;
import kusitms.spin.tikitak.domain.media.entity.Media;
import kusitms.spin.tikitak.domain.media.enums.MediaStatus;
import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.team.entity.Team;
import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus;
import kusitms.spin.tikitak.domain.team.enums.TeamStatus;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.repository.feed.FeedCommentRepository;
import kusitms.spin.tikitak.repository.feed.FeedReactionRepository;
import kusitms.spin.tikitak.repository.feed.FeedRepository;
import kusitms.spin.tikitak.repository.media.MediaRepository;
import kusitms.spin.tikitak.repository.place.PlaceRepository;
import kusitms.spin.tikitak.repository.team.TeamMemberRepository;
import kusitms.spin.tikitak.repository.team.TeamRepository;
import kusitms.spin.tikitak.service.feed.dto.FeedRequestDTO;
import kusitms.spin.tikitak.service.feed.dto.FeedResponseDTO;
import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static kusitms.spin.tikitak.support.fixture.FeedRequestFixture.createRequestWithContent;
import static kusitms.spin.tikitak.support.fixture.FeedRequestFixture.createRequestWithMediaCount;
import static kusitms.spin.tikitak.support.fixture.FeedRequestFixture.validCreateRequest;
import static kusitms.spin.tikitak.support.fixture.MediaFixture.uploadedFeedImage;
import static kusitms.spin.tikitak.support.fixture.MemberFixture.activeMember;
import static kusitms.spin.tikitak.support.fixture.TeamFixture.activeTeam;
import static kusitms.spin.tikitak.support.fixture.TeamMemberFixture.activeMember;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeedServiceTest extends UnitTest {

	private static final Long MEMBER_ID = 1L;
	private static final Long TEAM_ID = 10L;
	private static final Long TEAM_MEMBER_ID = 100L;
	private static final Long MEDIA_ID = 1000L;
	private static final UUID MEDIA_PUBLIC_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

	@Mock
	private FeedRepository feedRepository;

	@Mock
	private FeedReactionRepository feedReactionRepository;

	@Mock
	private FeedCommentRepository feedCommentRepository;

	@Mock
	private PlaceRepository placeRepository;

	@Mock
	private MediaRepository mediaRepository;

	@Mock
	private TeamRepository teamRepository;

	@Mock
	private TeamMemberRepository teamMemberRepository;

	private FeedService feedService;
	private Team team;
	private TeamMember author;

	@BeforeEach
	void setUp() {
		feedService = new FeedService(
				feedRepository,
				feedReactionRepository,
				feedCommentRepository,
				placeRepository,
				mediaRepository,
				teamRepository,
				teamMemberRepository
		);
		team = activeTeam(TEAM_ID);
		Member member = activeMember(MEMBER_ID);
		author = activeMember(TEAM_MEMBER_ID, member, team);
	}

	@Test
	@DisplayName("업로드 완료된 피드 이미지로 일반 피드를 작성한다")
	void createFeed() {
		Media media = uploadedFeedImage(MEDIA_ID, MEMBER_ID, MEDIA_PUBLIC_ID);
		FeedRequestDTO.FeedCreateRequestDTO request = validCreateRequest(MEDIA_PUBLIC_ID);
		stubActiveAuthor();
		when(mediaRepository.findByPublicIdsForUpdate(List.of(MEDIA_PUBLIC_ID))).thenReturn(List.of(media));
		when(feedRepository.save(any(Feed.class))).thenAnswer(invocation -> invocation.getArgument(0));

		FeedResponseDTO.FeedMutationResponseDTO response = feedService.createFeed(MEMBER_ID, TEAM_ID, request);

		assertThat(response.getType().name()).isEqualTo("GENERAL");
		assertThat(response.getContent()).isEqualTo("오늘 카페 좋았다");
		assertThat(response.getImageCount()).isEqualTo(1);
		assertThat(response.getThumbnailImageUrl()).isEqualTo(media.getUrl());
		assertThat(media.getStatus()).isEqualTo(MediaStatus.USED);
		verify(feedRepository).save(any(Feed.class));
	}

	@Test
	@DisplayName("피드 이미지는 최대 10장까지 등록할 수 있다")
	void createFeedThrowsWhenMediaCountExceeded() {
		FeedRequestDTO.FeedCreateRequestDTO request = createRequestWithMediaCount(11);
		stubActiveAuthor();

		assertThatThrownBy(() -> feedService.createFeed(MEMBER_ID, TEAM_ID, request))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FEED006));
	}

	@Test
	@DisplayName("공백만 있는 본문은 등록할 수 없다")
	void createFeedThrowsWhenContentIsBlank() {
		FeedRequestDTO.FeedCreateRequestDTO request = createRequestWithContent(MEDIA_PUBLIC_ID, "   ");
		stubActiveAuthor();

		assertThatThrownBy(() -> feedService.createFeed(MEMBER_ID, TEAM_ID, request))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FEED007));
	}

	@Test
	@DisplayName("당월 피드가 3개 미만이면 빈 목록을 반환한다")
	void getEveryonePickItemsReturnsEmptyWhenFeedCountLessThan3() {
		YearMonth now = YearMonth.now();
		LocalDate start = now.atDay(1);
		LocalDate end = now.plusMonths(1).atDay(1);

		stubActiveAuthor();
		when(feedRepository.countActiveByTeamAndMonth(eq(TEAM_ID), eq(start), eq(end))).thenReturn(2L);

		assertThat(feedService.getEveryonePickItems(MEMBER_ID, TEAM_ID)).isEmpty();
	}

	@Test
	@DisplayName("피드가 3개 이상이면 랭킹 순서대로 FeedListItemDTO 목록을 반환한다")
	void getEveryonePickItemsReturnsOrderedFeedListItemDTOs() {
		YearMonth now = YearMonth.now();
		LocalDate start = now.atDay(1);
		LocalDate end = now.plusMonths(1).atDay(1);

		Long feedId1 = 201L;
		Long feedId2 = 202L;
		List<Long> rankedIds = List.of(feedId1, feedId2);

		Feed feed1 = Feed.builder().id(feedId1).team(team).teamMember(author).content("1번 피드").build();
		Feed feed2 = Feed.builder().id(feedId2).team(team).teamMember(author).content("2번 피드").build();

		stubActiveAuthor();
		when(feedRepository.countActiveByTeamAndMonth(eq(TEAM_ID), eq(start), eq(end))).thenReturn(5L);
		when(feedRepository.findEveryonePickFeedIds(eq(TEAM_ID), eq(start), eq(end))).thenReturn(rankedIds);
		when(feedRepository.findActiveByIds(eq(TEAM_ID), eq(rankedIds))).thenReturn(List.of(feed1, feed2));
		when(feedCommentRepository.countByFeedIds(rankedIds)).thenReturn(List.of());
		when(feedReactionRepository.countByReactionTypeByFeedIds(rankedIds)).thenReturn(List.of());
		when(feedReactionRepository.findMyReactions(eq(rankedIds), eq(TEAM_MEMBER_ID))).thenReturn(List.of());

		List<FeedResponseDTO.FeedListItemDTO> result = feedService.getEveryonePickItems(MEMBER_ID, TEAM_ID);

		assertThat(result).hasSize(2);
		assertThat(result.get(0).getFeedId()).isEqualTo(feedId1);
		assertThat(result.get(0).getContent()).isEqualTo("1번 피드");
		assertThat(result.get(1).getFeedId()).isEqualTo(feedId2);
	}

	@Test
	@DisplayName("피드 본문은 1000자를 초과할 수 없다")
	void createFeedThrowsWhenContentTooLong() {
		FeedRequestDTO.FeedCreateRequestDTO request = createRequestWithContent(MEDIA_PUBLIC_ID, "a".repeat(1001));
		stubActiveAuthor();

		assertThatThrownBy(() -> feedService.createFeed(MEMBER_ID, TEAM_ID, request))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FEED007));
	}

	@Test
	@DisplayName("태그 필터 ID가 모두 포함된 피드 목록을 조회한다")
	void listFeedsWithTaggedTeamMemberFilter() {
		List<Long> taggedTeamMemberIds = List.of(101L, 102L);
		stubActiveAuthor();
		when(teamMemberRepository.findActiveByTeamIdAndIds(
				eq(TEAM_ID),
				eq(taggedTeamMemberIds),
				eq(TeamMemberStatus.ACTIVE),
				eq(TeamStatus.ACTIVE)
		)).thenReturn(List.of(
				activeMember(101L, activeMember(2L), team),
				activeMember(102L, activeMember(3L), team)
		));
		when(feedRepository.findActiveFirstPageByTaggedTeamMemberIds(
				eq(TEAM_ID),
				eq(null),
				eq(null),
				eq(null),
				eq(taggedTeamMemberIds),
				eq(2L),
				any(Pageable.class)
		)).thenReturn(List.of());

		FeedResponseDTO.FeedListResponseDTO response = feedService.listFeeds(
				MEMBER_ID, TEAM_ID, null, null, null, null, null, taggedTeamMemberIds);

		assertThat(response.getItems()).isEmpty();
		verify(feedRepository).findActiveFirstPageByTaggedTeamMemberIds(
				eq(TEAM_ID),
				eq(null),
				eq(null),
				eq(null),
				eq(taggedTeamMemberIds),
				eq(2L),
				any(Pageable.class)
		);
	}

	@Test
	@DisplayName("태그 필터와 region 필터를 함께 적용한다")
	void listFeedsWithTaggedTeamMemberAndRegionFilter() {
		List<Long> taggedTeamMemberIds = List.of(101L);
		stubActiveAuthor();
		when(teamMemberRepository.findActiveByTeamIdAndIds(
				eq(TEAM_ID),
				eq(taggedTeamMemberIds),
				eq(TeamMemberStatus.ACTIVE),
				eq(TeamStatus.ACTIVE)
		)).thenReturn(List.of(activeMember(101L, activeMember(2L), team)));
		when(feedRepository.findActiveFirstPageByTaggedTeamMemberIds(
				eq(TEAM_ID),
				eq(null),
				eq("서울 강남구"),
				eq(null),
				eq(taggedTeamMemberIds),
				eq(1L),
				any(Pageable.class)
		)).thenReturn(List.of());

		feedService.listFeeds(MEMBER_ID, TEAM_ID, null, null, null, "서울 강남구", null, taggedTeamMemberIds);

		verify(feedRepository).findActiveFirstPageByTaggedTeamMemberIds(
				eq(TEAM_ID),
				eq(null),
				eq("서울 강남구"),
				eq(null),
				eq(taggedTeamMemberIds),
				eq(1L),
				any(Pageable.class)
		);
	}

	@Test
	@DisplayName("placeId와 region이 함께 있으면 태그 필터에서도 placeId를 우선 적용한다")
	void listFeedsWithTaggedTeamMemberFilterPrefersPlaceIdOverRegion() {
		List<Long> taggedTeamMemberIds = List.of(101L);
		stubActiveAuthor();
		when(teamMemberRepository.findActiveByTeamIdAndIds(
				eq(TEAM_ID),
				eq(taggedTeamMemberIds),
				eq(TeamMemberStatus.ACTIVE),
				eq(TeamStatus.ACTIVE)
		)).thenReturn(List.of(activeMember(101L, activeMember(2L), team)));
		when(feedRepository.findActiveFirstPageByTaggedTeamMemberIds(
				eq(TEAM_ID),
				eq("kakao_12345"),
				eq(null),
				eq(null),
				eq(taggedTeamMemberIds),
				eq(1L),
				any(Pageable.class)
		)).thenReturn(List.of());

		feedService.listFeeds(MEMBER_ID, TEAM_ID, null, null, "kakao_12345", "서울 강남구", null, taggedTeamMemberIds);

		verify(feedRepository).findActiveFirstPageByTaggedTeamMemberIds(
				eq(TEAM_ID),
				eq("kakao_12345"),
				eq(null),
				eq(null),
				eq(taggedTeamMemberIds),
				eq(1L),
				any(Pageable.class)
		);
	}

	@Test
	@DisplayName("태그 필터 ID에 해당 팀 활성 멤버가 아니면 예외가 발생한다")
	void listFeedsThrowsWhenTaggedTeamMemberIsInvalid() {
		List<Long> taggedTeamMemberIds = List.of(101L, 102L);
		stubActiveAuthor();
		when(teamMemberRepository.findActiveByTeamIdAndIds(
				eq(TEAM_ID),
				eq(taggedTeamMemberIds),
				eq(TeamMemberStatus.ACTIVE),
				eq(TeamStatus.ACTIVE)
		)).thenReturn(List.of(activeMember(101L, activeMember(2L), team)));

		assertThatThrownBy(() -> feedService.listFeeds(
				MEMBER_ID, TEAM_ID, null, null, null, null, null, taggedTeamMemberIds))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FEED008));
	}

	@Test
	@DisplayName("태그 필터 ID는 중복 제거 후 최대 11개까지 허용한다")
	void listFeedsThrowsWhenTaggedTeamMemberFilterCountExceeded() {
		List<Long> taggedTeamMemberIds = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L);
		stubActiveAuthor();

		assertThatThrownBy(() -> feedService.listFeeds(
				MEMBER_ID, TEAM_ID, null, null, null, null, null, taggedTeamMemberIds))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FEED009));
	}

	private void stubActiveAuthor() {
		when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team));
		when(teamMemberRepository.findActiveByMemberIdAndTeamId(
				eq(MEMBER_ID),
				eq(TEAM_ID),
				eq(TeamMemberStatus.ACTIVE),
				eq(TeamStatus.ACTIVE)
		)).thenReturn(Optional.of(author));
	}
}
