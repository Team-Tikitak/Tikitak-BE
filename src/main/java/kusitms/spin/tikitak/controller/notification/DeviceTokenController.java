package kusitms.spin.tikitak.controller.notification;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kusitms.spin.tikitak.global.dto.CommonResponse;
import kusitms.spin.tikitak.global.security.CurrentMemberId;
import kusitms.spin.tikitak.service.notification.DeviceTokenService;
import kusitms.spin.tikitak.service.notification.dto.DeviceTokenRequestDTO;
import kusitms.spin.tikitak.service.notification.dto.DeviceTokenResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "DeviceToken", description = "푸시 알림 디바이스 토큰 관리 API")
public class DeviceTokenController {

	private final DeviceTokenService deviceTokenService;

	@Operation(
			summary = "디바이스 토큰 등록/갱신",
			description = "현재 로그인한 사용자의 FCM 디바이스 토큰을 등록하거나 갱신합니다. 이미 등록된 토큰이면 소유자와 플랫폼 정보를 갱신합니다.",
			security = @SecurityRequirement(name = "bearerAuth")
	)
	@PostMapping("/api/v1/me/device-tokens")
	public CommonResponse<DeviceTokenResponseDTO.RegisterResponseDTO> registerDeviceToken(
			@Parameter(hidden = true) @CurrentMemberId Long memberId,
			@Valid @RequestBody DeviceTokenRequestDTO.RegisterRequestDTO request
	) {
		return CommonResponse.success(deviceTokenService.registerToken(memberId, request));
	}

	@Operation(
			summary = "디바이스 토큰 해제",
			description = "현재 로그인한 사용자의 FCM 디바이스 토큰을 해제합니다. 로그아웃 시 호출합니다.",
			security = @SecurityRequirement(name = "bearerAuth")
	)
	@DeleteMapping("/api/v1/me/device-tokens")
	public ResponseEntity<CommonResponse<Void>> unregisterDeviceToken(
			@Parameter(hidden = true) @CurrentMemberId Long memberId,
			@Valid @RequestBody DeviceTokenRequestDTO.UnregisterRequestDTO request
	) {
		deviceTokenService.unregisterToken(memberId, request);
		return ResponseEntity.ok(CommonResponse.success(null));
	}
}
