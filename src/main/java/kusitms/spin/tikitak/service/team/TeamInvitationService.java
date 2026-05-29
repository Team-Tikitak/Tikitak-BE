package kusitms.spin.tikitak.service.team;

import kusitms.spin.tikitak.domain.media.entity.Media;
import kusitms.spin.tikitak.domain.media.enums.MediaPurpose;
import kusitms.spin.tikitak.domain.media.enums.MediaStatus;
import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.team.entity.Team;
import kusitms.spin.tikitak.domain.team.entity.TeamInvite;
import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberRole;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus;
import kusitms.spin.tikitak.domain.team.enums.TeamStatus;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.repository.media.MediaRepository;
import kusitms.spin.tikitak.repository.member.MemberRepository;
import kusitms.spin.tikitak.repository.team.TeamInviteRepository;
import kusitms.spin.tikitak.repository.team.TeamMemberRepository;
import kusitms.spin.tikitak.repository.team.TeamRepository;
import kusitms.spin.tikitak.service.team.dto.TeamInvitationRequestDTO;
import kusitms.spin.tikitak.service.team.dto.TeamInvitationResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamInvitationService {

	private static final int INVITE_EXPIRE_DAYS = 7;
	private static final int TEAM_MEMBER_LIMIT = 100;

	private final TeamRepository teamRepository;
	private final MemberRepository memberRepository;
	private final TeamMemberRepository teamMemberRepository;
	private final TeamInviteRepository teamInviteRepository;
	private final MediaRepository mediaRepository;

	@Transactional
	public TeamInvitationResponseDTO.GenerateInviteLinkResponseDTO generateOrReissueInviteLink(
			Long memberId, Long teamId
	) {
		Team team = teamRepository.findByIdForUpdate(teamId)
				.orElseThrow(() -> new BusinessException(ErrorCode.TEAM001));

		TeamMember caller = teamMemberRepository.findByMemberIdAndTeamId(memberId, teamId)
				.orElseThrow(() -> new BusinessException(ErrorCode.TEAM_MEMBER001));

		if (caller.getStatus() != TeamMemberStatus.ACTIVE) {
			throw new BusinessException(ErrorCode.TEAM_MEMBER003);
		}

		if (caller.getRole() != TeamMemberRole.OWNER) {
			throw new BusinessException(ErrorCode.INVITE001);
		}

		String newToken = UUID.randomUUID().toString().replace("-", "");
		LocalDateTime newExpiresAt = LocalDateTime.now().plusDays(INVITE_EXPIRE_DAYS);

		TeamInvite invite = teamInviteRepository.findByTeamId(teamId)
				.map(existing -> {
					existing.update(newToken, newExpiresAt);
					return existing;
				})
				.orElseGet(() -> teamInviteRepository.save(
						TeamInvite.builder()
								.team(team)
								.inviteToken(newToken)
								.expiresAt(newExpiresAt)
								.active(true)
								.build()
				));

		return TeamInvitationResponseDTO.GenerateInviteLinkResponseDTO.builder()
				.inviteToken(invite.getInviteToken())
				.expiresAt(invite.getExpiresAt())
				.build();
	}

	public TeamInvitationResponseDTO.ActiveInviteLinkResponseDTO getActiveInviteLink(
			Long memberId, Long teamId
	) {
		TeamMember caller = teamMemberRepository.findByMemberIdAndTeamId(memberId, teamId)
				.orElseThrow(() -> new BusinessException(ErrorCode.TEAM_MEMBER001));

		if (caller.getStatus() != TeamMemberStatus.ACTIVE) {
			throw new BusinessException(ErrorCode.TEAM_MEMBER003);
		}

		Team currentTeam = teamRepository.findById(teamId)
				.orElseThrow(() -> new BusinessException(ErrorCode.TEAM001));


		TeamInvite invite = teamInviteRepository.findByTeamId(teamId)
				.filter(TeamInvite::isActive)
				.orElseThrow(() -> new BusinessException(ErrorCode.INVITE002));

		if (invite.isExpired()) {
			throw new BusinessException(ErrorCode.INVITE005);
		}

		return TeamInvitationResponseDTO.ActiveInviteLinkResponseDTO.builder()
				.inviteToken(invite.getInviteToken())
				.teamName(currentTeam.getName())
				.expiresAt(invite.getExpiresAt())
				.build();
	}

	public TeamInvitationResponseDTO.InviteLinkPreviewResponseDTO getInviteLinkPreview(String token) {
		TeamInvite invite = teamInviteRepository.findByInviteToken(token)
				.orElseThrow(() -> new BusinessException(ErrorCode.INVITE004));

		validateInviteUsable(invite);

		Team team = teamRepository.findTeamWithTeamMembersById(invite.getTeam().getId())
				.orElseThrow(() -> new BusinessException(ErrorCode.INVITE004));

		if (team.getStatus() != TeamStatus.ACTIVE) {
			throw new BusinessException(ErrorCode.INVITE004);
		}

		int memberCount = (int) team.getTeamMembers().stream()
				.filter(tm -> tm.getStatus() == TeamMemberStatus.ACTIVE)
				.count();

		return TeamInvitationResponseDTO.InviteLinkPreviewResponseDTO.builder()
				.teamId(team.getId())
				.teamName(team.getName())
				.teamDescription(team.getDescription())
				.teamImgUrl(team.getTeamImgUrl())
				.memberCount(memberCount)
				.build();
	}

	@Transactional
	public TeamInvitationResponseDTO.JoinTeamResponseDTO acceptInviteLink(
			Long memberId, String token, TeamInvitationRequestDTO.JoinTeamRequestDTO request
	) {
		TeamInvite invite = teamInviteRepository.findByInviteToken(token)
				.orElseThrow(() -> new BusinessException(ErrorCode.INVITE004));

		validateInviteUsable(invite);

		Team team = teamRepository.findByIdForUpdate(invite.getTeam().getId())
				.orElseThrow(() -> new BusinessException(ErrorCode.INVITE004));

		if (team.getStatus() != TeamStatus.ACTIVE) {
			throw new BusinessException(ErrorCode.INVITE004);
		}

		Member member = memberRepository.findById(memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER001));

		long activeCount = teamMemberRepository.countByTeamIdAndStatus(team.getId(), TeamMemberStatus.ACTIVE);
		if (activeCount >= TEAM_MEMBER_LIMIT) {
			throw new BusinessException(ErrorCode.INVITE008);
		}

		String profileImgUrl = resolveProfileImgUrlFromMedia(memberId, request.getProfileImagePublicId());

		teamMemberRepository.findByMemberIdAndTeamId(memberId, team.getId())
				.ifPresentOrElse(
						existing -> {
							if (existing.getStatus() == TeamMemberStatus.ACTIVE) {
								throw new BusinessException(ErrorCode.INVITE006);
							}
							if (existing.getStatus() == TeamMemberStatus.BANNED) {
								throw new BusinessException(ErrorCode.INVITE007);
							}
							existing.rejoin();
							existing.updateProfile(request.getNickname(), profileImgUrl);
						},
						() -> teamMemberRepository.save(TeamMember.builder()
								.team(team)
								.member(member)
								.nickname(request.getNickname())
								.profileImgUrl(profileImgUrl)
								.role(TeamMemberRole.MEMBER)
								.status(TeamMemberStatus.ACTIVE)
								.build())
				);

		memberRepository.setActiveTeamIdIfNull(memberId, team.getId());

		return TeamInvitationResponseDTO.JoinTeamResponseDTO.builder()
				.teamId(team.getId())
				.teamName(team.getName())
				.build();
	}

	private void validateInviteUsable(TeamInvite invite) {
		if (!invite.isActive()) {
			throw new BusinessException(ErrorCode.INVITE004);
		}
		if (invite.isExpired()) {
			throw new BusinessException(ErrorCode.INVITE005);
		}
	}

	private String resolveProfileImgUrlFromMedia(Long memberId, UUID mediaPublicId) {
		if (mediaPublicId == null) return null;
		Media media = mediaRepository.findByPublicIdForUpdate(mediaPublicId)
				.filter(m -> m.getMemberId().equals(memberId)
						&& m.getPurpose() == MediaPurpose.PROFILE_IMAGE
						&& m.getStatus() == MediaStatus.UPLOADED)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEDIA018));
		media.markUsed();
		return media.getUrl();
	}
}
