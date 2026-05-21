package kusitms.spin.tikitak.service.team;

import kusitms.spin.tikitak.domain.team.entity.Team;
import kusitms.spin.tikitak.domain.team.entity.TeamInvite;
import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberRole;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus;
import kusitms.spin.tikitak.domain.team.enums.TeamStatus;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.repository.member.MemberRepository;
import kusitms.spin.tikitak.repository.team.TeamInviteRepository;
import kusitms.spin.tikitak.repository.team.TeamMemberRepository;
import kusitms.spin.tikitak.repository.team.TeamRepository;
import kusitms.spin.tikitak.service.team.dto.TeamInvitationRequestDTO;
import kusitms.spin.tikitak.service.team.dto.TeamInvitationResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamInvitationService {

	private static final int INVITE_EXPIRE_DAYS = 7;

	private final TeamRepository teamRepository;
	private final MemberRepository memberRepository;
	private final TeamMemberRepository teamMemberRepository;
	private final TeamInviteRepository teamInviteRepository;

	@Transactional
	public TeamInvitationResponseDTO.GenerateInviteLinkResponseDTO generateOrReissueInviteLink(
			Long memberId, Long teamId
	) {
		Team team = teamRepository.findById(teamId)
				.orElseThrow(() -> new BusinessException(ErrorCode.TEAM001));

		TeamMember caller = teamMemberRepository.findByMemberIdAndTeamId(memberId, teamId)
				.orElseThrow(() -> new BusinessException(ErrorCode.TEAM_MEMBER001));

		if (caller.getStatus() != TeamMemberStatus.ACTIVE) {
			throw new BusinessException(ErrorCode.TEAM_MEMBER003);
		}

		// 링크 생성 권한 검증
		if (caller.getRole() != TeamMemberRole.OWNER) {
			throw new BusinessException(ErrorCode.INVITE001);
		}

		String newToken = UUID.randomUUID().toString().replace("-", "");
		LocalDateTime newExpiresAt = LocalDateTime.now().plusDays(INVITE_EXPIRE_DAYS);

		TeamInvite invite;
		try {
			invite = teamInviteRepository.findByTeamId(teamId)
				.map(existing -> {
					existing.update(newToken, newExpiresAt);	// 기존 초대링크가 있으면 무효화 후 재발급
					return existing;
				})
				.orElseGet(() -> teamInviteRepository.save(
					TeamInvite.builder()					// 기존 초대링크가 없으면 생성
								.team(team)
								.inviteToken(newToken)
								.expiresAt(newExpiresAt)
								.active(true)
								.build()
				));
		} catch (DataIntegrityViolationException e) {
			invite = teamInviteRepository.findByTeamId(teamId)
					.orElseThrow(() -> new BusinessException(ErrorCode.INVITE004));
			invite.update(newToken, newExpiresAt);
		}

		return TeamInvitationResponseDTO.GenerateInviteLinkResponseDTO.builder()
				.inviteToken(invite.getInviteToken())
				.expiresAt(invite.getExpiresAt())
				.build();
	}

	public TeamInvitationResponseDTO.GenerateInviteLinkResponseDTO getActiveInviteLink(
			Long memberId, Long teamId
	) {
		teamRepository.findById(teamId)
				.orElseThrow(() -> new BusinessException(ErrorCode.TEAM001));

		TeamMember caller = teamMemberRepository.findByMemberIdAndTeamId(memberId, teamId)
				.orElseThrow(() -> new BusinessException(ErrorCode.TEAM_MEMBER001));

		if (caller.getStatus() != TeamMemberStatus.ACTIVE) {
			throw new BusinessException(ErrorCode.TEAM_MEMBER003);
		}

		TeamInvite invite = teamInviteRepository.findByTeamId(teamId)
				.filter(TeamInvite::isActive)
				.orElseThrow(() -> new BusinessException(ErrorCode.INVITE002));

		if (invite.getExpiresAt().isBefore(LocalDateTime.now())) {
			throw new BusinessException(ErrorCode.INVITE005);
		}

		return TeamInvitationResponseDTO.GenerateInviteLinkResponseDTO.builder()
				.inviteToken(invite.getInviteToken())
				.expiresAt(invite.getExpiresAt())
				.build();
	}

	public TeamInvitationResponseDTO.InviteLinkPreviewResponseDTO getInviteLinkPreview(String token) {
		TeamInvite invite = teamInviteRepository.findByInviteToken(token)
				.orElseThrow(() -> new BusinessException(ErrorCode.INVITE004));

		if (!invite.isActive()) {
			throw new BusinessException(ErrorCode.INVITE004);
		}

		if (invite.getExpiresAt().isBefore(LocalDateTime.now())) {
			throw new BusinessException(ErrorCode.INVITE005);
		}

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

		if (!invite.isActive()) {
			throw new BusinessException(ErrorCode.INVITE004);
		}

		if (invite.getExpiresAt().isBefore(LocalDateTime.now())) {
			throw new BusinessException(ErrorCode.INVITE005);
		}

		Team team = teamRepository.findById(invite.getTeam().getId())
				.orElseThrow(() -> new BusinessException(ErrorCode.INVITE004));

		if (team.getStatus() != TeamStatus.ACTIVE) {
			throw new BusinessException(ErrorCode.INVITE004);
		}

		Member member = memberRepository.findById(memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER001));

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
							existing.updateProfile(request.getNickname(), request.getProfileImgUrl());
						},
						() -> teamMemberRepository.save(TeamMember.builder()
								.team(team)
								.member(member)
								.nickname(request.getNickname())
								.profileImgUrl(request.getProfileImgUrl())
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
}
