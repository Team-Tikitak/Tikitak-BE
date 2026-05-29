package kusitms.spin.tikitak.service.team;

import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.team.entity.Team;
import kusitms.spin.tikitak.domain.team.entity.TeamInvite;
import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberRole;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus;
import kusitms.spin.tikitak.domain.team.enums.TeamStatus;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.repository.member.MemberRepository;
import kusitms.spin.tikitak.repository.team.TeamInviteRepository;
import kusitms.spin.tikitak.repository.team.TeamMemberRepository;
import kusitms.spin.tikitak.repository.team.TeamRepository;
import kusitms.spin.tikitak.service.me.DefaultProfileImageResolver;
import kusitms.spin.tikitak.service.team.dto.TeamRequestDTO;
import kusitms.spin.tikitak.service.team.dto.TeamResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamService {

    private final TeamRepository teamRepository;
    private final MemberRepository memberRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamInviteRepository teamInviteRepository;
    private final DefaultProfileImageResolver defaultProfileImageResolver;

    @Transactional
    public TeamResponseDTO.TeamCreateResponseDTO createTeam(Long memberId, TeamRequestDTO.TeamCreateRequestDTO request) {

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER001));

        // Team 생성
        Team team = Team.builder()
                .name(request.getTeamName())
                .description(request.getIntroduction())
                .status(TeamStatus.ACTIVE)
                .build();

        teamRepository.save(team);
        member.changeActiveTeam(team.getId());

        // TeamMember 생성
        String profileImgUrl = (request.getProfileImageUrl() != null && !request.getProfileImageUrl().isBlank())
                ? request.getProfileImageUrl()
                : null;

        TeamMember teamMember = TeamMember.builder()
                .team(team)
                .member(member)
                .nickname(request.getNickName())
                .profileImgUrl(profileImgUrl)
                .role(TeamMemberRole.OWNER)
                .status(TeamMemberStatus.ACTIVE)
                .build();

        teamMemberRepository.save(teamMember);

        // 팀 초대링크 생성
        teamInviteRepository.save(TeamInvite.builder()
                .team(team)
                .inviteToken(UUID.randomUUID().toString().replace("-", ""))
                .expiresAt(LocalDateTime.now().plusDays(7))
                .active(true)
                .build());

        return TeamResponseDTO.TeamCreateResponseDTO.builder()
                .teamName(team.getName())
                .build();
    }

    @Transactional
    public TeamResponseDTO.TeamUpdateResponseDTO updateTeam(Long memberId, Long teamId, TeamRequestDTO.TeamUpdateRequestDTO request) {
        Team team = teamRepository.findTeamWithTeamMembersById(teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM001));

        TeamMember currentMember = team.getTeamMembers().stream()
                .filter(tm -> tm.getMember().getId().equals(memberId)
                        && tm.getStatus() == TeamMemberStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM002));

        // TeamMember가 팀장인지 확인
        if (currentMember.getRole() != TeamMemberRole.OWNER) {
            throw new BusinessException(ErrorCode.TEAM003);
        }

        team.update(request.getTeamName(), request.getIntroduction());

        return TeamResponseDTO.TeamUpdateResponseDTO.builder()
                .teamName(team.getName())
                .introduction(team.getDescription())
                .build();
    }

    public TeamResponseDTO.TeamDetailResponseDTO viewTeamDetail(Long memberId, Long teamId) {
        Team team = teamRepository.findTeamWithTeamMembersById(teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM001));

        TeamMember currentMember = team.getTeamMembers().stream()
                .filter(tm -> tm.getMember().getId().equals(memberId)
                        && tm.getStatus() == TeamMemberStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM002));

        List<TeamResponseDTO.TeamMemberDTO> members = team.getTeamMembers().stream()
                .filter(tm -> tm.getStatus() == TeamMemberStatus.ACTIVE
                        && !tm.getId().equals(currentMember.getId()))
                .map(tm -> TeamResponseDTO.TeamMemberDTO.builder()
                        .teamMemberId(tm.getId())
                        .nickname(tm.getNickname())
                        .teamMemberRole(tm.getRole())
                        .email(tm.getMember().getEmail())
                        .profileImgUrl(resolveProfileImgUrl(tm))
                        .build())
                .toList();

        return TeamResponseDTO.TeamDetailResponseDTO.builder()
                .teamName(team.getName())
                .myProfile(TeamResponseDTO.MyProfileDTO.builder()
                        .nickname(currentMember.getNickname())
                        .teamMemberRole(currentMember.getRole())
                        .profileImgUrl(resolveProfileImgUrl(currentMember))
                        .build())
                .teamMembers(members)
                .build();
    }

    @Transactional
    public void deleteTeam(Long memberId, Long teamId) {
        Team team = teamRepository.findTeamWithTeamMembersById(teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM001));

        // 이미 삭제 요청된 팀인지 확인
        if (team.getStatus() == TeamStatus.INACTIVE) {
            throw new BusinessException(ErrorCode.TEAM004);
        }

        TeamMember currentMember = team.getTeamMembers().stream()
                .filter(tm -> tm.getMember().getId().equals(memberId)
                        && tm.getStatus() == TeamMemberStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM002));

        // 삭제 요청한 사람이 팀장인지 확인
        if (currentMember.getRole() != TeamMemberRole.OWNER) {
            throw new BusinessException(ErrorCode.TEAM005);
        }

        // 팀의 status를 INACTIVE로 변경
        team.inactive();
        memberRepository.clearActiveTeamIdByTeamId(teamId);
    }

    @Transactional
    public void recoverTeam(Long memberId, Long teamId) {
        Team team = teamRepository.findTeamWithTeamMembersById(teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM001));

        // 삭제 신청된 팀인지 확인
        if (team.getStatus() != TeamStatus.INACTIVE) {
            throw new BusinessException(ErrorCode.TEAM006);
        }

        TeamMember currentMember = team.getTeamMembers().stream()
                .filter(tm -> tm.getMember().getId().equals(memberId)
                        && tm.getStatus() == TeamMemberStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM002));

        if (currentMember.getRole() != TeamMemberRole.OWNER) {
            throw new BusinessException(ErrorCode.TEAM007);
        }

        // 팀의 status를 ACTIVE로 변경
        team.recover();
    }

    private String resolveProfileImgUrl(TeamMember teamMember) {
        if (teamMember.getProfileImgUrl() != null && !teamMember.getProfileImgUrl().isBlank()) {
            return teamMember.getProfileImgUrl();
        }
        return defaultProfileImageResolver.resolve(teamMember.getMember().getProfileCharacterType());
    }
}
