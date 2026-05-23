package kusitms.spin.tikitak.repository.dailyquestion;

import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus;
import kusitms.spin.tikitak.domain.team.enums.TeamStatus;

import java.util.Optional;

public interface DailyQuestionTeamMemberRepository {

	Optional<TeamMember> findActiveByMemberIdAndTeamId(
			Long memberId,
			Long teamId,
			TeamMemberStatus memberStatus,
			TeamStatus teamStatus
	);
}
