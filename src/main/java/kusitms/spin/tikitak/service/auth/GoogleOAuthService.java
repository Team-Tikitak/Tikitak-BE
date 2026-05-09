package kusitms.spin.tikitak.service.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import kusitms.spin.tikitak.domain.member.enums.SocialProvider;
import kusitms.spin.tikitak.global.config.AuthProperties;
import kusitms.spin.tikitak.service.auth.dto.GoogleAuthorizeUrlResponse;
import kusitms.spin.tikitak.service.auth.dto.GoogleOAuthTokenResponse;
import kusitms.spin.tikitak.service.auth.dto.OAuthUserInfo;
import kusitms.spin.tikitak.service.auth.exception.OAuthAuthenticationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleOAuthService {

	private static final String AUTHORIZATION_URI = "https://accounts.google.com/o/oauth2/v2/auth";
	private static final String TOKEN_URI = "https://oauth2.googleapis.com/token";
	private static final String USER_INFO_URI = "https://www.googleapis.com/oauth2/v3/userinfo";

	private final AuthProperties authProperties;
	private final ObjectMapper objectMapper;
	private final HttpClient httpClient = HttpClient.newHttpClient();

	public GoogleAuthorizeUrlResponse getAuthorizeUrl(String state) {
		AuthProperties.Google google = authProperties.oauth().google();
		validateGoogleClientConfig(google, false);
		URI authorizeUrl = UriComponentsBuilder.fromUriString(AUTHORIZATION_URI)
				.queryParam("client_id", google.clientId())
				.queryParam("redirect_uri", google.redirectUri())
				.queryParam("response_type", "code")
				.queryParam("scope", "openid email profile")
				.queryParam("state", state)
				.queryParam("access_type", "offline")
				.queryParam("prompt", "select_account")
				.build()
				.encode()
				.toUri();

		return new GoogleAuthorizeUrlResponse(authorizeUrl, state);
	}

	public OAuthUserInfo getUserInfo(String code) {
		GoogleOAuthTokenResponse tokenResponse = requestToken(code);
		if (tokenResponse.accessToken() == null || tokenResponse.accessToken().isBlank()) {
			throw new OAuthAuthenticationException("Google access token is empty.");
		}

		GoogleUserInfoResponse userInfo = requestGoogleUserInfo(tokenResponse.accessToken());
		if (userInfo.sub() == null || userInfo.sub().isBlank()) {
			throw new OAuthAuthenticationException("Google provider id is empty.");
		}

		return new OAuthUserInfo(
				SocialProvider.GOOGLE,
				userInfo.sub(),
				userInfo.email(),
				userInfo.name(),
				userInfo.picture()
		);
	}

	private GoogleOAuthTokenResponse requestToken(String code) {
		AuthProperties.Google google = authProperties.oauth().google();
		validateGoogleClientConfig(google, true);
		String body = formParam("code", code)
				+ "&" + formParam("client_id", google.clientId())
				+ "&" + formParam("client_secret", google.clientSecret())
				+ "&" + formParam("redirect_uri", google.redirectUri())
				+ "&" + formParam("grant_type", "authorization_code");

		HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_URI))
				.header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded;charset=UTF-8")
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build();

		return send(request, GoogleOAuthTokenResponse.class);
	}

	private GoogleUserInfoResponse requestGoogleUserInfo(String accessToken) {
		HttpRequest request = HttpRequest.newBuilder(URI.create(USER_INFO_URI))
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.GET()
				.build();

		return send(request, GoogleUserInfoResponse.class);
	}

	private <T> T send(HttpRequest request, Class<T> responseType) {
		try {
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				log.warn(
						"Google OAuth request failed. uri={}, status={}, body={}",
						request.uri(),
						response.statusCode(),
						truncate(response.body())
				);
				throw new OAuthAuthenticationException("Google OAuth request failed. status=" + response.statusCode());
			}
			return objectMapper.readValue(response.body(), responseType);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new OAuthAuthenticationException("Google OAuth request interrupted.", e);
		} catch (IOException e) {
			log.warn("Google OAuth response parsing failed. uri={}, reason={}", request.uri(), e.getMessage());
			throw new OAuthAuthenticationException("Google OAuth response parsing failed.", e);
		}
	}

	private String formParam(String name, String value) {
		return URLEncoder.encode(name, StandardCharsets.UTF_8)
				+ "="
				+ URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private void validateGoogleClientConfig(AuthProperties.Google google, boolean requireSecret) {
		if (google == null || isBlank(google.clientId()) || isBlank(google.redirectUri())) {
			throw new OAuthAuthenticationException("Google OAuth client-id and redirect-uri must be configured.");
		}
		if (requireSecret && isBlank(google.clientSecret())) {
			throw new OAuthAuthenticationException("Google OAuth client-secret must be configured.");
		}
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private String truncate(String value) {
		if (value == null || value.length() <= 1000) {
			return value;
		}
		return value.substring(0, 1000);
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record GoogleUserInfoResponse(
			String sub,
			String email,
			String name,
			@JsonProperty("picture")
			String picture
	) {
	}
}
