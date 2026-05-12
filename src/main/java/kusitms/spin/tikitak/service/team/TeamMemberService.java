package kusitms.spin.tikitak.service.team;

import kusitms.spin.tikitak.domain.team.entity.Team;
import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.repository.team.TeamMemberRepository;
import kusitms.spin.tikitak.repository.team.TeamRepository;
import kusitms.spin.tikitak.service.team.dto.TeamMemberRequestDTO;
import kusitms.spin.tikitak.service.team.dto.TeamMemberResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamMemberService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;

    public TeamMemberResponseDTO.TeamMemberListResponseDTO listTeamMembers(Long memberId, Long teamId) {
        // TeamMember까지 같이 조회
        Team team = teamRepository.findTeamWithTeamMembersById(teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM001));

        // API 호출한 사용자가 해당 팀의 멤버인지 검증
        team.getTeamMembers().stream()
                .filter(tm -> tm.getMember().getId().equals(memberId)
                        && tm.getStatus() == TeamMemberStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM002));

        List<TeamMemberResponseDTO.TeamMemberItemDTO> members = team.getTeamMembers().stream()
                .filter(tm -> tm.getStatus() == TeamMemberStatus.ACTIVE)
                .map(tm -> TeamMemberResponseDTO.TeamMemberItemDTO.builder()
                        .teamMemberId(tm.getId())
                        .nickname(tm.getNickname())
                        .role(tm.getRole())
                        .profileImgUrl(tm.getProfileImgUrl())
                        .build())
                .toList();

        return TeamMemberResponseDTO.TeamMemberListResponseDTO.builder()
                .members(members)
                .build();
    }

    @Transactional
    public TeamMemberResponseDTO.TeamMemberUpdateResponseDTO updateMyProfile(
            Long memberId, Long teamId, TeamMemberRequestDTO.TeamMemberUpdateRequestDTO request
    ) {
        TeamMember teamMember = teamMemberRepository.findByMemberIdAndTeamId(memberId, teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_MEMBER001));

        if (teamMember.getStatus() != TeamMemberStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.TEAM002);
        }

        teamMember.updateProfile(request.getNickname(), request.getProfileImgUrl());

        return TeamMemberResponseDTO.TeamMemberUpdateResponseDTO.builder()
                .nickname(teamMember.getNickname())
                .profileImgUrl(teamMember.getProfileImgUrl())
                .build();
    }
}
