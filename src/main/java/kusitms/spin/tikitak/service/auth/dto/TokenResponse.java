package kusitms.spin.tikitak.service.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "토큰 응답")
public record TokenResponse(
		@Schema(description = "JWT access token", example = "jwt-access-token")
		String accessToken,
		@Schema(description = "JWT refresh token", example = "jwt-refresh-token")
		String refreshToken,
		@Schema(description = "토큰 타입", example = "Bearer")
		String tokenType,
		@Schema(description = "access token 만료 시간(초)", example = "3600")
		long expiresIn
) {
}
