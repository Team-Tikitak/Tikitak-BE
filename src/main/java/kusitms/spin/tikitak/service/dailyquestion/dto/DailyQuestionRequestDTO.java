package kusitms.spin.tikitak.service.dailyquestion.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotNull;
import kusitms.spin.tikitak.global.dto.PatchField;
import kusitms.spin.tikitak.global.jackson.PatchFieldDeserializer;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

public class DailyQuestionRequestDTO {

	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class AnswerCreateRequestDTO {
		private String content;
		@NotNull
		private UUID mediaPublicId;
	}

	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class AnswerUpdateRequestDTO {
		@JsonDeserialize(using = PatchFieldDeserializer.class)
		private PatchField<String> content = PatchField.undefined();

		@JsonDeserialize(using = PatchFieldDeserializer.class)
		private PatchField<UUID> mediaPublicId = PatchField.undefined();
	}
}
