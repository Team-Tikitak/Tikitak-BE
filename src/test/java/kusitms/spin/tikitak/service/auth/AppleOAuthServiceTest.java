package kusitms.spin.tikitak.service.auth;

import kusitms.spin.tikitak.global.config.AuthProperties;
import kusitms.spin.tikitak.service.auth.dto.OAuthUserInfo;
import kusitms.spin.tikitak.service.auth.exception.OAuthAuthenticationException;
import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AppleOAuthServiceTest extends UnitTest {

	private static final String CLIENT_ID = "apple-client-id";
	private static final String ID_TOKEN = "apple-id-token";

	@Mock
	private JwtDecoder jwtDecoder;

	@Test
	@DisplayName("getUserInfo를 두 번 호출하면 같은 JwtDecoder를 재사용한다")
	void reusesSameDecoderAcrossCalls() {
		AppleOAuthService appleOAuthService = new AppleOAuthService(authProperties(), jwtDecoder);
		given(jwtDecoder.decode(ID_TOKEN)).willReturn(validJwt());

		OAuthUserInfo first = appleOAuthService.getUserInfo("code-1", ID_TOKEN);
		OAuthUserInfo second = appleOAuthService.getUserInfo("code-2", ID_TOKEN);

		verify(jwtDecoder, times(2)).decode(ID_TOKEN);
		assertThat(first.providerId()).isEqualTo("apple-sub");
		assertThat(second.providerId()).isEqualTo("apple-sub");
	}

	@Test
	@DisplayName("JwtDecoder가 JwtException을 던지면 OAuthAuthenticationException으로 바꾼다")
	void wrapsJwtException() {
		AppleOAuthService appleOAuthService = new AppleOAuthService(authProperties(), jwtDecoder);
		given(jwtDecoder.decode(ID_TOKEN)).willThrow(new JwtException("jwks fetch failed"));

		assertThatThrownBy(() -> appleOAuthService.getUserInfo("code", ID_TOKEN))
				.isInstanceOf(OAuthAuthenticationException.class)
				.hasCauseInstanceOf(JwtException.class);
	}

	@Test
	@DisplayName("Spring용 생성자는 JwtDecoder를 생성 시점에 한 번 만든다")
	void springConstructorCreatesDecoder() {
		AppleOAuthService appleOAuthService = new AppleOAuthService(authProperties());

		assertThat(appleOAuthService.jwtDecoder).isNotNull();
	}

	private Jwt validJwt() {
		Instant now = Instant.now();
		return Jwt.withTokenValue(ID_TOKEN)
				.header("alg", "RS256")
				.issuer("https://appleid.apple.com")
				.audience(List.of(CLIENT_ID))
				.subject("apple-sub")
				.claim("email", "user@example.com")
				.issuedAt(now)
				.expiresAt(now.plusSeconds(600))
				.build();
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
								CLIENT_ID,
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
						false,
						null
				)
		);
	}
}
