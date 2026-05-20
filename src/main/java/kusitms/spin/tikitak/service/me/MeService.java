package kusitms.spin.tikitak.service.me;

import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.member.enums.MemberStatus;
import kusitms.spin.tikitak.domain.team.entity.Team;
import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberRole;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus;
import kusitms.spin.tikitak.domain.team.enums.TeamStatus;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.repository.member.MemberRepository;
import kusitms.spin.tikitak.repository.team.TeamMemberRepository;
import kusitms.spin.tikitak.service.auth.TokenService;
import kusitms.spin.tikitak.service.me.dto.MeRequestDTO;
import kusitms.spin.tikitak.service.me.dto.MeResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MeService {

	private final MemberRepository memberRepository;
	private final TeamMemberRepository teamMemberRepository;
	private final TokenService tokenService;
	private final ActiveTeamService activeTeamService;

	@Transactional
	public MeResponseDTO.MeProfileResponseDTO getMyProfile(Long memberId) {
		try {
			Member member = getActiveMember(memberId);
			Long activeTeamId = activeTeamService.resolveActiveTeamId(member);
			boolean hasTeam = hasActiveTeam(memberId);

			return MeResponseDTO.MeProfileResponseDTO.builder()
					.memberId(member.getId())
					.email(member.getEmail())
					.socialProvider(member.getSocialProvider())
					.status(member.getStatus())
					.hasAgreedRequiredTerms(member.isTermsAgreed() && member.isPrivacyAgreed())
					.onboardingCompleted(member.isOnboardingCompleted())
					.profileCharacterType(member.getProfileCharacterType())
					.activeTeamId(activeTeamId)
					.hasTeam(hasTeam)
					.createdAt(member.getCreatedAt())
					.build();
		} catch (BusinessException e) {
			throw e;
		} catch (Exception e) {
			throw new BusinessException(ErrorCode.ME002, e);
		}
	}

	@Transactional
	public MeResponseDTO.TeamListResponseDTO getMyTeams(Long memberId) {
		try {
			Member member = getActiveMember(memberId);
			Long activeTeamId = activeTeamService.resolveActiveTeamId(member);
			List<TeamMember> memberships = teamMemberRepository.findActiveTeamMemberships(
					memberId,
					TeamMemberStatus.ACTIVE,
					TeamStatus.ACTIVE
			);

			List<Long> teamIds = memberships.stream()
					.map(teamMember -> teamMember.getTeam().getId())
					.toList();
			Map<Long, Long> memberCountByTeamId = countActiveMembers(teamIds);

			List<MeResponseDTO.TeamItemDTO> teams = memberships.stream()
					.map(teamMember -> toTeamItem(teamMember, activeTeamId, memberCountByTeamId))
					.sorted(Comparator
							.comparing(MeResponseDTO.TeamItemDTO::isActive).reversed()
							.thenComparing(MeResponseDTO.TeamItemDTO::getJoinedAt, Comparator.reverseOrder()))
					.toList();

			return MeResponseDTO.TeamListResponseDTO.builder()
					.teams(teams)
					.build();
		} catch (BusinessException e) {
			throw e;
		} catch (Exception e) {
			throw new BusinessException(ErrorCode.ME003, e);
		}
	}

	@Transactional
	public MeResponseDTO.ActiveTeamUpdateResponseDTO updateActiveTeam(
			Long memberId,
			MeRequestDTO.ActiveTeamUpdateRequestDTO request
	) {
		try {
			Member member = getActiveMember(memberId);
			activeTeamService.validateChangeableTeam(memberId, request.getTeamId());
			member.changeActiveTeam(request.getTeamId());

			return MeResponseDTO.ActiveTeamUpdateResponseDTO.builder()
					.activeTeamId(member.getActiveTeamId())
					.build();
		} catch (BusinessException e) {
			throw e;
		} catch (Exception e) {
			throw new BusinessException(ErrorCode.ME008, e);
		}
	}

	@Transactional
	public MeResponseDTO.AgreementResponseDTO getAgreements(Long memberId) {
		try {
			Member member = getActiveMember(memberId);
			return toAgreementResponse(member);
		} catch (BusinessException e) {
			throw e;
		} catch (Exception e) {
			throw new BusinessException(ErrorCode.AGREEMENT001, e);
		}
	}

	@Transactional
	public MeResponseDTO.OnboardingUpdateResponseDTO updateOnboarding(
			Long memberId,
			MeRequestDTO.OnboardingUpdateRequestDTO request
	) {
		try {
			Member member = getActiveMember(memberId);
			member.completeOnboarding(request.getProfileCharacterType());
			memberRepository.flush();
			return MeResponseDTO.OnboardingUpdateResponseDTO.builder()
					.onboardingCompleted(member.isOnboardingCompleted())
					.profileCharacterType(member.getProfileCharacterType())
					.build();
		} catch (BusinessException e) {
			throw e;
		} catch (Exception e) {
			throw new BusinessException(ErrorCode.ME011, e);
		}
	}

	@Transactional
	public MeResponseDTO.AgreementResponseDTO updateAgreements(
			Long memberId,
			MeRequestDTO.AgreementUpdateRequestDTO request
	) {
		try {
			if (!Boolean.TRUE.equals(request.getTermsAgreed()) || !Boolean.TRUE.equals(request.getPrivacyAgreed())) {
				throw new BusinessException(ErrorCode.AGREEMENT002);
			}

			Member member = getActiveMember(memberId);
			member.agreeRequiredTerms();
			return toAgreementResponse(member);
		} catch (BusinessException e) {
			throw e;
		} catch (Exception e) {
			throw new BusinessException(ErrorCode.AGREEMENT003, e);
		}
	}

	@Transactional
	public void withdraw(Long memberId) {
		try {
			Member member = memberRepository.findById(memberId)
					.orElseThrow(() -> new BusinessException(ErrorCode.AUTH009));

			boolean hasActiveOwnerTeam = teamMemberRepository.existsActiveOwnerTeam(
					memberId,
					TeamMemberRole.OWNER,
					TeamMemberStatus.ACTIVE,
					TeamStatus.ACTIVE
			);
			if (hasActiveOwnerTeam) {
				throw new BusinessException(ErrorCode.ME009);
			}

			member.withdraw();
			tokenService.revokeAllRefreshTokens(memberId);
		} catch (BusinessException e) {
			throw e;
		} catch (Exception e) {
			throw new BusinessException(ErrorCode.ME010, e);
		}
	}

	private Member getActiveMember(Long memberId) {
		Member member = memberRepository.findById(memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ME001));
		if (member.getStatus() != MemberStatus.ACTIVE) {
			throw new BusinessException(ErrorCode.ME001);
		}
		return member;
	}

	private boolean hasActiveTeam(Long memberId) {
		return teamMemberRepository.existsActiveMembership(
				memberId,
				TeamMemberStatus.ACTIVE,
				TeamStatus.ACTIVE
		);
	}

	private Map<Long, Long> countActiveMembers(List<Long> teamIds) {
		if (teamIds.isEmpty()) {
			return Map.of();
		}

		return teamMemberRepository.countActiveMembersByTeamIds(teamIds, TeamMemberStatus.ACTIVE).stream()
				.collect(Collectors.toMap(
						row -> (Long) row[0],
						row -> (Long) row[1]
				));
	}

	private MeResponseDTO.TeamItemDTO toTeamItem(
			TeamMember teamMember,
			Long activeTeamId,
			Map<Long, Long> memberCountByTeamId
	) {
		Team team = teamMember.getTeam();
		boolean isActive = team.getId().equals(activeTeamId);

		return MeResponseDTO.TeamItemDTO.builder()
				.teamId(team.getId())
				.teamMemberId(teamMember.getId())
				.teamName(team.getName())
				.description(team.getDescription())
				.role(teamMember.getRole())
				.nickname(teamMember.getNickname())
				.memberCount(memberCountByTeamId.getOrDefault(team.getId(), 0L))
				.isActive(isActive)
				.joinedAt(teamMember.getCreatedAt())
				.build();
	}

	private MeResponseDTO.AgreementResponseDTO toAgreementResponse(Member member) {
		return MeResponseDTO.AgreementResponseDTO.builder()
				.termsAgreed(member.isTermsAgreed())
				.privacyAgreed(member.isPrivacyAgreed())
				.termsAgreedAt(member.getTermsAgreedAt())
				.build();
	}
}
