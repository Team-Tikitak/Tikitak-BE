package kusitms.spin.tikitak.repository.dailyquestion;

import kusitms.spin.tikitak.domain.feed.entity.Feed;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyQuestionFeedRepository {

	Feed saveDailyQuestionFeed(Feed feed);

	Optional<Feed> findActiveDailyAnswer(
			Long teamId,
			Long teamMemberId,
			Long questionId,
			LocalDate answerDate
	);

	Optional<Feed> findActiveDailyAnswerForUpdate(
			Long teamId,
			Long teamMemberId,
			Long questionId,
			LocalDate answerDate
	);
}
