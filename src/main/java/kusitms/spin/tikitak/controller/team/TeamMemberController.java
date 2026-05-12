package kusitms.spin.tikitak.controller.team;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kusitms.spin.tikitak.global.dto.CommonResponse;
import kusitms.spin.tikitak.global.security.CurrentMemberId;
import kusitms.spin.tikitak.service.team.TeamMemberService;
import kusitms.spin.tikitak.service.team.dto.TeamMemberRequestDTO;
import kusitms.spin.tikitak.service.team.dto.TeamMemberResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "팀 멤버 컨트롤러")
@RestController
@RequestMapping("/api/v1/teams/{teamId}/members")
@RequiredArgsConstructor
public class TeamMemberController {

    private final TeamMemberService teamMemberService;

    @GetMapping
    @Operation(summary = "팀 멤버 목록 조회 API")
    public CommonResponse<TeamMemberResponseDTO.TeamMemberListResponseDTO> listTeamMembers(
            @Parameter(hidden = true) @CurrentMemberId Long memberId,
            @PathVariable Long teamId
    ) {
        TeamMemberResponseDTO.TeamMemberListResponseDTO response = teamMemberService.listTeamMembers(memberId, teamId);
        return CommonResponse.success(response);
    }

    @PatchMapping("/me")
    @Operation(summary = "내 팀 프로필 수정 API")
    public CommonResponse<TeamMemberResponseDTO.TeamMemberUpdateResponseDTO> updateMyProfile(
            @Parameter(hidden = true) @CurrentMemberId Long memberId,
            @PathVariable Long teamId,
            @Valid @RequestBody TeamMemberRequestDTO.TeamMemberUpdateRequestDTO request
    ) {
        TeamMemberResponseDTO.TeamMemberUpdateResponseDTO response = teamMemberService.updateMyProfile(memberId, teamId, request);
        return CommonResponse.success(response);
    }

    @DeleteMapping("/me")
    @Operation(summary = "팀 나가기 API")
    public CommonResponse<Void> leaveTeam(
            @Parameter(hidden = true) @CurrentMemberId Long memberId,
            @PathVariable Long teamId
    ) {
        teamMemberService.leaveTeam(memberId, teamId);
        return CommonResponse.success(null);
    }
}
