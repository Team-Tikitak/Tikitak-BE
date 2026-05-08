package kusitms.spin.tikitak.service.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "OAuth 로그인 결과")
public record LoginResponse(
		@Schema(description = "JWT access token", example = "jwt-access-token")
		String accessToken,
		@Schema(description = "JWT refresh token", example = "jwt-refresh-token")
		String refreshToken,
		@Schema(description = "신규 회원 여부", example = "true")
		boolean isNewMember,
		@Schema(description = "필수 약관 동의 완료 여부", example = "false")
		boolean hasAgreedRequiredTerms,
		@Schema(description = "활성 팀 ID", nullable = true, example = "10")
		Long activeTeamId
) {
}
