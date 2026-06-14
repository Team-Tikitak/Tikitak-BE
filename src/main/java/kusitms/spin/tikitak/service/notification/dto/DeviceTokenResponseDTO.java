package kusitms.spin.tikitak.service.notification.dto;

import kusitms.spin.tikitak.domain.notification.enums.DevicePlatform;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class DeviceTokenResponseDTO {

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class RegisterResponseDTO {
		private Long deviceTokenId;
		private DevicePlatform platform;
	}
}
