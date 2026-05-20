package kusitms.spin.tikitak.controller.feed;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import kusitms.spin.tikitak.global.dto.CommonResponse;
import kusitms.spin.tikitak.global.security.CurrentMemberId;
import kusitms.spin.tikitak.service.feed.FeedCommentService;
import kusitms.spin.tikitak.service.feed.dto.FeedCommentRequestDTO;
import kusitms.spin.tikitak.service.feed.dto.FeedCommentResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/teams/{teamId}/feeds/{feedId}/comments")
@Tag(name = "Feed Comment", description = "피드 이미지 앵커 댓글 API")
@SecurityRequirement(name = "bearerAuth")
public class FeedCommentController {

	private final FeedCommentService feedCommentService;

	@GetMapping
	@Operation(summary = "피드 댓글 목록 조회", description = "피드 전체 또는 특정 피드 이미지의 앵커 댓글을 생성일시 오름차순으로 조회합니다.")
	public CommonResponse<FeedCommentResponseDTO.CommentListResponseDTO> listComments(
			@Parameter(hidden = true) @CurrentMemberId Long memberId,
			@PathVariable Long teamId,
			@PathVariable Long feedId,
			@RequestParam(required = false) Long feedImageId,
			@RequestParam(required = false) String cursor,
			@RequestParam(required = false) Integer size
	) {
		FeedCommentResponseDTO.CommentListResponseDTO response =
				feedCommentService.listComments(memberId, teamId, feedId, feedImageId, cursor, size);
		return CommonResponse.success(response);
	}

	@PostMapping
	@Operation(summary = "피드 댓글 작성", description = "피드 이미지의 특정 위치에 앵커 댓글을 작성합니다.")
	public ResponseEntity<CommonResponse<FeedCommentResponseDTO.CommentItemDTO>> createComment(
			@Parameter(hidden = true) @CurrentMemberId Long memberId,
			@PathVariable Long teamId,
			@PathVariable Long feedId,
			@RequestBody FeedCommentRequestDTO.CommentCreateRequestDTO request
	) {
		FeedCommentResponseDTO.CommentItemDTO response =
				feedCommentService.createComment(memberId, teamId, feedId, request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(CommonResponse.successWithStatus(response, HttpStatus.CREATED.value()));
	}

	@PatchMapping("/{commentId}")
	@Operation(summary = "피드 댓글 수정", description = "본인이 작성한 앵커 댓글의 내용 또는 같은 이미지 내 위치를 수정합니다.")
	public CommonResponse<FeedCommentResponseDTO.CommentItemDTO> updateComment(
			@Parameter(hidden = true) @CurrentMemberId Long memberId,
			@PathVariable Long teamId,
			@PathVariable Long feedId,
			@PathVariable Long commentId,
			@RequestBody FeedCommentRequestDTO.CommentUpdateRequestDTO request
	) {
		FeedCommentResponseDTO.CommentItemDTO response =
				feedCommentService.updateComment(memberId, teamId, feedId, commentId, request);
		return CommonResponse.success(response);
	}

	@DeleteMapping("/{commentId}")
	@Operation(summary = "피드 댓글 삭제", description = "본인이 작성한 앵커 댓글을 hard delete 합니다.")
	public CommonResponse<Void> deleteComment(
			@Parameter(hidden = true) @CurrentMemberId Long memberId,
			@PathVariable Long teamId,
			@PathVariable Long feedId,
			@PathVariable Long commentId
	) {
		feedCommentService.deleteComment(memberId, teamId, feedId, commentId);
		return CommonResponse.success(null);
	}
}
