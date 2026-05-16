package kusitms.spin.tikitak.service.me;

import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.team.entity.Team;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus;
import kusitms.spin.tikitak.domain.team.enums.TeamStatus;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.repository.team.TeamMemberRepository;
import kusitms.spin.tikitak.repository.team.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ActiveTeamService {

	private final TeamRepository teamRepository;
	private final TeamMemberRepository teamMemberRepository;

	public Long resolveActiveTeamId(Member member) {
		Long activeTeamId = member.getActiveTeamId();
		if (activeTeamId == null) {
			return null;
		}

		if (isValidActiveTeam(member.getId(), activeTeamId)) {
			return activeTeamId;
		}

		member.clearActiveTeam();
		return null;
	}

	public void validateChangeableTeam(Long memberId, Long teamId) {
		Team team = teamRepository.findById(teamId)
				.orElseThrow(() -> new BusinessException(ErrorCode.TEAM001));

		if (team.getStatus() != TeamStatus.ACTIVE || !isValidActiveTeam(memberId, teamId)) {
			throw new BusinessException(ErrorCode.ME007);
		}
	}

	public boolean isValidActiveTeam(Long memberId, Long teamId) {
		return teamMemberRepository.existsByTeamIdAndMemberIdAndStatusAndTeamStatus(
				teamId,
				memberId,
				TeamMemberStatus.ACTIVE,
				TeamStatus.ACTIVE
		);
	}
}
