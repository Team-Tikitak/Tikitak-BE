package kusitms.spin.tikitak.service.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import kusitms.spin.tikitak.domain.notification.enums.DevicePlatform;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class DeviceTokenRequestDTO {

	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class RegisterRequestDTO {
		@NotBlank
		private String fcmToken;

		@NotNull
		private DevicePlatform platform;
	}

	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class UnregisterRequestDTO {
		@NotBlank
		private String fcmToken;
	}
}
