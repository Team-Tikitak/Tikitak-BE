package kusitms.spin.tikitak.controller.map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import kusitms.spin.tikitak.global.dto.CommonResponse;
import kusitms.spin.tikitak.global.security.CurrentMemberId;
import kusitms.spin.tikitak.service.map.MapService;
import kusitms.spin.tikitak.service.map.dto.MapResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/teams/{teamId}/map")
@Tag(name = "Map", description = "카카오맵 피드 장소핀 API")
@SecurityRequirement(name = "bearerAuth")
public class MapController {

	private final MapService mapService;

	@GetMapping("/pins")
	@Operation(
			summary = "지도 핀 목록 조회",
			description = "팀의 피드가 등록된 장소를 지도 핀으로 조회합니다. 각 핀은 해당 장소의 최신 피드 썸네일 이미지와 전체 피드 수를 포함합니다."
	)
	public CommonResponse<MapResponseDTO.MapPinListResponseDTO> getMapPins(
			@Parameter(hidden = true) @CurrentMemberId Long memberId,
			@Parameter(description = "팀 ID", required = true) @PathVariable Long teamId
	) {
		return CommonResponse.success(mapService.getMapPins(memberId, teamId));
	}
}
