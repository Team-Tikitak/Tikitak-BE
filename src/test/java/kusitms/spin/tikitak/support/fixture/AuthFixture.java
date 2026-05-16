package kusitms.spin.tikitak.support.fixture;

import kusitms.spin.tikitak.domain.member.enums.SocialProvider;
import kusitms.spin.tikitak.service.auth.dto.OAuthUserInfo;
import kusitms.spin.tikitak.service.auth.dto.TokenResponse;

public final class AuthFixture {

	private AuthFixture() {
	}

	public static OAuthUserInfo kakaoUserInfo() {
		return new OAuthUserInfo(
				SocialProvider.KAKAO,
				"kakao-provider-id",
				"user@example.com",
				"Kakao User",
				"https://example.com/profile.png"
		);
	}

	public static TokenResponse tokenResponse() {
		return new TokenResponse(
				"access-token",
				"refresh-token",
				"Bearer",
				3600L
		);
	}
}
