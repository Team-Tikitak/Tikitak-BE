package kusitms.spin.tikitak.service.auth;

import kusitms.spin.tikitak.domain.member.enums.SocialProvider;
import kusitms.spin.tikitak.global.config.AuthProperties;
import kusitms.spin.tikitak.service.auth.dto.OAuthAuthorizeUrlResponse;
import kusitms.spin.tikitak.service.auth.dto.OAuthUserInfo;
import kusitms.spin.tikitak.service.auth.exception.OAuthAuthenticationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppleOAuthService {

	private static final String AUTHORIZATION_URI = "https://appleid.apple.com/auth/authorize";
	private static final String JWK_SET_URI = "https://appleid.apple.com/auth/keys";
	private static final String ISSUER = "https://appleid.apple.com";

	private final AuthProperties authProperties;

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
		AppleIdTokenPayload idTokenPayload = validateIdToken(idToken);
		
		if (isBlank(idTokenPayload.sub())) {
			throw new OAuthAuthenticationException("Apple provider id is empty.");
		}

		return new OAuthUserInfo(
				SocialProvider.APPLE,
				idTokenPayload.sub(),
				idTokenPayload.email(),
				null,
				null
		);
	}

	private AppleIdTokenPayload validateIdToken(String idToken) {
		try {
			AuthProperties.Apple apple = authProperties.oauth().apple();
			validateAppleClientConfig(apple, false);

			JwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri(JWK_SET_URI).build();
			Jwt jwt = jwtDecoder.decode(idToken);

			if (!ISSUER.equals(jwt.getIssuer() == null ? null : jwt.getIssuer().toString())) {
				throw new OAuthAuthenticationException("Invalid id_token issuer.");
			}
			List<String> audiences = jwt.getAudience();
			if (audiences == null || !audiences.contains(apple.clientId())) {
				throw new OAuthAuthenticationException("Invalid id_token audience.");
			}
			Instant expiresAt = jwt.getExpiresAt();
			if (expiresAt == null || expiresAt.isBefore(Instant.now())) {
				throw new OAuthAuthenticationException("id_token is expired.");
			}

			return new AppleIdTokenPayload(
					jwt.getSubject(),
					jwt.getClaimAsString("email")
			);
		} catch (OAuthAuthenticationException e) {
			throw e;
		} catch (JwtException e) {
			log.warn("Apple id_token validation failed. reason={}", e.getMessage());
			throw new OAuthAuthenticationException("Apple id_token validation failed.", e);
		} catch (Exception e) {
			log.warn("Apple id_token validation failed. reason={}", e.getMessage());
			throw new OAuthAuthenticationException("Apple id_token validation failed.", e);
		}
	}

	private void validateAppleClientConfig(AuthProperties.Apple apple, boolean requirePrivateKey) {
		if (apple == null || isBlank(apple.clientId()) || isBlank(apple.redirectUri())) {
			throw new OAuthAuthenticationException("Apple OAuth client-id and redirect-uri must be configured.");
		}
		if (requirePrivateKey && (isBlank(apple.teamId()) || isBlank(apple.keyId()))) {
			throw new OAuthAuthenticationException("Apple OAuth team-id and key-id must be configured.");
		}
		if (requirePrivateKey && isBlank(apple.privateKey())) {
			throw new OAuthAuthenticationException("Apple OAuth private-key must be configured.");
		}
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private record AppleIdTokenPayload(
			String sub,
			String email
	) {}
}
