package kusitms.spin.tikitak.controller.home;

import kusitms.spin.tikitak.domain.feed.enums.FeedType;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.service.feed.dto.FeedResponseDTO;
import kusitms.spin.tikitak.service.home.HomeService;
import kusitms.spin.tikitak.service.home.dto.HomeResponseDTO;
import kusitms.spin.tikitak.support.ApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HomeControllerTest extends ApiTest {

	private static final Long TEAM_ID = 10L;

	@Mock
	private HomeService homeService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = mockMvc(new HomeController(homeService));
	}

	@Test
	@DisplayName("GET /api/v1/teams/{teamId}/home/best-attendance는 상위 3명을 반환한다")
	void getBestAttendance() throws Exception {
		HomeResponseDTO.BestAttendanceResponse response = HomeResponseDTO.BestAttendanceResponse.builder()
				.members(List.of(
						member(1, 101L, "다다", "https://example.com/1.png", 5L),
						member(2, 102L, "가가", "https://example.com/2.png", 3L),
						member(3, 103L, "나나", "https://example.com/3.png", 3L)
				))
				.build();

		when(homeService.getBestAttendance(TEST_MEMBER_ID, TEAM_ID)).thenReturn(response);

		mockMvc.perform(get("/api/v1/teams/{teamId}/home/best-attendance", TEAM_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.status").value(200))
				.andExpect(jsonPath("$.data.members").isArray())
				.andExpect(jsonPath("$.data.members.length()").value(3))
				.andExpect(jsonPath("$.data.members[0].rank").value(1))
				.andExpect(jsonPath("$.data.members[0].teamMemberId").value(101))
				.andExpect(jsonPath("$.data.members[0].nickname").value("다다"))
				.andExpect(jsonPath("$.data.members[0].tagCount").value(5))
				.andExpect(jsonPath("$.data.members[1].rank").value(2))
				.andExpect(jsonPath("$.data.members[1].nickname").value("가가"))
				.andExpect(jsonPath("$.data.members[2].rank").value(3))
				.andExpect(jsonPath("$.data.members[2].nickname").value("나나"));
	}

	@Test
	@DisplayName("태그된 팀원이 없으면 빈 목록을 반환한다")
	void getBestAttendanceReturnsEmptyList() throws Exception {
		when(homeService.getBestAttendance(TEST_MEMBER_ID, TEAM_ID))
				.thenReturn(HomeResponseDTO.BestAttendanceResponse.builder().members(List.of()).build());

		mockMvc.perform(get("/api/v1/teams/{teamId}/home/best-attendance", TEAM_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.members").isEmpty());
	}

	@Test
	@DisplayName("팀 접근 권한이 없으면 403을 반환한다")
	void getBestAttendanceReturnsForbiddenWhenNotTeamMember() throws Exception {
		when(homeService.getBestAttendance(TEST_MEMBER_ID, TEAM_ID))
				.thenThrow(new BusinessException(ErrorCode.TEAM008));

		mockMvc.perform(get("/api/v1/teams/{teamId}/home/best-attendance", TEAM_ID))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("TEAM008"));
	}

	@Test
	@DisplayName("GET /api/v1/teams/{teamId}/home/everyone-pick는 PICK 목록을 반환한다")
	void getEveryonePick() throws Exception {
		HomeResponseDTO.EveryonePickResponse response = HomeResponseDTO.EveryonePickResponse.builder()
				.picks(List.of(
						feedListItem(301L, "오늘 정말 즐거웠다", 5, 3),
						feedListItem(302L, "다음에 또 가고 싶다", 3, 1)
				))
				.build();

		when(homeService.getEveryonePick(TEST_MEMBER_ID, TEAM_ID)).thenReturn(response);

		mockMvc.perform(get("/api/v1/teams/{teamId}/home/everyone-pick", TEAM_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.picks").isArray())
				.andExpect(jsonPath("$.data.picks.length()").value(2))
				.andExpect(jsonPath("$.data.picks[0].feedId").value(301))
				.andExpect(jsonPath("$.data.picks[0].content").value("오늘 정말 즐거웠다"))
				.andExpect(jsonPath("$.data.picks[0].reactionSummary.totalCount").value(5))
				.andExpect(jsonPath("$.data.picks[0].commentCount").value(3))
				.andExpect(jsonPath("$.data.picks[1].feedId").value(302));
	}

	@Test
	@DisplayName("당월 피드가 부족하면 빈 PICK 목록을 반환한다")
	void getEveryonePickReturnsEmptyWhenNotEnoughFeeds() throws Exception {
		when(homeService.getEveryonePick(TEST_MEMBER_ID, TEAM_ID))
				.thenReturn(HomeResponseDTO.EveryonePickResponse.builder().picks(List.of()).build());

		mockMvc.perform(get("/api/v1/teams/{teamId}/home/everyone-pick", TEAM_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.picks").isEmpty());
	}

	@Test
	@DisplayName("팀 접근 권한이 없으면 모두의 PICK도 403을 반환한다")
	void getEveryonePickReturnsForbiddenWhenNotTeamMember() throws Exception {
		when(homeService.getEveryonePick(TEST_MEMBER_ID, TEAM_ID))
				.thenThrow(new BusinessException(ErrorCode.TEAM008));

		mockMvc.perform(get("/api/v1/teams/{teamId}/home/everyone-pick", TEAM_ID))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("TEAM008"));
	}

	@Test
	@DisplayName("GET /api/v1/teams/{teamId}/home/all-tagged는 전원 태그 피드 목록을 반환한다")
	void getAllTaggedFeeds() throws Exception {
		HomeResponseDTO.AllTaggedResponse response = HomeResponseDTO.AllTaggedResponse.builder()
				.feeds(List.of(
						feedListItem(401L, "전원 태그 피드1", 2, 1),
						feedListItem(402L, "전원 태그 피드2", 1, 0),
						feedListItem(403L, "전원 태그 피드3", 0, 3)
				))
				.build();

		when(homeService.getAllTaggedFeeds(TEST_MEMBER_ID, TEAM_ID)).thenReturn(response);

		mockMvc.perform(get("/api/v1/teams/{teamId}/home/all-tagged", TEAM_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.feeds").isArray())
				.andExpect(jsonPath("$.data.feeds.length()").value(3))
				.andExpect(jsonPath("$.data.feeds[0].feedId").value(401))
				.andExpect(jsonPath("$.data.feeds[0].content").value("전원 태그 피드1"))
				.andExpect(jsonPath("$.data.feeds[1].feedId").value(402))
				.andExpect(jsonPath("$.data.feeds[2].feedId").value(403));
	}

	@Test
	@DisplayName("전원 태그 피드가 3개 미만이면 빈 목록을 반환한다")
	void getAllTaggedFeedsReturnsEmptyWhenNotEnough() throws Exception {
		when(homeService.getAllTaggedFeeds(TEST_MEMBER_ID, TEAM_ID))
				.thenReturn(HomeResponseDTO.AllTaggedResponse.builder().feeds(List.of()).build());

		mockMvc.perform(get("/api/v1/teams/{teamId}/home/all-tagged", TEAM_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.feeds").isEmpty());
	}

	@Test
	@DisplayName("팀 접근 권한이 없으면 전원 태그 피드도 403을 반환한다")
	void getAllTaggedFeedsReturnsForbiddenWhenNotTeamMember() throws Exception {
		when(homeService.getAllTaggedFeeds(TEST_MEMBER_ID, TEAM_ID))
				.thenThrow(new BusinessException(ErrorCode.TEAM008));

		mockMvc.perform(get("/api/v1/teams/{teamId}/home/all-tagged", TEAM_ID))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("TEAM008"));
	}

	// --- helpers ---

	private HomeResponseDTO.BestAttendanceMemberDTO member(
			int rank, Long teamMemberId, String nickname, String profileImgUrl, long tagCount
	) {
		return HomeResponseDTO.BestAttendanceMemberDTO.builder()
				.rank(rank)
				.teamMemberId(teamMemberId)
				.nickname(nickname)
				.profileImgUrl(profileImgUrl)
				.tagCount(tagCount)
				.build();
	}

	private FeedResponseDTO.FeedListItemDTO feedListItem(
			Long feedId, String content, long reactionTotal, long commentCount
	) {
		return FeedResponseDTO.FeedListItemDTO.builder()
				.feedId(feedId)
				.type(FeedType.GENERAL)
				.content(content)
				.author(FeedResponseDTO.AuthorDTO.builder()
						.teamMemberId(101L)
						.nickname("테스터")
						.profileImageUrl("https://example.com/profile.png")
						.isAnonymous(false)
						.build())
				.reactionSummary(FeedResponseDTO.ReactionSummaryDTO.builder()
						.totalCount(reactionTotal)
						.items(List.of())
						.build())
				.commentCount(commentCount)
				.createdAt(LocalDateTime.of(2026, 5, 15, 12, 0))
				.build();
	}
}
