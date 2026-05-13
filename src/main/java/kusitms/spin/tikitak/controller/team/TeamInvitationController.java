package kusitms.spin.tikitak.controller.team;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import kusitms.spin.tikitak.global.dto.CommonResponse;
import kusitms.spin.tikitak.global.security.CurrentMemberId;
import kusitms.spin.tikitak.service.team.TeamInvitationService;
import kusitms.spin.tikitak.service.team.dto.TeamInvitationResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "팀 초대 컨트롤러")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TeamInvitationController {

	private final TeamInvitationService teamInvitationService;

	@PutMapping("/teams/{teamId}/invitation-link")
	@Operation(summary = "초대 링크 생성/재발급 API", description = "팀장만 사용할 수 있습니다.")
	public CommonResponse<TeamInvitationResponseDTO.GenerateInviteLinkResponseDTO> generateOrReissueInviteLink(
			@Parameter(hidden = true) @CurrentMemberId Long memberId,
			@PathVariable Long teamId
	) {
		TeamInvitationResponseDTO.GenerateInviteLinkResponseDTO response =
				teamInvitationService.generateOrReissueInviteLink(memberId, teamId);
		return CommonResponse.success(response);
	}

	@GetMapping("/teams/{teamId}/invitation-link")
	@Operation(summary = "현재 활성 초대 링크 조회 API", description = "팀장만 사용할 수 있습니다.")
	public CommonResponse<TeamInvitationResponseDTO.GenerateInviteLinkResponseDTO> getActiveInviteLink(
			@Parameter(hidden = true) @CurrentMemberId Long memberId,
			@PathVariable Long teamId
	) {
		TeamInvitationResponseDTO.GenerateInviteLinkResponseDTO response =
				teamInvitationService.getActiveInviteLink(memberId, teamId);
		return CommonResponse.success(response);
	}
}
