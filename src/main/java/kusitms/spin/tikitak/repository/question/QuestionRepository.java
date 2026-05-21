package kusitms.spin.tikitak.repository.question;

import kusitms.spin.tikitak.domain.question.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {

	@Query("""
			select q
			from Question q
			where q.active = true
				and q.effectiveFrom <= :today
			order by q.sequence asc
			""")
	List<Question> findAvailableQuestions(@Param("today") LocalDate today);
}
