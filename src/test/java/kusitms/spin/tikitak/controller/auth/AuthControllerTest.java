package kusitms.spin.tikitak.controller.auth;

import kusitms.spin.tikitak.global.config.AuthProperties;
import kusitms.spin.tikitak.service.auth.AuthService;
import kusitms.spin.tikitak.service.auth.dto.LoginResponse;
import kusitms.spin.tikitak.service.auth.dto.OAuthAuthorizeUrlResponse;
import kusitms.spin.tikitak.service.auth.dto.TokenResponse;
import kusitms.spin.tikitak.support.ApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest extends ApiTest {

	@Mock
	private AuthService authService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = mockMvc(new AuthController(authService, authProperties()));
	}

	@Test
	@DisplayName("OAuth 시작은 redirect=false일 때 인증 URL을 JSON으로 반환한다")
	void startOAuthLoginReturnsAuthorizeUrlWhenRedirectFalse() throws Exception {
		when(authService.getAuthorizeUrl("kakao")).thenReturn(new OAuthAuthorizeUrlResponse(
				URI.create("https://kauth.kakao.com/oauth/authorize?state=state"),
				"state"
		));

		mockMvc.perform(get("/api/v1/auth/oauth/kakao/start")
						.param("redirect", "false"))
				.andExpect(status().isOk())
				.andExpect(cookie().value("oauthState", "state"))
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.authorizeUrl").value("https://kauth.kakao.com/oauth/authorize?state=state"))
				.andExpect(jsonPath("$.data.state").value("state"));
	}

	@Test
	@DisplayName("OAuth 시작은 기본적으로 provider 인증 URL로 redirect한다")
	void startOAuthLoginRedirectsByDefault() throws Exception {
		when(authService.getAuthorizeUrl("kakao")).thenReturn(new OAuthAuthorizeUrlResponse(
				URI.create("https://kauth.kakao.com/oauth/authorize?state=state"),
				"state"
		));

		mockMvc.perform(get("/api/v1/auth/oauth/kakao/start"))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("https://kauth.kakao.com/oauth/authorize?state=state"))
				.andExpect(cookie().value("oauthState", "state"));
	}

	@Test
	@DisplayName("OAuth callback은 로그인 결과를 프론트 redirect URI query parameter로 전달한다")
	void handleOAuthCallbackRedirectsWithLoginResult() throws Exception {
		when(authService.loginWithOAuth("kakao", "code", "state", "state"))
				.thenReturn(new LoginResponse(
						"access-token",
						"refresh-token",
						false,
						true,
						10L
				));

		String location = mockMvc.perform(get("/api/v1/auth/oauth/kakao/callback")
						.param("code", "code")
						.param("state", "state")
						.cookie(new jakarta.servlet.http.Cookie("oauthState", "state")))
				.andExpect(status().isFound())
				.andExpect(cookie().value("refreshToken", "refresh-token"))
				.andExpect(header().exists("Location"))
				.andReturn()
				.getResponse()
				.getHeader("Location");

		assertThat(location).startsWith("http://localhost:5173/oauth/callback");
		assertThat(location).contains("accessToken=access-token");
		assertThat(location).contains("isNewMember=false");
		assertThat(location).contains("hasAgreedRequiredTerms=true");
		assertThat(location).contains("activeTeamId=10");
	}

	@Test
	@DisplayName("토큰 재발급은 request body의 refreshToken을 우선 사용한다")
	void refreshTokenUsesRequestBodyFirst() throws Exception {
		when(authService.refreshToken("body-refresh-token"))
				.thenReturn(new TokenResponse("new-access-token", "new-refresh-token", "Bearer", 3600L));

		mockMvc.perform(post("/api/v1/auth/token/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "refreshToken": "body-refresh-token"
								}
								""")
						.cookie(new jakarta.servlet.http.Cookie("refreshToken", "cookie-refresh-token")))
				.andExpect(status().isOk())
				.andExpect(cookie().value("refreshToken", "new-refresh-token"))
				.andExpect(jsonPath("$.data.accessToken").value("new-access-token"))
				.andExpect(jsonPath("$.data.refreshToken").value("new-refresh-token"));

		verify(authService).refreshToken(eq("body-refresh-token"));
	}

	@Test
	@DisplayName("토큰 재발급은 request body가 없으면 cookie refreshToken을 사용한다")
	void refreshTokenUsesCookieWhenRequestBodyMissing() throws Exception {
		when(authService.refreshToken("cookie-refresh-token"))
				.thenReturn(new TokenResponse("new-access-token", "new-refresh-token", "Bearer", 3600L));

		mockMvc.perform(post("/api/v1/auth/token/refresh")
						.cookie(new jakarta.servlet.http.Cookie("refreshToken", "cookie-refresh-token")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.accessToken").value("new-access-token"));

		verify(authService).refreshToken(eq("cookie-refresh-token"));
	}

	@Test
	@DisplayName("로그아웃은 refresh token을 폐기하고 refresh cookie를 만료한다")
	void logoutRevokesRefreshTokenAndExpiresCookie() throws Exception {
		mockMvc.perform(post("/api/v1/auth/logout")
						.cookie(new jakarta.servlet.http.Cookie("refreshToken", "refresh-token")))
				.andExpect(status().isOk())
				.andExpect(cookie().maxAge("refreshToken", 0))
				.andExpect(jsonPath("$.data.loggedOut").value(true));

		verify(authService).logout(eq("refresh-token"));
	}

	private AuthProperties authProperties() {
		return new AuthProperties(
				new AuthProperties.OAuth(
						"http://localhost:5173/oauth/callback",
						new AuthProperties.Google(
								"google-client-id",
								"google-client-secret",
								"http://localhost:8080/api/v1/auth/oauth/google/callback"
						),
						new AuthProperties.Kakao(
								"kakao-client-id",
								"kakao-client-secret",
								"http://localhost:8080/api/v1/auth/oauth/kakao/callback"
						)
				),
				new AuthProperties.Jwt(
						"test-secret",
						3600L,
						604800L,
						false
				)
		);
	}
}
