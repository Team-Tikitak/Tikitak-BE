package kusitms.spin.tikitak.controller.team;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kusitms.spin.tikitak.global.dto.CommonResponse;
import kusitms.spin.tikitak.global.security.CurrentMemberId;
import kusitms.spin.tikitak.service.team.TeamService;
import kusitms.spin.tikitak.service.team.dto.TeamRequestDTO;
import kusitms.spin.tikitak.service.team.dto.TeamResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "팀 컨트롤러")
@RestController
@RequestMapping("/api/v1/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    @PostMapping
    @Operation(summary = "팀 생성 API")
    public CommonResponse<TeamResponseDTO.TeamCreateResponseDTO> createTeam(
            @Parameter(hidden = true) @CurrentMemberId Long memberId,
            @RequestBody TeamRequestDTO.TeamCreateRequestDTO request
    ) {
        TeamResponseDTO.TeamCreateResponseDTO teamCreateDTO = teamService.createTeam(memberId, request);
        return CommonResponse.success(teamCreateDTO);
    }

    @GetMapping("/{teamId}")
    @Operation(summary = "팀 상세 조회 API")
    public CommonResponse<TeamResponseDTO.TeamDetailResponseDTO> viewTeam(
            @Parameter(hidden = true) @CurrentMemberId Long memberId,
            @Valid @PathVariable Long teamId
    ) {
        TeamResponseDTO.TeamDetailResponseDTO teamDetailResponseDTO = teamService.viewTeamDetail(memberId, teamId);
        return CommonResponse.success(teamDetailResponseDTO);
    }

    @PatchMapping("/{teamId}")
    @Operation(summary = "팀 수정 API")
    public CommonResponse<TeamResponseDTO.TeamUpdateResponseDTO> editTeam(
            @Parameter(hidden = true) @CurrentMemberId Long memberId,
            @PathVariable Long teamId,
            @RequestBody TeamRequestDTO.TeamUpdateRequestDTO request
    ) {
        TeamResponseDTO.TeamUpdateResponseDTO response = teamService.updateTeam(memberId, teamId, request);
        return CommonResponse.success(response);
    }

}
