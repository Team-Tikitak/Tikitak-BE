package kusitms.spin.tikitak.controller.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import kusitms.spin.tikitak.global.config.AuthProperties;
import kusitms.spin.tikitak.global.dto.CommonResponse;
import kusitms.spin.tikitak.service.auth.AuthService;
import kusitms.spin.tikitak.service.auth.dto.LoginResponse;
import kusitms.spin.tikitak.service.auth.dto.LogoutResponse;
import kusitms.spin.tikitak.service.auth.dto.OAuthAuthorizeUrlResponse;
import kusitms.spin.tikitak.service.auth.dto.RefreshTokenRequest;
import kusitms.spin.tikitak.service.auth.dto.TokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;

@RestController
@RequiredArgsConstructor
@Tag(name = "Auth", description = "OAuth 로그인, 토큰 재발급, 로그아웃 API")
public class AuthController {

	private final AuthService authService;
	private final AuthProperties authProperties;

	@Operation(
			summary = "OAuth 로그인 시작",
			description = "지원하는 OAuth Provider 인증 페이지로 리다이렉트합니다. Swagger에서 테스트할 때는 redirect=false로 호출하면 인증 URL을 JSON으로 확인할 수 있습니다."
	)
	@GetMapping("/api/v1/auth/oauth/{provider}/start")
	public ResponseEntity<?> startOAuthLogin(
			@Parameter(description = "OAuth Provider. google, kakao", example = "kakao")
			@PathVariable String provider,
			@Parameter(
					description = "true면 302 Redirect, false면 Swagger 테스트용 JSON 응답",
					schema = @Schema(defaultValue = "false")
			)
			@RequestParam(required = false) Boolean redirect
	) {
		OAuthAuthorizeUrlResponse response = authService.getAuthorizeUrl(provider);
		if (Boolean.FALSE.equals(redirect)) {
			return ResponseEntity.ok()
					.header(HttpHeaders.SET_COOKIE, oauthStateCookie(response.state()).toString())
					.body(CommonResponse.success(response));
		}

		return ResponseEntity.status(HttpStatus.FOUND)
				.header(HttpHeaders.LOCATION, response.authorizeUrl().toString())
				.header(HttpHeaders.SET_COOKIE, oauthStateCookie(response.state()).toString())
				.build();
	}

	@Operation(
			summary = "OAuth 콜백 처리 및 로그인 완료",
			description = "Provider에서 전달한 인가 코드를 검증하고 로그인 또는 회원가입을 완료한 뒤 프론트 웹앱으로 리다이렉트합니다."
	)
	@GetMapping("/api/v1/auth/oauth/{provider}/callback")
	public ResponseEntity<Void> handleOAuthCallback(
			@Parameter(description = "OAuth Provider. google, kakao", example = "kakao")
			@PathVariable String provider,
			@Parameter(description = "Provider가 전달한 인가 코드")
			@RequestParam(required = false) String code,
			@Parameter(description = "OAuth 요청 검증용 state")
			@RequestParam(required = false) String state,
			@CookieValue(name = "oauthState", required = false) String savedState
	) {
		LoginResponse loginResponse = authService.loginWithOAuth(provider, code, state, savedState, null);
		URI redirectUri = UriComponentsBuilder.fromUriString(authProperties.oauth().frontendRedirectUri())
				.queryParam("accessToken", loginResponse.accessToken())
				.queryParam("isNewMember", loginResponse.isNewMember())
				.queryParam("hasAgreedRequiredTerms", loginResponse.hasAgreedRequiredTerms())
				.queryParam("activeTeamId", loginResponse.activeTeamId())
				.build()
				.encode()
				.toUri();

		return ResponseEntity.status(HttpStatus.FOUND)
				.header(HttpHeaders.LOCATION, redirectUri.toString())
				.header(HttpHeaders.SET_COOKIE, refreshTokenCookie(loginResponse.refreshToken()).toString())
				.header(HttpHeaders.SET_COOKIE, expireOAuthStateCookie().toString())
				.build();
	}

	@Operation(
			summary = "액세스 토큰 재발급",
			description = "refresh token을 검증하고 새로운 access token과 refresh token을 발급합니다."
	)
	@PostMapping("/api/v1/auth/token/refresh")
	public ResponseEntity<CommonResponse<TokenResponse>> refreshToken(
			@RequestBody(required = false) RefreshTokenRequest request,
			@CookieValue(name = "refreshToken", required = false) String refreshTokenCookie
	) {
		String refreshToken = request != null && request.refreshToken() != null
				? request.refreshToken()
				: refreshTokenCookie;
		TokenResponse tokenResponse = authService.refreshToken(refreshToken);

		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, refreshTokenCookie(tokenResponse.refreshToken()).toString())
				.body(CommonResponse.success(tokenResponse));
	}

	@Operation(
			summary = "Apple OAuth 콜백 처리 및 로그인 완료",
			description = "Apple에서 전달한 인가 코드와 id_token을 검증하고 로그인 또는 회원가입을 완료한 뒤 프론트 웹앱으로 리다이렉트합니다."
	)
	@PostMapping("/api/v1/auth/oauth/apple/callback")
	public ResponseEntity<Void> handleAppleOAuthCallback(
			@Parameter(description = "Apple에서 전달한 인가 코드")
			@RequestParam(required = false) String code,
			@Parameter(description = "OAuth 요청 검증용 state")
			@RequestParam(required = false) String state,
			@Parameter(description = "Apple 사용자 식별 정보를 포함한 JWT")
			@RequestParam(name = "id_token", required = false) String idToken,
			@CookieValue(name = "oauthState", required = false) String savedState
	) {
		LoginResponse loginResponse = authService.loginWithOAuth("apple", code, state, savedState, idToken);
		URI redirectUri = UriComponentsBuilder.fromUriString(authProperties.oauth().frontendRedirectUri())
				.queryParam("accessToken", loginResponse.accessToken())
				.queryParam("isNewMember", loginResponse.isNewMember())
				.queryParam("hasAgreedRequiredTerms", loginResponse.hasAgreedRequiredTerms())
				.queryParam("activeTeamId", loginResponse.activeTeamId())
				.build()
				.encode()
				.toUri();

		return ResponseEntity.status(HttpStatus.FOUND)
				.header(HttpHeaders.LOCATION, redirectUri.toString())
				.header(HttpHeaders.SET_COOKIE, refreshTokenCookie(loginResponse.refreshToken()).toString())
				.header(HttpHeaders.SET_COOKIE, expireOAuthStateCookie().toString())
				.build();
	}

	@Operation(
			summary = "로그아웃",
			description = "브라우저의 refresh token 쿠키를 만료시켜 로그아웃 처리합니다."
	)
	@PostMapping("/api/v1/auth/logout")
	public ResponseEntity<CommonResponse<LogoutResponse>> logout(
			@CookieValue(name = "refreshToken", required = false) String refreshToken
	) {
		authService.logout(refreshToken);
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, expireRefreshTokenCookie().toString())
				.body(CommonResponse.success(new LogoutResponse(true)));
	}

	private ResponseCookie oauthStateCookie(String state) {
		return ResponseCookie.from("oauthState", state)
				.httpOnly(true)
				.secure(authProperties.jwt().cookieSecure())
				.path("/api/v1/auth/oauth")
				.sameSite(oauthStateCookieSameSite())
				.maxAge(Duration.ofMinutes(5))
				.build();
	}

	private ResponseCookie refreshTokenCookie(String refreshToken) {
		return ResponseCookie.from("refreshToken", refreshToken)
				.httpOnly(true)
				.secure(true)
				.path("/")
				.sameSite("None")
				.maxAge(Duration.ofSeconds(authProperties.jwt().refreshTokenExpiresIn()))
				.build();
	}

	private ResponseCookie expireOAuthStateCookie() {
		return ResponseCookie.from("oauthState", "")
				.httpOnly(true)
				.secure(authProperties.jwt().cookieSecure())
				.path("/api/v1/auth/oauth")
				.sameSite(oauthStateCookieSameSite())
				.maxAge(Duration.ZERO)
				.build();
	}

	private String oauthStateCookieSameSite() {
		return authProperties.jwt().cookieSecure() ? "None" : "Lax";
	}

	private ResponseCookie expireRefreshTokenCookie() {
		return ResponseCookie.from("refreshToken", "")
				.httpOnly(true)
				.secure(true)
				.path("/")
				.sameSite("None")
				.maxAge(Duration.ZERO)
				.build();
	}
}
