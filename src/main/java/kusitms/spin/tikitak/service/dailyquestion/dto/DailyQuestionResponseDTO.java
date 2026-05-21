package kusitms.spin.tikitak.service.dailyquestion.dto;

import kusitms.spin.tikitak.domain.feed.enums.FeedType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class DailyQuestionResponseDTO {

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class TodayQuestionResponseDTO {
		private Long questionId;
		private String content;
		private LocalDate date;
		private boolean answered;
		private Long answerFeedId;
		private AnswerDTO answer;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class AnswerMutationResponseDTO {
		private Long feedId;
		private FeedType type;
		private QuestionDTO question;
		private AnswerDTO answer;
		private LocalDateTime createdAt;
		private LocalDateTime updatedAt;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class QuestionDTO {
		private Long questionId;
		private String content;
		private LocalDate answerDate;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class AnswerDTO {
		private String content;
		private String imageUrl;
		private LocalDateTime createdAt;
		private LocalDateTime updatedAt;
	}
}
