package kusitms.spin.tikitak.service.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "loginCode 교환 요청")
public record LoginCodeExchangeRequest(
		@NotBlank
		@Schema(description = "OAuth 콜백에서 발급된 1회용 loginCode", example = "a3f9b2c1...")
		String loginCode
) {
}
