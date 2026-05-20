package kusitms.spin.tikitak.controller.home;

import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.service.home.HomeService;
import kusitms.spin.tikitak.service.home.dto.HomeResponseDTO;
import kusitms.spin.tikitak.support.ApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.test.web.servlet.MockMvc;

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
}
