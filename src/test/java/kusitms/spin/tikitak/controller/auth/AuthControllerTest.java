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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
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
	@DisplayName("OAuth start returns authorize URL as JSON when redirect is false")
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
	@DisplayName("OAuth start redirects to provider authorize URL by default")
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
	@DisplayName("OAuth start sets oauthMode cookie when mode is app")
	void startOAuthLoginSetsOAuthModeCookieWhenModeIsApp() throws Exception {
		when(authService.getAuthorizeUrl("kakao")).thenReturn(new OAuthAuthorizeUrlResponse(
				URI.create("https://kauth.kakao.com/oauth/authorize?state=state"),
				"state"
		));

		mockMvc.perform(get("/api/v1/auth/oauth/kakao/start")
						.param("mode", "app"))
				.andExpect(status().isFound())
				.andExpect(cookie().value("oauthState", "state"))
				.andExpect(header().stringValues("Set-Cookie",
						hasItem(containsString("oauthMode=app"))));
	}

	@Test
	@DisplayName("OAuth callback redirects to frontend with login result")
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
				.andExpect(header().stringValues("Set-Cookie",
						hasItem(containsString("refreshToken=refresh-token;"))))
				.andExpect(header().stringValues("Set-Cookie",
						hasItem(containsString("SameSite=None"))))
				.andExpect(header().stringValues("Set-Cookie",
						hasItem(containsString("Secure"))))
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
	@DisplayName("Apple OAuth callback passes id_token to login service")
	void handleAppleOAuthCallbackUsesIdTokenParameter() throws Exception {
		when(authService.loginWithOAuth("apple", "code", "state", "state", "id-token"))
				.thenReturn(new LoginResponse(
						"access-token",
						"refresh-token",
						true,
						false,
						null
				));

		String location = mockMvc.perform(post("/api/v1/auth/oauth/apple/callback")
						.param("code", "code")
						.param("state", "state")
						.param("id_token", "id-token")
						.cookie(new jakarta.servlet.http.Cookie("oauthState", "state")))
				.andExpect(status().isFound())
				.andExpect(cookie().value("refreshToken", "refresh-token"))
				.andExpect(header().exists("Location"))
				.andReturn()
				.getResponse()
				.getHeader("Location");

		assertThat(location).contains("accessToken=access-token");
		assertThat(location).contains("isNewMember=true");
		verify(authService).loginWithOAuth("apple", "code", "state", "state", "id-token");
	}

	@Test
	@DisplayName("Apple OAuth callback redirects to deep link when oauthMode is app")
	void handleAppleOAuthCallbackRedirectsToDeepLinkWhenModeIsApp() throws Exception {
		when(authService.issueAppLoginCode("apple", "code", "state", "state", "id-token"))
				.thenReturn("applelogincode1234567890abcdef");

		String location = mockMvc.perform(post("/api/v1/auth/oauth/apple/callback")
						.param("code", "code")
						.param("state", "state")
						.param("id_token", "id-token")
						.cookie(new jakarta.servlet.http.Cookie("oauthState", "state"))
						.cookie(new jakarta.servlet.http.Cookie("oauthMode", "app")))
				.andExpect(status().isFound())
				.andExpect(header().stringValues("Set-Cookie",
						hasItem(containsString("oauthMode=;"))))
				.andExpect(header().exists("Location"))
				.andReturn()
				.getResponse()
				.getHeader("Location");

		assertThat(location).startsWith("tikitak://oauth/callback");
		assertThat(location).contains("loginCode=applelogincode1234567890abcdef");
		verify(authService).issueAppLoginCode("apple", "code", "state", "state", "id-token");
	}

	@Test
	@DisplayName("OAuth callback redirects to deep link when oauthMode is app")
	void handleOAuthCallbackRedirectsToDeepLinkWhenModeIsApp() throws Exception {
		when(authService.issueAppLoginCode("kakao", "code", "state", "state"))
				.thenReturn("testlogincode1234567890abcdef");

		String location = mockMvc.perform(get("/api/v1/auth/oauth/kakao/callback")
						.param("code", "code")
						.param("state", "state")
						.cookie(new jakarta.servlet.http.Cookie("oauthState", "state"))
						.cookie(new jakarta.servlet.http.Cookie("oauthMode", "app")))
				.andExpect(status().isFound())
				.andExpect(header().exists("Location"))
				.andReturn()
				.getResponse()
				.getHeader("Location");

		assertThat(location).startsWith("tikitak://oauth/callback");
		assertThat(location).contains("loginCode=testlogincode1234567890abcdef");
	}

	@Test
	@DisplayName("App OAuth callback expires oauthMode cookie")
	void handleAppOAuthCallbackExpiresOAuthModeCookie() throws Exception {
		when(authService.issueAppLoginCode("kakao", "code", "state", "state"))
				.thenReturn("testlogincode");

		mockMvc.perform(get("/api/v1/auth/oauth/kakao/callback")
						.param("code", "code")
						.param("state", "state")
						.cookie(new jakarta.servlet.http.Cookie("oauthState", "state"))
						.cookie(new jakarta.servlet.http.Cookie("oauthMode", "app")))
				.andExpect(status().isFound())
				.andExpect(header().stringValues("Set-Cookie",
						hasItem(containsString("oauthMode=;"))));
	}

	@Test
	@DisplayName("Login code exchange returns access token and refresh token")
	void exchangeLoginCodeReturnsTokens() throws Exception {
		when(authService.exchangeLoginCode("validlogincode"))
				.thenReturn(new LoginResponse("access-token", "refresh-token", true, false, null));

		mockMvc.perform(post("/api/v1/auth/oauth/login-code/exchange")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"loginCode": "validlogincode"}
								"""))
				.andExpect(status().isOk())
				.andExpect(cookie().value("refreshToken", "refresh-token"))
				.andExpect(header().stringValues("Set-Cookie",
						hasItem(containsString("SameSite=None"))))
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.accessToken").value("access-token"))
				.andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
				.andExpect(jsonPath("$.data.isNewMember").value(true))
				.andExpect(jsonPath("$.data.hasAgreedRequiredTerms").value(false));

		verify(authService).exchangeLoginCode(eq("validlogincode"));
	}

	@Test
	@DisplayName("Token refresh uses refreshToken from request body first")
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
				.andExpect(header().stringValues("Set-Cookie",
						hasItem(containsString("SameSite=None"))))
				.andExpect(header().stringValues("Set-Cookie",
						hasItem(containsString("Secure"))))
				.andExpect(jsonPath("$.data.accessToken").value("new-access-token"))
				.andExpect(jsonPath("$.data.refreshToken").value("new-refresh-token"));

		verify(authService).refreshToken(eq("body-refresh-token"));
	}

	@Test
	@DisplayName("Token refresh uses refreshToken from cookie when request body is missing")
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
	@DisplayName("Logout revokes refresh token and expires refresh cookie")
	void logoutRevokesRefreshTokenAndExpiresCookie() throws Exception {
		mockMvc.perform(post("/api/v1/auth/logout")
						.cookie(new jakarta.servlet.http.Cookie("refreshToken", "refresh-token")))
				.andExpect(status().isOk())
				.andExpect(cookie().maxAge("refreshToken", 0))
				.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=None")))
				.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Secure")))
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
						),
						new AuthProperties.Apple(
								"apple-client-id",
								"apple-team-id",
								"apple-key-id",
								"apple-private-key",
								"http://localhost:8080/api/v1/auth/oauth/apple/callback"
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
