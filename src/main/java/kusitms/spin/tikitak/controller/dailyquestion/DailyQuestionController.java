package kusitms.spin.tikitak.controller.dailyquestion;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kusitms.spin.tikitak.global.dto.CommonResponse;
import kusitms.spin.tikitak.global.security.CurrentMemberId;
import kusitms.spin.tikitak.service.dailyquestion.DailyQuestionService;
import kusitms.spin.tikitak.service.dailyquestion.dto.DailyQuestionRequestDTO;
import kusitms.spin.tikitak.service.dailyquestion.dto.DailyQuestionResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/teams/{teamId}/daily-questions")
@Tag(name = "Daily Question", description = "오늘의 질문 조회, 답변 작성 및 수정 API")
@SecurityRequirement(name = "bearerAuth")
public class DailyQuestionController {

	private final DailyQuestionService dailyQuestionService;

	@GetMapping("/today")
	@Operation(
			summary = "오늘의 질문 조회",
			description = "KST 기준 오늘의 질문과 내 답변 여부를 조회합니다. 이미 답변한 경우 답변 피드 ID와 답변 내용을 함께 반환합니다."
	)
	public CommonResponse<DailyQuestionResponseDTO.TodayQuestionResponseDTO> getTodayQuestion(
			@Parameter(hidden = true) @CurrentMemberId Long memberId,
			@Parameter(description = "오늘의 질문을 조회할 팀 ID", required = true)
			@PathVariable Long teamId
	) {
		return CommonResponse.success(dailyQuestionService.getTodayQuestion(memberId, teamId));
	}

	@PostMapping("/{questionId}/my-answer")
	@Operation(
			summary = "오늘의 질문 답변 작성",
			description = "오늘의 질문에 대한 내 답변을 피드로 작성합니다. 이미지는 DAILY_QUESTION_IMAGE purpose로 업로드 완료된 미디어 1장이 필요하며, 작성자는 자동으로 본인 태그됩니다."
	)
	public ResponseEntity<CommonResponse<DailyQuestionResponseDTO.AnswerMutationResponseDTO>> createMyAnswer(
			@Parameter(hidden = true) @CurrentMemberId Long memberId,
			@Parameter(description = "답변을 작성할 팀 ID", required = true)
			@PathVariable Long teamId,
			@Parameter(description = "오늘의 질문 ID", required = true)
			@PathVariable Long questionId,
			@Parameter(description = "오늘의 질문 답변 작성 요청", required = true)
			@Valid @RequestBody DailyQuestionRequestDTO.AnswerCreateRequestDTO request
	) {
		DailyQuestionResponseDTO.AnswerMutationResponseDTO response =
				dailyQuestionService.createMyAnswer(memberId, teamId, questionId, request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(CommonResponse.successWithStatus(response, HttpStatus.CREATED.value()));
	}

	@PatchMapping("/{questionId}/my-answer")
	@Operation(
			summary = "오늘의 질문 답변 수정",
			description = "KST 기준 오늘 작성한 내 답변의 본문 또는 이미지를 수정합니다. mediaPublicId를 생략하면 기존 이미지를 유지하고, null로 전달하면 실패합니다."
	)
	public CommonResponse<DailyQuestionResponseDTO.AnswerMutationResponseDTO> updateMyAnswer(
			@Parameter(hidden = true) @CurrentMemberId Long memberId,
			@Parameter(description = "답변을 수정할 팀 ID", required = true)
			@PathVariable Long teamId,
			@Parameter(description = "오늘의 질문 ID", required = true)
			@PathVariable Long questionId,
			@Parameter(description = "오늘의 질문 답변 수정 요청", required = true)
			@Valid @RequestBody DailyQuestionRequestDTO.AnswerUpdateRequestDTO request
	) {
		return CommonResponse.success(dailyQuestionService.updateMyAnswer(memberId, teamId, questionId, request));
	}
}
