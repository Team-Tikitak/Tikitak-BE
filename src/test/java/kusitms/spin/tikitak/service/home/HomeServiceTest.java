package kusitms.spin.tikitak.service.home;

import kusitms.spin.tikitak.domain.feed.enums.FeedType;
import kusitms.spin.tikitak.domain.team.entity.Team;
import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus;
import kusitms.spin.tikitak.domain.team.enums.TeamStatus;
import kusitms.spin.tikitak.global.config.R2Properties;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.repository.feed.FeedRepository;
import kusitms.spin.tikitak.repository.feed.FeedTagRepository;
import kusitms.spin.tikitak.repository.team.TeamMemberRepository;
import kusitms.spin.tikitak.service.home.dto.RegionRow;
import kusitms.spin.tikitak.service.feed.FeedService;
import kusitms.spin.tikitak.service.feed.FeedService.CombinationItemsResult;
import kusitms.spin.tikitak.service.feed.dto.FeedResponseDTO;
import kusitms.spin.tikitak.service.home.dto.HomeResponseDTO;
import kusitms.spin.tikitak.service.me.DefaultProfileImageResolver;
import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static kusitms.spin.tikitak.support.fixture.MemberFixture.activeMember;
import static kusitms.spin.tikitak.support.fixture.TeamFixture.activeTeam;
import static kusitms.spin.tikitak.support.fixture.TeamMemberFixture.activeMember;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HomeServiceTest extends UnitTest {

	private static final Long MEMBER_ID = 1L;
	private static final Long TEAM_ID = 10L;

	@Mock
	private FeedTagRepository feedTagRepository;

	@Mock
	private TeamMemberRepository teamMemberRepository;

	@Mock
	private FeedService feedService;

	@Mock
	private FeedRepository feedRepository;

	@Mock
	private DefaultProfileImageResolver defaultProfileImageResolver;

	private R2Properties r2Properties;
	private HomeService homeService;
	private Team team;
	private TeamMember requester;

	@BeforeEach
	void setUp() {
		r2Properties = new R2Properties();
		r2Properties.setPublicBaseUrl("https://media.tikitak.space");
		homeService = new HomeService(
				feedTagRepository,
				teamMemberRepository,
				feedService,
				feedRepository,
				defaultProfileImageResolver,
				r2Properties
		);
		team = activeTeam(TEAM_ID);
		requester = activeMember(100L, activeMember(MEMBER_ID), team);
	}

	@Test
	@DisplayName("태그 수 상위 3명을 rank 1부터 순서대로 반환한다")
	void returnsTop3WithRank() {
		TeamMember tm1 = activeMember(101L, activeMember(2L), team);
		TeamMember tm2 = activeMember(102L, activeMember(3L), team);
		TeamMember tm3 = activeMember(103L, activeMember(4L), team);
		TeamMember tm4 = activeMember(104L, activeMember(5L), team);

		stubRequester();
		stubTagRows(List.of(
				new Object[]{tm1, 5L},
				new Object[]{tm2, 3L},
				new Object[]{tm3, 3L},
				new Object[]{tm4, 1L}
		));

		HomeResponseDTO.BestAttendanceResponse result = homeService.getBestAttendance(MEMBER_ID, TEAM_ID);

		assertThat(result.getMembers()).hasSize(3);
		assertThat(result.getMembers().get(0).getRank()).isEqualTo(1);
		assertThat(result.getMembers().get(0).getTagCount()).isEqualTo(5L);
		assertThat(result.getMembers().get(0).getTeamMemberId()).isEqualTo(101L);
		assertThat(result.getMembers().get(1).getRank()).isEqualTo(2);
		assertThat(result.getMembers().get(1).getTagCount()).isEqualTo(3L);
		assertThat(result.getMembers().get(2).getRank()).isEqualTo(3);
	}

	@Test
	@DisplayName("태그된 팀원이 3명 미만이면 있는 만큼만 반환한다")
	void returnsFewerThan3WhenNotEnoughData() {
		TeamMember tm1 = activeMember(101L, activeMember(2L), team);
		TeamMember tm2 = activeMember(102L, activeMember(3L), team);

		stubRequester();
		stubTagRows(List.of(
				new Object[]{tm1, 2L},
				new Object[]{tm2, 1L}
		));

		HomeResponseDTO.BestAttendanceResponse result = homeService.getBestAttendance(MEMBER_ID, TEAM_ID);

		assertThat(result.getMembers()).hasSize(2);
	}

	@Test
	@DisplayName("당월 태그가 없으면 빈 목록을 반환한다")
	void returnsEmptyListWhenNoTagsThisMonth() {
		stubRequester();
		stubTagRows(List.of());

		HomeResponseDTO.BestAttendanceResponse result = homeService.getBestAttendance(MEMBER_ID, TEAM_ID);

		assertThat(result.getMembers()).isEmpty();
	}

	@Test
	@DisplayName("팀 멤버가 아니면 TEAM008을 던진다")
	void throwsTeam008WhenNotTeamMember() {
		when(teamMemberRepository.findActiveByMemberIdAndTeamId(
				eq(MEMBER_ID), eq(TEAM_ID),
				eq(TeamMemberStatus.ACTIVE), eq(TeamStatus.ACTIVE)
		)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> homeService.getBestAttendance(MEMBER_ID, TEAM_ID))
				.isInstanceOfSatisfying(BusinessException.class, e ->
						assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TEAM008));
	}

	@Test
	@DisplayName("전원 태그 피드가 3개 이상이면 목록을 반환한다")
	void returnsAllTaggedFeedsWhenEnough() {
		List<FeedResponseDTO.FeedListItemDTO> items = List.of(
				dummyFeedItem(301L), dummyFeedItem(302L), dummyFeedItem(303L)
		);
		when(feedService.getAllTaggedItems(MEMBER_ID, TEAM_ID)).thenReturn(items);

		HomeResponseDTO.AllTaggedResponse result = homeService.getAllTaggedFeeds(MEMBER_ID, TEAM_ID);

		assertThat(result.getFeeds()).hasSize(3);
		assertThat(result.getFeeds().get(0).getFeedId()).isEqualTo(301L);
	}

	@Test
	@DisplayName("전원 태그 피드가 3개 미만이면 빈 목록을 반환한다")
	void returnsEmptyWhenNotEnoughAllTaggedFeeds() {
		when(feedService.getAllTaggedItems(MEMBER_ID, TEAM_ID)).thenReturn(List.of());

		HomeResponseDTO.AllTaggedResponse result = homeService.getAllTaggedFeeds(MEMBER_ID, TEAM_ID);

		assertThat(result.getFeeds()).isEmpty();
	}

	@Test
	@DisplayName("콤비네이션이 있으면 조합 멤버와 피드를 함께 반환한다")
	void returnsCombinationWithMembersAndFeeds() {
		List<FeedResponseDTO.TaggedMemberDTO> combo = List.of(
				taggedMember(101L, "홍길동"),
				taggedMember(102L, "김철수")
		);
		List<FeedResponseDTO.FeedListItemDTO> feeds = List.of(
				dummyFeedItem(501L), dummyFeedItem(502L), dummyFeedItem(503L)
		);
		when(feedService.getCombinationItems(MEMBER_ID, TEAM_ID))
				.thenReturn(new FeedService.CombinationItemsResult(combo, feeds));

		HomeResponseDTO.CombinationResponse result = homeService.getCombination(MEMBER_ID, TEAM_ID);

		assertThat(result.getCombination()).hasSize(2);
		assertThat(result.getCombination().get(0).getTeamMemberId()).isEqualTo(101L);
		assertThat(result.getFeeds()).hasSize(3);
	}

	@Test
	@DisplayName("콤비네이션 피드가 3개 미만이면 빈 조합과 빈 피드를 반환한다")
	void returnsEmptyWhenNotEnoughCombinationFeeds() {
		when(feedService.getCombinationItems(MEMBER_ID, TEAM_ID))
				.thenReturn(FeedService.CombinationItemsResult.empty());

		HomeResponseDTO.CombinationResponse result = homeService.getCombination(MEMBER_ID, TEAM_ID);

		assertThat(result.getCombination()).isEmpty();
		assertThat(result.getFeeds()).isEmpty();
	}

	@Test
	@DisplayName("장소 있는 피드가 3개 이상이면 지역 목록을 반환한다")
	void returnsRegionListWhenEnoughFeeds() {
		List<RegionRow> rows = List.of(
				regionRow("서울 강남구", 3L, "https://img1.jpg"),
				regionRow("경기 성남시 분당구", 2L, null)
		);
		stubRequester();
		when(feedRepository.countActiveFeedsWithRegion(TEAM_ID)).thenReturn(5L);
		when(feedRepository.findRegionSummaries(TEAM_ID)).thenReturn(rows);

		HomeResponseDTO.RegionResponse result = homeService.getRegions(MEMBER_ID, TEAM_ID);

		assertThat(result.getRegions()).hasSize(2);
		assertThat(result.getRegions().get(0).getRegion()).isEqualTo("서울 강남구");
		assertThat(result.getRegions().get(0).getFeedCount()).isEqualTo(3L);
		assertThat(result.getRegions().get(0).getThumbnailImageUrl()).isEqualTo("https://img1.jpg");
		assertThat(result.getRegions().get(1).getRegion()).isEqualTo("경기 성남시 분당구");
		assertThat(result.getRegions().get(1).getThumbnailImageUrl()).isNull();
	}

	@Test
	@DisplayName("장소 있는 피드가 3개 미만이면 빈 목록을 반환한다")
	void returnsEmptyRegionListWhenNotEnoughFeeds() {
		stubRequester();
		when(feedRepository.countActiveFeedsWithRegion(TEAM_ID)).thenReturn(2L);

		HomeResponseDTO.RegionResponse result = homeService.getRegions(MEMBER_ID, TEAM_ID);

		assertThat(result.getRegions()).isEmpty();
	}

	@Test
	@DisplayName("팀 멤버가 아니면 지역 조회도 TEAM008을 던진다")
	void throwsTeam008WhenNotTeamMemberForRegions() {
		when(teamMemberRepository.findActiveByMemberIdAndTeamId(
				eq(MEMBER_ID), eq(TEAM_ID),
				eq(TeamMemberStatus.ACTIVE), eq(TeamStatus.ACTIVE)
		)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> homeService.getRegions(MEMBER_ID, TEAM_ID))
				.isInstanceOfSatisfying(BusinessException.class, e ->
						assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TEAM008));
	}

	// --- helpers ---

	private void stubRequester() {
		when(teamMemberRepository.findActiveByMemberIdAndTeamId(
				eq(MEMBER_ID), eq(TEAM_ID),
				eq(TeamMemberStatus.ACTIVE), eq(TeamStatus.ACTIVE)
		)).thenReturn(Optional.of(requester));
	}

	private RegionRow regionRow(String region, long feedCount, String thumbnailUrl) {
		RegionRow row = mock(RegionRow.class);
		when(row.getRegion()).thenReturn(region);
		when(row.getFeedCount()).thenReturn(feedCount);
		when(row.getThumbnailUrl()).thenReturn(thumbnailUrl);
		return row;
	}

	private FeedResponseDTO.TaggedMemberDTO taggedMember(Long teamMemberId, String nickname) {
		return FeedResponseDTO.TaggedMemberDTO.builder()
				.teamMemberId(teamMemberId)
				.nickname(nickname)
				.profileImageUrl("https://example.com/p.png")
				.build();
	}

	private FeedResponseDTO.FeedListItemDTO dummyFeedItem(Long feedId) {
		return FeedResponseDTO.FeedListItemDTO.builder()
				.feedId(feedId)
				.type(FeedType.GENERAL)
				.content("피드 " + feedId)
				.author(FeedResponseDTO.AuthorDTO.builder()
						.teamMemberId(100L).nickname("테스터")
						.profileImageUrl("https://example.com/p.png")
						.isAnonymous(false).build())
				.reactionSummary(FeedResponseDTO.ReactionSummaryDTO.builder()
						.totalCount(0).items(List.of()).build())
				.commentCount(0)
				.createdAt(LocalDateTime.of(2026, 5, 15, 12, 0))
				.build();
	}

	private void stubTagRows(List<Object[]> rows) {
		YearMonth currentMonth = YearMonth.now();
		LocalDateTime start = currentMonth.atDay(1).atStartOfDay();
		LocalDateTime end = currentMonth.plusMonths(1).atDay(1).atStartOfDay();

		when(feedTagRepository.findTopTaggedMembersByTeamAndMonth(
				eq(TEAM_ID), eq(start), eq(end), eq(TeamMemberStatus.ACTIVE)
		)).thenReturn(rows);
	}
}
