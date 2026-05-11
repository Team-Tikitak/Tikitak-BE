package kusitms.spin.tikitak.service.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import kusitms.spin.tikitak.domain.member.enums.SocialProvider;
import kusitms.spin.tikitak.global.config.AuthProperties;
import kusitms.spin.tikitak.service.auth.dto.OAuthAuthorizeUrlResponse;
import kusitms.spin.tikitak.service.auth.dto.OAuthUserInfo;
import kusitms.spin.tikitak.service.auth.exception.OAuthAuthenticationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppleOAuthService {

	private static final String AUTHORIZATION_URI = "https://appleid.apple.com/auth/authorize";
	private static final String ISSUER = "https://appleid.apple.com";
	private static final String AUDIENCE = "com.tikitak";

	private final AuthProperties authProperties;
	private final ObjectMapper objectMapper;

	public OAuthAuthorizeUrlResponse getAuthorizeUrl(String state) {
		AuthProperties.Apple apple = authProperties.oauth().apple();
		validateAppleClientConfig(apple, false);
		
		URI authorizeUrl = UriComponentsBuilder.fromUriString(AUTHORIZATION_URI)
				.queryParam("client_id", apple.clientId())
				.queryParam("redirect_uri", apple.redirectUri())
				.queryParam("response_type", "code id_token")
				.queryParam("scope", "name email")
				.queryParam("response_mode", "form_post")
				.queryParam("state", state)
				.build()
				.encode()
				.toUri();

		return new OAuthAuthorizeUrlResponse(authorizeUrl, state);
	}

	public OAuthUserInfo getUserInfo(String code, String idToken) {
		// id_token 검증
		AppleIdTokenPayload idTokenPayload = validateIdToken(idToken);
		
		if (idTokenPayload.sub() == null || idTokenPayload.sub().isBlank()) {
			throw new OAuthAuthenticationException("Apple provider id is empty.");
		}

		return new OAuthUserInfo(
				SocialProvider.APPLE,
				idTokenPayload.sub(),
				idTokenPayload.email(),
				idTokenPayload.getDisplayName(),
				null
		);
	}

	private AppleIdTokenPayload validateIdToken(String idToken) {
		try {
			// JWT의 payload 부분 추출 및 디코딩
			String[] parts = idToken.split("\\.");
			if (parts.length != 3) {
				throw new OAuthAuthenticationException("Invalid id_token format.");
			}

			String payload = new String(
					Base64.getDecoder().decode(parts[1]),
					StandardCharsets.UTF_8
			);
			
			AppleIdTokenPayload idTokenPayload = objectMapper.readValue(payload, AppleIdTokenPayload.class);
			
			// issuer 검증
			if (!ISSUER.equals(idTokenPayload.iss())) {
				throw new OAuthAuthenticationException("Invalid id_token issuer.");
			}
			
			// audience 검증
			if (!AUDIENCE.equals(idTokenPayload.aud())) {
				throw new OAuthAuthenticationException("Invalid id_token audience.");
			}
			
			// 만료 시간 검증
			long currentTime = Instant.now().getEpochSecond();
			if (currentTime > idTokenPayload.exp()) {
				throw new OAuthAuthenticationException("id_token is expired.");
			}
			
			// exp - iat 차이가 너무 크면 거부 (일반적으로 10분 이내)
			if (idTokenPayload.exp() - idTokenPayload.iat() > 600) {
				throw new OAuthAuthenticationException("id_token has invalid expiration time.");
			}
			
			return idTokenPayload;
		} catch (OAuthAuthenticationException e) {
			throw e;
		} catch (IOException e) {
			log.warn("Apple id_token parsing failed. reason={}", e.getMessage());
			throw new OAuthAuthenticationException("Apple id_token parsing failed.", e);
		} catch (Exception e) {
			log.warn("Apple id_token validation failed. reason={}", e.getMessage());
			throw new OAuthAuthenticationException("Apple id_token validation failed.", e);
		}
	}

	private void validateAppleClientConfig(AuthProperties.Apple apple, boolean requirePrivateKey) {
		if (apple == null || isBlank(apple.clientId()) || isBlank(apple.redirectUri())) {
			throw new OAuthAuthenticationException("Apple OAuth client-id and redirect-uri must be configured.");
		}
		if (isBlank(apple.teamId()) || isBlank(apple.keyId())) {
			throw new OAuthAuthenticationException("Apple OAuth team-id and key-id must be configured.");
		}
		if (requirePrivateKey && isBlank(apple.privateKey())) {
			throw new OAuthAuthenticationException("Apple OAuth private-key must be configured.");
		}
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record AppleIdTokenPayload(
			@JsonProperty("iss")
			String iss,
			@JsonProperty("aud")
			String aud,
			@JsonProperty("iat")
			long iat,
			@JsonProperty("exp")
			long exp,
			@JsonProperty("sub")
			String sub,
			@JsonProperty("email")
			String email,
			@JsonProperty("email_verified")
			boolean emailVerified,
			@JsonProperty("nonce")
			String nonce,
			@JsonProperty("nonce_supported")
			boolean nonceSupported,
			@JsonProperty("is_private_email")
			boolean isPrivateEmail
	) {
		public String getDisplayName() {
			// Apple에서는 email 또는 처리된 email 주소 반환
			if (isPrivateEmail() && email != null) {
				// 프라이빗 이메일인 경우 uuid 형태의 이메일
				return email.split("@")[0];
			}
			return email;
		}
	}
}
