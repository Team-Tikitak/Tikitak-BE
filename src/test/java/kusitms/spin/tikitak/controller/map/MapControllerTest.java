package kusitms.spin.tikitak.controller.map;

import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.service.map.MapService;
import kusitms.spin.tikitak.service.map.dto.MapResponseDTO;
import kusitms.spin.tikitak.support.ApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MapControllerTest extends ApiTest {

	private static final Long TEAM_ID = 10L;

	@Mock
	private MapService mapService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = mockMvc(new MapController(mapService));
	}

	@Test
	@DisplayName("GET /api/v1/teams/{teamId}/map/pins는 지도 핀 목록을 반환한다")
	void getMapPins() throws Exception {
		when(mapService.getMapPins(TEST_MEMBER_ID, TEAM_ID))
				.thenReturn(MapResponseDTO.MapPinListResponseDTO.builder()
						.pins(List.of(
								MapResponseDTO.MapPinDTO.builder()
										.placeId("kakao-001")
										.name("카페A")
										.latitude(new BigDecimal("37.500000"))
										.longitude(new BigDecimal("127.000000"))
										.address("서울시 성동구")
										.thumbnailUrl("https://example.com/thumb1.jpg")
										.heroPreviewUrl("https://example.com/hero-preview1.jpg")
										.feedCount(3)
										.build(),
								MapResponseDTO.MapPinDTO.builder()
										.placeId("kakao-002")
										.name("카페B")
										.latitude(new BigDecimal("37.510000"))
										.longitude(new BigDecimal("127.010000"))
										.address("서울시 마포구")
										.thumbnailUrl("https://example.com/thumb2.jpg")
										.heroPreviewUrl("https://example.com/hero-preview2.jpg")
										.feedCount(1)
										.build()
						))
						.build());

		mockMvc.perform(get("/api/v1/teams/{teamId}/map/pins", TEAM_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.status").value(200))
				.andExpect(jsonPath("$.data.pins").isArray())
				.andExpect(jsonPath("$.data.pins.length()").value(2))
				.andExpect(jsonPath("$.data.pins[0].placeId").value("kakao-001"))
				.andExpect(jsonPath("$.data.pins[0].name").value("카페A"))
				.andExpect(jsonPath("$.data.pins[0].thumbnailUrl").value("https://example.com/thumb1.jpg"))
				.andExpect(jsonPath("$.data.pins[0].heroPreviewUrl").value("https://example.com/hero-preview1.jpg"))
				.andExpect(jsonPath("$.data.pins[0].feedCount").value(3))
				.andExpect(jsonPath("$.data.pins[1].placeId").value("kakao-002"))
				.andExpect(jsonPath("$.data.pins[1].heroPreviewUrl").value("https://example.com/hero-preview2.jpg"))
				.andExpect(jsonPath("$.data.pins[1].feedCount").value(1));

		verify(mapService).getMapPins(TEST_MEMBER_ID, TEAM_ID);
	}

	@Test
	@DisplayName("GET /api/v1/teams/{teamId}/map/pins는 피드가 없으면 빈 핀 목록을 반환한다")
	void getMapPinsReturnsEmptyList() throws Exception {
		when(mapService.getMapPins(TEST_MEMBER_ID, TEAM_ID))
				.thenReturn(MapResponseDTO.MapPinListResponseDTO.builder()
						.pins(List.of())
						.build());

		mockMvc.perform(get("/api/v1/teams/{teamId}/map/pins", TEAM_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.pins").isArray())
				.andExpect(jsonPath("$.data.pins").isEmpty());

		verify(mapService).getMapPins(TEST_MEMBER_ID, TEAM_ID);
	}

	@Test
	@DisplayName("존재하지 않는 팀이면 404를 반환한다")
	void getMapPinsReturns404WhenTeamNotFound() throws Exception {
		when(mapService.getMapPins(TEST_MEMBER_ID, TEAM_ID))
				.thenThrow(new BusinessException(ErrorCode.TEAM009));

		mockMvc.perform(get("/api/v1/teams/{teamId}/map/pins", TEAM_ID))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.code").value("TEAM009"));
	}

	@Test
	@DisplayName("팀 멤버가 아니면 403을 반환한다")
	void getMapPinsReturns403WhenNotTeamMember() throws Exception {
		when(mapService.getMapPins(TEST_MEMBER_ID, TEAM_ID))
				.thenThrow(new BusinessException(ErrorCode.TEAM008));

		mockMvc.perform(get("/api/v1/teams/{teamId}/map/pins", TEAM_ID))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.code").value("TEAM008"));
	}
}
