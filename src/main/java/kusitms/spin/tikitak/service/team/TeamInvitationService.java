package kusitms.spin.tikitak.service.team;

import kusitms.spin.tikitak.domain.team.entity.Team;
import kusitms.spin.tikitak.domain.team.entity.TeamInvite;
import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberRole;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.repository.team.TeamInviteRepository;
import kusitms.spin.tikitak.repository.team.TeamMemberRepository;
import kusitms.spin.tikitak.repository.team.TeamRepository;
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

	private final TeamRepository teamRepository;
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

		TeamInvite invite = teamInviteRepository.findByTeamId(teamId)
				.map(existing -> {
					existing.update(newToken, newExpiresAt); // 기존 초대링크가 있으면 무효화 후 재발급
					return existing;
				})
				.orElseGet(() -> teamInviteRepository.save(
						TeamInvite.builder() 				// 기존 초대링크가 없으면 생성
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

		if (caller.getRole() != TeamMemberRole.OWNER) {
			throw new BusinessException(ErrorCode.INVITE003);
		}

		TeamInvite invite = teamInviteRepository.findByTeamId(teamId)
				.filter(TeamInvite::isActive)
				.orElseThrow(() -> new BusinessException(ErrorCode.INVITE002));

		return TeamInvitationResponseDTO.GenerateInviteLinkResponseDTO.builder()
				.inviteToken(invite.getInviteToken())
				.expiresAt(invite.getExpiresAt())
				.build();
	}
}
