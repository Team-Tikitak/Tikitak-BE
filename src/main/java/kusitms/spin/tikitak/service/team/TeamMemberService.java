package kusitms.spin.tikitak.service.team;

import kusitms.spin.tikitak.domain.media.entity.Media;
import kusitms.spin.tikitak.domain.media.enums.MediaPurpose;
import kusitms.spin.tikitak.domain.media.enums.MediaStatus;
import kusitms.spin.tikitak.domain.team.entity.Team;
import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberRole;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.repository.media.MediaRepository;
import kusitms.spin.tikitak.repository.team.TeamMemberRepository;
import kusitms.spin.tikitak.repository.team.TeamRepository;
import kusitms.spin.tikitak.service.me.DefaultProfileImageResolver;
import kusitms.spin.tikitak.service.team.dto.TeamMemberRequestDTO;
import kusitms.spin.tikitak.service.team.dto.TeamMemberResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamMemberService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final MediaRepository mediaRepository;
    private final DefaultProfileImageResolver defaultProfileImageResolver;

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
                        .profileImgUrl(resolveProfileImgUrl(tm))
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
            throw new BusinessException(ErrorCode.TEAM003);
        }

        Media newProfileMedia = resolveProfileMedia(memberId, request.getProfileImagePublicId());
        if (newProfileMedia != null && teamMember.getProfileMedia() != null) {
            teamMember.getProfileMedia().markDeleted();
        }
        teamMember.updateProfile(
                request.getNickname(),
                newProfileMedia != null ? newProfileMedia.getUrl() : null,
                newProfileMedia
        );

        return TeamMemberResponseDTO.TeamMemberUpdateResponseDTO.builder()
                .nickname(teamMember.getNickname())
                .profileImgUrl(resolveProfileImgUrl(teamMember))
                .build();
    }

    @Transactional
    public void leaveTeam(Long memberId, Long teamId) {
        TeamMember teamMember = teamMemberRepository.findByMemberIdAndTeamId(memberId, teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_MEMBER001));

        if (teamMember.getStatus() != TeamMemberStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.TEAM_MEMBER003);
        }

        if (teamMember.getRole() == TeamMemberRole.OWNER) {
            throw new BusinessException(ErrorCode.TEAM_MEMBER002);
        }

        teamMember.leaveTeam();
        if (teamId.equals(teamMember.getMember().getActiveTeamId())) {
            teamMember.getMember().clearActiveTeam();
        }
    }

    @Transactional
    public void kickMember(Long callerId, Long teamId, Long targetTeamMemberId) {
        TeamMember caller = teamMemberRepository.findByMemberIdAndTeamId(callerId, teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_MEMBER001));

        if (caller.getStatus() != TeamMemberStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.TEAM_MEMBER003);
        }

        if (caller.getRole() != TeamMemberRole.OWNER) {
            throw new BusinessException(ErrorCode.TEAM_MEMBER004);
        }

        // 강퇴 대상 팀 멤버
        TeamMember target = teamMemberRepository.findById(targetTeamMemberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_MEMBER001));

        if (!target.getTeam().getId().equals(teamId)) {
            throw new BusinessException(ErrorCode.TEAM_MEMBER001);
        }

        if (target.getStatus() != TeamMemberStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.TEAM_MEMBER001);
        }

        if (target.getRole() == TeamMemberRole.OWNER) {
            throw new BusinessException(ErrorCode.TEAM_MEMBER005);
        }

        target.kickTeamMember();
    }

    private String resolveProfileImgUrl(TeamMember teamMember) {
        if (teamMember.getProfileImgUrl() != null && !teamMember.getProfileImgUrl().isBlank()) {
            return teamMember.getProfileImgUrl();
        }
        return defaultProfileImageResolver.resolve(teamMember.getMember().getProfileCharacterType());
    }

    private Media resolveProfileMedia(Long memberId, UUID mediaPublicId) {
        if (mediaPublicId == null) return null;
        Media media = mediaRepository.findByPublicIdForUpdate(mediaPublicId)
                .filter(m -> m.getMemberId().equals(memberId)
                        && m.getPurpose() == MediaPurpose.PROFILE_IMAGE
                        && m.getStatus() == MediaStatus.UPLOADED)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA018));
        media.markUsed();
        return media;
    }
}
