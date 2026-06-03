package kusitms.spin.tikitak.repository.dailyquestion;

import kusitms.spin.tikitak.domain.question.entity.Question;

import java.time.LocalDate;
import java.util.List;

public interface DailyQuestionQuestionRepository {

	List<Question> findAvailableQuestions(LocalDate today);
}
