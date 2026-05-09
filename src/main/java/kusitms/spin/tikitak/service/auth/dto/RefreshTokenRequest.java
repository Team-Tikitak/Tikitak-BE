package kusitms.spin.tikitak.service.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "액세스 토큰 재발급 요청")
public record RefreshTokenRequest(
		@Schema(description = "JWT refresh token", example = "jwt-refresh-token")
		String refreshToken
) {
}
