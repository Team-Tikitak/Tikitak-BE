package kusitms.spin.tikitak.service.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import kusitms.spin.tikitak.domain.member.enums.SocialProvider;
import kusitms.spin.tikitak.global.config.AuthProperties;
import kusitms.spin.tikitak.service.auth.dto.KakaoOAuthTokenResponse;
import kusitms.spin.tikitak.service.auth.dto.OAuthAuthorizeUrlResponse;
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
public class KakaoOAuthService {

	private static final String AUTHORIZATION_URI = "https://kauth.kakao.com/oauth/authorize";
	private static final String TOKEN_URI = "https://kauth.kakao.com/oauth/token";
	private static final String USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";
	private static final String SCOPE = "profile_nickname,profile_image,account_email";

	private final AuthProperties authProperties;
	private final ObjectMapper objectMapper;
	private final HttpClient httpClient = HttpClient.newHttpClient();

	public OAuthAuthorizeUrlResponse getAuthorizeUrl(String state) {
		AuthProperties.Kakao kakao = authProperties.oauth().kakao();
		validateKakaoClientConfig(kakao, false);
		URI authorizeUrl = UriComponentsBuilder.fromUriString(AUTHORIZATION_URI)
				.queryParam("client_id", kakao.clientId())
				.queryParam("redirect_uri", kakao.redirectUri())
				.queryParam("response_type", "code")
				.queryParam("scope", SCOPE)
				.queryParam("state", state)
				.build()
				.encode()
				.toUri();

		return new OAuthAuthorizeUrlResponse(authorizeUrl, state);
	}

	public OAuthUserInfo getUserInfo(String code) {
		KakaoOAuthTokenResponse tokenResponse = requestToken(code);
		if (tokenResponse.accessToken() == null || tokenResponse.accessToken().isBlank()) {
			throw new OAuthAuthenticationException("Kakao access token is empty.");
		}

		KakaoUserInfoResponse userInfo = requestKakaoUserInfo(tokenResponse.accessToken());
		if (userInfo.id() == null) {
			throw new OAuthAuthenticationException("Kakao provider id is empty.");
		}

		KakaoAccount account = userInfo.kakaoAccount();
		KakaoProfile profile = account != null ? account.profile() : null;
		return new OAuthUserInfo(
				SocialProvider.KAKAO,
				String.valueOf(userInfo.id()),
				account != null ? account.email() : null,
				profile != null ? profile.nickname() : null,
				profile != null ? profile.profileImageUrl() : null
		);
	}

	private KakaoOAuthTokenResponse requestToken(String code) {
		AuthProperties.Kakao kakao = authProperties.oauth().kakao();
		validateKakaoClientConfig(kakao, true);
		String body = formParam("grant_type", "authorization_code")
				+ "&" + formParam("client_id", kakao.clientId())
				+ "&" + formParam("redirect_uri", kakao.redirectUri())
				+ "&" + formParam("code", code)
				+ "&" + formParam("client_secret", kakao.clientSecret());

		HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_URI))
				.header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded;charset=UTF-8")
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build();

		return send(request, KakaoOAuthTokenResponse.class);
	}

	private KakaoUserInfoResponse requestKakaoUserInfo(String accessToken) {
		HttpRequest request = HttpRequest.newBuilder(URI.create(USER_INFO_URI))
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.GET()
				.build();

		return send(request, KakaoUserInfoResponse.class);
	}

	private <T> T send(HttpRequest request, Class<T> responseType) {
		try {
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				log.warn(
						"Kakao OAuth request failed. uri={}, status={}, body={}",
						request.uri(),
						response.statusCode(),
						truncate(response.body())
				);
				throw new OAuthAuthenticationException("Kakao OAuth request failed. status=" + response.statusCode());
			}
			return objectMapper.readValue(response.body(), responseType);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new OAuthAuthenticationException("Kakao OAuth request interrupted.", e);
		} catch (IOException e) {
			log.warn("Kakao OAuth response parsing failed. uri={}, reason={}", request.uri(), e.getMessage());
			throw new OAuthAuthenticationException("Kakao OAuth response parsing failed.", e);
		}
	}

	private String formParam(String name, String value) {
		return URLEncoder.encode(name, StandardCharsets.UTF_8)
				+ "="
				+ URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private void validateKakaoClientConfig(AuthProperties.Kakao kakao, boolean requireSecret) {
		if (kakao == null || isBlank(kakao.clientId()) || isBlank(kakao.redirectUri())) {
			throw new OAuthAuthenticationException("Kakao OAuth client-id and redirect-uri must be configured.");
		}
		if (requireSecret && isBlank(kakao.clientSecret())) {
			throw new OAuthAuthenticationException("Kakao OAuth client-secret must be configured.");
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
	private record KakaoUserInfoResponse(
			Long id,
			@JsonProperty("kakao_account")
			KakaoAccount kakaoAccount
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record KakaoAccount(
			String email,
			KakaoProfile profile
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record KakaoProfile(
			String nickname,
			@JsonProperty("profile_image_url")
			String profileImageUrl
	) {
	}
}
