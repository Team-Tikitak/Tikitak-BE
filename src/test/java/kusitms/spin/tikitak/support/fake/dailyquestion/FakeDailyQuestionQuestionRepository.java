package kusitms.spin.tikitak.support.fake.dailyquestion;

import kusitms.spin.tikitak.domain.question.entity.Question;
import kusitms.spin.tikitak.repository.dailyquestion.DailyQuestionQuestionRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class FakeDailyQuestionQuestionRepository implements DailyQuestionQuestionRepository {

	private final List<Question> store = new ArrayList<>();

	public void save(Question question) {
		store.add(question);
	}

	@Override
	public List<Question> findAvailableQuestions(LocalDate today) {
		return store.stream()
				.filter(Question::isActive)
				.filter(question -> question.getEffectiveFrom() == null || !question.getEffectiveFrom().isAfter(today))
				.sorted(Comparator.comparing(Question::getSequence))
				.toList();
	}
}
