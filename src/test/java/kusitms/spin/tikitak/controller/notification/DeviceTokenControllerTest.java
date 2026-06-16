package kusitms.spin.tikitak.controller.notification;

import kusitms.spin.tikitak.domain.notification.enums.DevicePlatform;
import kusitms.spin.tikitak.service.notification.DeviceTokenService;
import kusitms.spin.tikitak.service.notification.dto.DeviceTokenRequestDTO;
import kusitms.spin.tikitak.service.notification.dto.DeviceTokenResponseDTO;
import kusitms.spin.tikitak.support.ApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DeviceTokenControllerTest extends ApiTest {

	@Mock
	private DeviceTokenService deviceTokenService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = mockMvc(new DeviceTokenController(deviceTokenService));
	}

	@Test
	@DisplayName("POST /api/v1/me/device-tokens는 디바이스 토큰 등록 결과를 반환한다")
	void registerDeviceToken() throws Exception {
		when(deviceTokenService.registerToken(eq(TEST_MEMBER_ID), any(DeviceTokenRequestDTO.RegisterRequestDTO.class)))
				.thenReturn(DeviceTokenResponseDTO.RegisterResponseDTO.builder()
						.deviceTokenId(10L)
						.platform(DevicePlatform.ANDROID)
						.build());

		mockMvc.perform(post("/api/v1/me/device-tokens")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "fcmToken": "token-1",
								  "platform": "ANDROID"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.status").value(200))
				.andExpect(jsonPath("$.data.deviceTokenId").value(10L))
				.andExpect(jsonPath("$.data.platform").value("ANDROID"));
	}

	@Test
	@DisplayName("POST /api/v1/me/device-tokens는 fcmToken이 없으면 400을 반환한다")
	void registerDeviceTokenReturnsBadRequestWhenFcmTokenMissing() throws Exception {
		mockMvc.perform(post("/api/v1/me/device-tokens")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "platform": "ANDROID"
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.status").value(400));
	}

	@Test
	@DisplayName("POST /api/v1/me/device-tokens는 지원하지 않는 platform이면 400을 반환한다")
	void registerDeviceTokenReturnsBadRequestWhenPlatformInvalid() throws Exception {
		mockMvc.perform(post("/api/v1/me/device-tokens")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "fcmToken": "token-1",
								  "platform": "WINDOWS"
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.status").value(400));
	}

	@Test
	@DisplayName("DELETE /api/v1/me/device-tokens는 디바이스 토큰을 해제한다")
	void unregisterDeviceToken() throws Exception {
		mockMvc.perform(delete("/api/v1/me/device-tokens")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "fcmToken": "token-1"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.status").value(200));

		verify(deviceTokenService).unregisterToken(
				eq(TEST_MEMBER_ID),
				any(DeviceTokenRequestDTO.UnregisterRequestDTO.class)
		);
	}

	@Test
	@DisplayName("DELETE /api/v1/me/device-tokens는 fcmToken이 없으면 400을 반환한다")
	void unregisterDeviceTokenReturnsBadRequestWhenFcmTokenMissing() throws Exception {
		mockMvc.perform(delete("/api/v1/me/device-tokens")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.status").value(400));
	}
}
