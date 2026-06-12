package kusitms.spin.tikitak.service.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import kusitms.spin.tikitak.global.config.AuthProperties;
import kusitms.spin.tikitak.service.auth.dto.OAuthAuthorizeUrlResponse;
import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleOAuthServiceTest extends UnitTest {

	private final GoogleOAuthService googleOAuthService = new GoogleOAuthService(
			new AuthProperties(
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
							false,
							null
					)
			),
			new ObjectMapper()
	);

	@Test
	@DisplayName("Google OAuth 인증 URL은 자체 토큰 사용을 위해 offline access를 요청하지 않는다")
	void getAuthorizeUrlDoesNotRequestOfflineAccess() {
		OAuthAuthorizeUrlResponse response = googleOAuthService.getAuthorizeUrl("state");

		String authorizeUrl = response.authorizeUrl().toString();
		assertThat(authorizeUrl).contains("client_id=google-client-id");
		assertThat(authorizeUrl).contains("response_type=code");
		assertThat(authorizeUrl).contains("scope=openid%20email%20profile");
		assertThat(authorizeUrl).doesNotContain("access_type=offline");
	}
}
