package kusitms.spin.tikitak.controller.me;

import kusitms.spin.tikitak.domain.member.enums.MemberStatus;
import kusitms.spin.tikitak.domain.member.enums.ProfileCharacterType;
import kusitms.spin.tikitak.domain.member.enums.SocialProvider;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberRole;
import kusitms.spin.tikitak.service.me.MeService;
import kusitms.spin.tikitak.service.me.dto.MeRequestDTO;
import kusitms.spin.tikitak.service.me.dto.MeResponseDTO;
import kusitms.spin.tikitak.support.ApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasKey;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MeControllerTest extends ApiTest {

	@Mock
	private MeService meService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = mockMvc(new MeController(meService));
	}

	@Test
	@DisplayName("GET /api/v1/me는 내 계정 요약 정보를 반환한다")
	void getMyProfile() throws Exception {
		when(meService.getMyProfile(TEST_MEMBER_ID)).thenReturn(MeResponseDTO.MeProfileResponseDTO.builder()
				.memberId(TEST_MEMBER_ID)
				.email("user@example.com")
				.socialProvider(SocialProvider.KAKAO)
				.status(MemberStatus.ACTIVE)
				.hasAgreedRequiredTerms(true)
				.onboardingCompleted(true)
				.profileCharacterType(ProfileCharacterType.TAK_LEADER)
				.activeTeamId(10L)
				.hasTeam(true)
				.createdAt(LocalDateTime.of(2026, 3, 4, 20, 30))
				.build());

		mockMvc.perform(get("/api/v1/me"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.status").value(200))
				.andExpect(jsonPath("$.data.memberId").value(1L))
				.andExpect(jsonPath("$.data.email").value("user@example.com"))
				.andExpect(jsonPath("$.data.socialProvider").value("KAKAO"))
				.andExpect(jsonPath("$.data.status").value("ACTIVE"))
				.andExpect(jsonPath("$.data.hasAgreedRequiredTerms").value(true))
				.andExpect(jsonPath("$.data.onboardingCompleted").value(true))
				.andExpect(jsonPath("$.data.profileCharacterType").value("TAK_LEADER"))
				.andExpect(jsonPath("$.data.activeTeamId").value(10L))
				.andExpect(jsonPath("$.data.hasTeam").value(true));
	}

	@Test
	@DisplayName("GET /api/v1/me/teams는 간결한 팀 목록을 반환한다")
	void getMyTeams() throws Exception {
		when(meService.getMyTeams(TEST_MEMBER_ID)).thenReturn(MeResponseDTO.TeamListResponseDTO.builder()
				.teams(List.of(MeResponseDTO.TeamItemDTO.builder()
						.teamId(10L)
						.teamMemberId(100L)
						.teamName("티키탁")
						.description("우리 팀 공간")
						.role(TeamMemberRole.OWNER)
						.nickname("민경")
						.memberCount(5L)
						.isActive(true)
						.joinedAt(LocalDateTime.of(2026, 3, 4, 20, 30))
						.build()))
				.build());

		mockMvc.perform(get("/api/v1/me/teams"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.teams[0].teamId").value(10L))
				.andExpect(jsonPath("$.data.teams[0].teamMemberId").value(100L))
				.andExpect(jsonPath("$.data.teams[0].teamName").value("티키탁"))
				.andExpect(jsonPath("$.data.teams[0].role").value("OWNER"))
				.andExpect(jsonPath("$.data.teams[0].memberCount").value(5L))
				.andExpect(jsonPath("$.data.teams[0].isActive").value(true))
				.andExpect(jsonPath("$.data.teams[0]", not(hasKey("teamImageUrl"))))
				.andExpect(jsonPath("$.data.teams[0]", not(hasKey("memberStatus"))))
				.andExpect(jsonPath("$.data.teams[0]", not(hasKey("teamStatus"))));
	}

	@Test
	@DisplayName("PATCH /api/v1/me/active-team은 활성 팀 변경 결과를 반환한다")
	void updateActiveTeam() throws Exception {
		when(meService.updateActiveTeam(any(Long.class), any(MeRequestDTO.ActiveTeamUpdateRequestDTO.class)))
				.thenReturn(MeResponseDTO.ActiveTeamUpdateResponseDTO.builder()
						.activeTeamId(10L)
						.build());

		mockMvc.perform(patch("/api/v1/me/active-team")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "teamId": 10
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.activeTeamId").value(10L));
	}

	@Test
	@DisplayName("PATCH /api/v1/me/active-team은 teamId가 없으면 400을 반환한다")
	void updateActiveTeamReturnsBadRequestWhenTeamIdMissing() throws Exception {
		mockMvc.perform(patch("/api/v1/me/active-team")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.status").value(400));
	}

	@Test
	@DisplayName("PATCH /api/v1/me/active-team은 teamId가 양수가 아니면 400을 반환한다")
	void updateActiveTeamReturnsBadRequestWhenTeamIdIsNotPositive() throws Exception {
		mockMvc.perform(patch("/api/v1/me/active-team")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "teamId": 0
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.status").value(400));
	}

	@Test
	@DisplayName("GET /api/v1/me/agreements는 약관 동의 상태를 반환한다")
	void getAgreements() throws Exception {
		when(meService.getAgreements(TEST_MEMBER_ID)).thenReturn(MeResponseDTO.AgreementResponseDTO.builder()
				.termsAgreed(true)
				.privacyAgreed(true)
				.termsAgreedAt(LocalDateTime.of(2026, 3, 4, 20, 30))
				.build());

		mockMvc.perform(get("/api/v1/me/agreements"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.termsAgreed").value(true))
				.andExpect(jsonPath("$.data.privacyAgreed").value(true))
				.andExpect(jsonPath("$.data.termsAgreedAt").exists());
	}

	@Test
	@DisplayName("PUT /api/v1/me/agreements는 약관 동의 저장 결과를 반환한다")
	void updateAgreements() throws Exception {
		when(meService.updateAgreements(any(Long.class), any(MeRequestDTO.AgreementUpdateRequestDTO.class)))
				.thenReturn(MeResponseDTO.AgreementResponseDTO.builder()
						.termsAgreed(true)
						.privacyAgreed(true)
						.termsAgreedAt(LocalDateTime.of(2026, 3, 4, 20, 30))
						.build());

		mockMvc.perform(put("/api/v1/me/agreements")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "termsAgreed": true,
								  "privacyAgreed": true
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.termsAgreed").value(true))
				.andExpect(jsonPath("$.data.privacyAgreed").value(true));
	}

	@Test
	@DisplayName("PATCH /api/v1/me/onboarding은 온보딩 프로필 저장 결과를 반환한다")
	void updateOnboarding() throws Exception {
		when(meService.updateOnboarding(any(Long.class), any(MeRequestDTO.OnboardingUpdateRequestDTO.class)))
				.thenReturn(MeResponseDTO.OnboardingUpdateResponseDTO.builder()
						.onboardingCompleted(true)
						.profileCharacterType(ProfileCharacterType.TAK_SPARK)
						.build());

		mockMvc.perform(patch("/api/v1/me/onboarding")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "profileCharacterType": "TAK_SPARK"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.onboardingCompleted").value(true))
				.andExpect(jsonPath("$.data.profileCharacterType").value("TAK_SPARK"));
	}

	@Test
	@DisplayName("PATCH /api/v1/me/onboarding은 프로필 캐릭터가 없으면 400을 반환한다")
	void updateOnboardingReturnsBadRequestWhenProfileCharacterTypeMissing() throws Exception {
		mockMvc.perform(patch("/api/v1/me/onboarding")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.status").value(400));
	}

	@Test
	@DisplayName("PATCH /api/v1/me/onboarding은 지원하지 않는 프로필 캐릭터면 400을 반환한다")
	void updateOnboardingReturnsBadRequestWhenProfileCharacterTypeInvalid() throws Exception {
		mockMvc.perform(patch("/api/v1/me/onboarding")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "profileCharacterType": "INVALID"
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.status").value(400));
	}
}
