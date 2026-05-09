package kusitms.spin.tikitak.service.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoOAuthTokenResponse(
		@JsonProperty("access_token")
		String accessToken,
		@JsonProperty("token_type")
		String tokenType,
		@JsonProperty("refresh_token")
		String refreshToken,
		@JsonProperty("expires_in")
		Long expiresIn,
		String scope,
		@JsonProperty("refresh_token_expires_in")
		Long refreshTokenExpiresIn
) {
}
