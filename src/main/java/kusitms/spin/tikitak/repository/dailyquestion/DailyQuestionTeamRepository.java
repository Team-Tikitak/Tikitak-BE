package kusitms.spin.tikitak.repository.dailyquestion;

import kusitms.spin.tikitak.domain.team.entity.Team;

import java.util.Optional;

public interface DailyQuestionTeamRepository {

	Optional<Team> findDailyQuestionTeamById(Long teamId);
}
