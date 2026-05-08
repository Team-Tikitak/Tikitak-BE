package kusitms.spin.tikitak.service.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.net.URI;

@Schema(description = "Google OAuth 인증 URL 생성 결과")
public record GoogleAuthorizeUrlResponse(
		@Schema(description = "Google 인증 페이지 URL")
		URI authorizeUrl,
		@Schema(description = "OAuth 요청 검증용 state")
		String state
) {
}
