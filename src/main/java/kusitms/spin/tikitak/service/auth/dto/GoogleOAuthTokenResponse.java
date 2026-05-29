package kusitms.spin.tikitak.service.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GoogleOAuthTokenResponse(
		@JsonProperty("access_token")
		String accessToken,
		@JsonProperty("expires_in")
		Long expiresIn,
		@JsonProperty("refresh_token")
		String refreshToken,
		String scope,
		@JsonProperty("token_type")
		String tokenType,
		@JsonProperty("id_token")
		String idToken
) {
}
