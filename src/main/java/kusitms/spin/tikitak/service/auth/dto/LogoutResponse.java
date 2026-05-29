package kusitms.spin.tikitak.service.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그아웃 응답")
public record LogoutResponse(
		@Schema(description = "로그아웃 처리 여부", example = "true")
		boolean loggedOut
) {
}
