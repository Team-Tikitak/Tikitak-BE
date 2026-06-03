package kusitms.spin.tikitak.support.fake.dailyquestion;

import kusitms.spin.tikitak.domain.team.entity.Team;
import kusitms.spin.tikitak.repository.dailyquestion.DailyQuestionTeamRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class FakeDailyQuestionTeamRepository implements DailyQuestionTeamRepository {

	private final Map<Long, Team> store = new HashMap<>();

	public void save(Team team) {
		store.put(team.getId(), team);
	}

	@Override
	public Optional<Team> findDailyQuestionTeamById(Long teamId) {
		return Optional.ofNullable(store.get(teamId));
	}
}
