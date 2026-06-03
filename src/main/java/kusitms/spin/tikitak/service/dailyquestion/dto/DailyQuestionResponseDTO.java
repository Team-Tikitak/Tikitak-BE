package kusitms.spin.tikitak.service.dailyquestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
		@Schema(
				description = "데일리 질문 답변 이미지 URL. feed_detail preset이 적용됩니다.",
				example = "https://media.tikitak.space/media/daily-question-image/8b2e58f0-8e24-4e34-91b0-87dc86d1892a.jpg?preset=feed_detail"
		)
		private String imageUrl;
		private LocalDateTime createdAt;
		private LocalDateTime updatedAt;
	}
}
