package kusitms.spin.tikitak.support.fake.dailyquestion;

import kusitms.spin.tikitak.domain.feed.entity.Feed;
import kusitms.spin.tikitak.repository.dailyquestion.DailyQuestionFeedRepository;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class FakeDailyQuestionFeedRepository implements DailyQuestionFeedRepository {

	private final Map<Long, Feed> store = new HashMap<>();
	private long sequence = 1L;
	private RuntimeException saveException;

	public void throwOnSave(RuntimeException saveException) {
		this.saveException = saveException;
	}

	@Override
	public Feed saveDailyQuestionFeed(Feed feed) {
		if (saveException != null) {
			throw saveException;
		}
		if (feed.getId() == null) {
			ReflectionTestUtils.setField(feed, "id", sequence++);
		}
		if (feed.getCreatedAt() == null) {
			ReflectionTestUtils.setField(feed, "createdAt", LocalDateTime.of(2026, 3, 4, 20, 30));
		}
		if (feed.getUpdatedAt() == null) {
			ReflectionTestUtils.setField(feed, "updatedAt", LocalDateTime.of(2026, 3, 4, 20, 30));
		}
		store.put(feed.getId(), feed);
		return feed;
	}

	@Override
	public Optional<Feed> findActiveDailyAnswer(
			Long teamId,
			Long teamMemberId,
			Long questionId,
			LocalDate answerDate
	) {
		return findActiveDailyAnswerInStore(teamId, teamMemberId, questionId, answerDate);
	}

	@Override
	public Optional<Feed> findActiveDailyAnswerForUpdate(
			Long teamId,
			Long teamMemberId,
			Long questionId,
			LocalDate answerDate
	) {
		return findActiveDailyAnswerInStore(teamId, teamMemberId, questionId, answerDate);
	}

	private Optional<Feed> findActiveDailyAnswerInStore(
			Long teamId,
			Long teamMemberId,
			Long questionId,
			LocalDate answerDate
	) {
		return store.values().stream()
				.filter(feed -> feed.getTeam().getId().equals(teamId))
				.filter(feed -> feed.getTeamMember().getId().equals(teamMemberId))
				.filter(feed -> feed.getQuestion().getId().equals(questionId))
				.filter(feed -> feed.getQuestionAnswerDate().equals(answerDate))
				.filter(feed -> feed.getDeletedAt() == null)
				.findFirst();
	}
}
