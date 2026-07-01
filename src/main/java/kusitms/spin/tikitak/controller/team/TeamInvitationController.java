package kusitms.spin.tikitak.controller.team;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kusitms.spin.tikitak.global.dto.CommonResponse;
import kusitms.spin.tikitak.global.security.CurrentMemberId;
import kusitms.spin.tikitak.service.team.TeamInvitationService;
import kusitms.spin.tikitak.service.team.dto.TeamInvitationRequestDTO;
import kusitms.spin.tikitak.service.team.dto.TeamInvitationResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "팀 초대 컨트롤러")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TeamInvitationController {

	private final TeamInvitationService teamInvitationService;

	@PutMapping("/teams/{teamId}/invitation-link")
	@Operation(summary = "초대 링크 생성/재발급 API", description = "팀장 및 팀원 모두 사용할 수 있습니다.")
	public CommonResponse<TeamInvitationResponseDTO.GenerateInviteLinkResponseDTO> generateOrReissueInviteLink(
			@Parameter(hidden = true) @CurrentMemberId Long memberId,
			@PathVariable Long teamId
	) {
		TeamInvitationResponseDTO.GenerateInviteLinkResponseDTO response =
				teamInvitationService.generateOrReissueInviteLink(memberId, teamId);
		return CommonResponse.success(response);
	}

	@GetMapping("/teams/{teamId}/invitation-link")
	@Operation(summary = "현재 활성 초대 링크 조회 API", description = "팀장 및 팀원 모두 사용할 수 있습니다.")
	public CommonResponse<TeamInvitationResponseDTO.ActiveInviteLinkResponseDTO> getActiveInviteLink(
			@Parameter(hidden = true) @CurrentMemberId Long memberId,
			@PathVariable Long teamId
	) {
		TeamInvitationResponseDTO.ActiveInviteLinkResponseDTO response =
				teamInvitationService.getActiveInviteLink(memberId, teamId);
		return CommonResponse.success(response);
	}

	@GetMapping("/invitation-links/{token}")
	@Operation(summary = "초대 링크 유효성/미리보기 조회 API", description = "비로그인 사용자도 접근 가능합니다.")
	public CommonResponse<TeamInvitationResponseDTO.InviteLinkPreviewResponseDTO> getInviteLinkPreview(
			@PathVariable String token
	) {
		TeamInvitationResponseDTO.InviteLinkPreviewResponseDTO response =
				teamInvitationService.getInviteLinkPreview(token);
		return CommonResponse.success(response);
	}

	@PostMapping("/invitation-links/{token}/accept")
	@Operation(summary = "초대 링크 수락 및 팀 참여 API")
	public CommonResponse<TeamInvitationResponseDTO.JoinTeamResponseDTO> acceptInviteLink(
			@Parameter(hidden = true) @CurrentMemberId Long memberId,
			@PathVariable String token,
			@Valid @RequestBody TeamInvitationRequestDTO.JoinTeamRequestDTO request
	) {
		TeamInvitationResponseDTO.JoinTeamResponseDTO response =
				teamInvitationService.acceptInviteLink(memberId, token, request);
		return CommonResponse.success(response);
	}
}
