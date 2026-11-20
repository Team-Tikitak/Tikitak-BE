package kusitms.spin.tikitak.service.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import kusitms.spin.tikitak.global.config.AuthProperties;
import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class KakaoOAuthServiceTest extends UnitTest {

	private final KakaoOAuthService kakaoOAuthService = new KakaoOAuthService(
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
	@DisplayName("Kakao OAuth HttpClient는 connect timeout 3초를 가진다")
	void httpClientHasConnectTimeout() {
		assertThat(kakaoOAuthService.httpClient.connectTimeout()).contains(Duration.ofSeconds(3));
	}

	@Test
	@DisplayName("Kakao 토큰 요청은 request timeout 5초를 가진다")
	void tokenRequestHasRequestTimeout() {
		HttpRequest request = kakaoOAuthService.buildTokenRequest("auth-code");

		assertThat(request.uri().toString()).isEqualTo("https://kauth.kakao.com/oauth/token");
		assertThat(request.timeout()).contains(Duration.ofSeconds(5));
	}

	@Test
	@DisplayName("Kakao 사용자 정보 요청은 request timeout 5초를 가진다")
	void userInfoRequestHasRequestTimeout() {
		HttpRequest request = kakaoOAuthService.buildUserInfoRequest("access-token");

		assertThat(request.uri().toString()).isEqualTo("https://kapi.kakao.com/v2/user/me");
		assertThat(request.timeout()).contains(Duration.ofSeconds(5));
	}
}
