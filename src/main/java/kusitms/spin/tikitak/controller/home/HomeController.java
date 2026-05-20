package kusitms.spin.tikitak.controller.home;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import kusitms.spin.tikitak.global.dto.CommonResponse;
import kusitms.spin.tikitak.global.security.CurrentMemberId;
import kusitms.spin.tikitak.service.home.HomeService;
import kusitms.spin.tikitak.service.home.dto.HomeResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "홈 컨트롤러")
@RestController
@RequestMapping("/api/v1/teams/{teamId}/home")
@RequiredArgsConstructor
public class HomeController {

	private final HomeService homeService;

	@GetMapping("/best-attendance")
	@Operation(summary = "이달의 Best 출석 조회 API")
	public CommonResponse<HomeResponseDTO.BestAttendanceResponse> getBestAttendance(
			@Parameter(hidden = true) @CurrentMemberId Long memberId,
			@PathVariable Long teamId
	) {
		return CommonResponse.success(homeService.getBestAttendance(memberId, teamId));
	}

	@GetMapping("/everyone-pick")
	@Operation(summary = "모두의 PICK 조회 API")
	public CommonResponse<HomeResponseDTO.EveryonePickResponse> getEveryonePick(
			@Parameter(hidden = true) @CurrentMemberId Long memberId,
			@PathVariable Long teamId
	) {
		return CommonResponse.success(homeService.getEveryonePick(memberId, teamId));
	}
}
