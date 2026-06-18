package kusitms.spin.tikitak.support.fake.dailyquestion;

import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus;
import kusitms.spin.tikitak.domain.team.enums.TeamStatus;
import kusitms.spin.tikitak.repository.dailyquestion.DailyQuestionTeamMemberRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FakeDailyQuestionTeamMemberRepository implements DailyQuestionTeamMemberRepository {

	private final List<TeamMember> store = new ArrayList<>();

	public void save(TeamMember teamMember) {
		store.add(teamMember);
	}

	@Override
	public Optional<TeamMember> findActiveByMemberIdAndTeamId(
			Long memberId,
			Long teamId,
			TeamMemberStatus memberStatus,
			TeamStatus teamStatus
	) {
		return store.stream()
				.filter(teamMember -> teamMember.getMember().getId().equals(memberId))
				.filter(teamMember -> teamMember.getTeam().getId().equals(teamId))
				.filter(teamMember -> teamMember.getStatus() == memberStatus)
				.filter(teamMember -> teamMember.getTeam().getStatus() == teamStatus)
				.findFirst();
	}

	@Override
	public List<Long> findActiveMemberIdsByTeamIdExcludingTeamMember(
			Long teamId,
			Long excludeTeamMemberId,
			TeamMemberStatus memberStatus,
			TeamStatus teamStatus
	) {
		return store.stream()
				.filter(teamMember -> teamMember.getTeam().getId().equals(teamId))
				.filter(teamMember -> !teamMember.getId().equals(excludeTeamMemberId))
				.filter(teamMember -> teamMember.getStatus() == memberStatus)
				.filter(teamMember -> teamMember.getTeam().getStatus() == teamStatus)
				.map(teamMember -> teamMember.getMember().getId())
				.toList();
	}
}
