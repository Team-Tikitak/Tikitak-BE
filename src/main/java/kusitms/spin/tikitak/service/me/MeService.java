package kusitms.spin.tikitak.service.me;

import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberRole;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus;
import kusitms.spin.tikitak.domain.team.enums.TeamStatus;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.repository.member.MemberRepository;
import kusitms.spin.tikitak.repository.team.TeamMemberRepository;
import kusitms.spin.tikitak.service.auth.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MeService {

	private final MemberRepository memberRepository;
	private final TeamMemberRepository teamMemberRepository;
	private final TokenService tokenService;

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
			throw new BusinessException(ErrorCode.ME010);
		}
	}
}
