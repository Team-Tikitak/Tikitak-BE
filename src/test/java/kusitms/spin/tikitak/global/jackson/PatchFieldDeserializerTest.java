package kusitms.spin.tikitak.global.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import kusitms.spin.tikitak.global.dto.PatchField;
import kusitms.spin.tikitak.service.dailyquestion.dto.DailyQuestionRequestDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PatchFieldDeserializerTest {

	private static final UUID MEDIA_PUBLIC_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	@DisplayName("Deserializes explicit null PATCH fields as defined null")
	void deserializeExplicitNullAsDefinedNull() throws Exception {
		DailyQuestionRequestDTO.AnswerUpdateRequestDTO request = objectMapper.readValue(
				"""
						{
						  "content": null,
						  "mediaPublicId": null
						}
						""",
				DailyQuestionRequestDTO.AnswerUpdateRequestDTO.class
		);

		assertDefinedNull(request.getContent());
		assertDefinedNull(request.getMediaPublicId());
	}

	@Test
	@DisplayName("Keeps omitted PATCH fields undefined")
	void deserializeOmittedFieldAsUndefined() throws Exception {
		DailyQuestionRequestDTO.AnswerUpdateRequestDTO request = objectMapper.readValue(
				"{}",
				DailyQuestionRequestDTO.AnswerUpdateRequestDTO.class
		);

		assertThat(request.getContent().isDefined()).isFalse();
		assertThat(request.getMediaPublicId().isDefined()).isFalse();
	}

	@Test
	@DisplayName("Deserializes present PATCH fields as defined values")
	void deserializeValueAsDefinedValue() throws Exception {
		DailyQuestionRequestDTO.AnswerUpdateRequestDTO request = objectMapper.readValue(
				"""
						{
						  "content": "updated answer",
						  "mediaPublicId": "%s"
						}
						""".formatted(MEDIA_PUBLIC_ID),
				DailyQuestionRequestDTO.AnswerUpdateRequestDTO.class
		);

		assertThat(request.getContent().isDefined()).isTrue();
		assertThat(request.getContent().getValue()).isEqualTo("updated answer");
		assertThat(request.getMediaPublicId().isDefined()).isTrue();
		assertThat(request.getMediaPublicId().getValue()).isEqualTo(MEDIA_PUBLIC_ID);
	}

	private <T> void assertDefinedNull(PatchField<T> patchField) {
		assertThat(patchField).isNotNull();
		assertThat(patchField.isDefined()).isTrue();
		assertThat(patchField.getValue()).isNull();
	}
}
