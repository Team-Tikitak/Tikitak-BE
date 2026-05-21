package kusitms.spin.tikitak.controller.feed;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kusitms.spin.tikitak.global.dto.CommonResponse;
import kusitms.spin.tikitak.global.security.CurrentMemberId;
import kusitms.spin.tikitak.service.feed.FeedService;
import kusitms.spin.tikitak.service.feed.dto.FeedRequestDTO;
import kusitms.spin.tikitak.service.feed.dto.FeedResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/teams/{teamId}/feeds")
@Tag(name = "Feed", description = "피드 작성, 조회, 수정, 삭제 및 반응 API")
@SecurityRequirement(name = "bearerAuth")
public class FeedController {

	private final FeedService feedService;

	@GetMapping
	@Operation(
			summary = "피드 목록 조회",
			description = "특정 팀의 피드 목록을 생성일시 내림차순으로 조회합니다. placeId를 전달하면 해당 장소의 피드만 조회합니다."
	)
	public CommonResponse<FeedResponseDTO.FeedListResponseDTO> listFeeds(
			@Parameter(hidden = true) @CurrentMemberId Long memberId,
			@Parameter(description = "피드 목록을 조회할 팀 ID", required = true)
			@PathVariable Long teamId,
			@Parameter(description = "다음 페이지 조회용 커서. 형식: createdAt_feedId")
			@RequestParam(required = false) String cursor,
			@Parameter(description = "조회 개수. 기본값 20, 최대 50")
			@RequestParam(required = false) Integer size,
			@Parameter(description = "특정 장소의 피드만 조회할 외부 장소 ID")
			@RequestParam(required = false) String placeId,
			@Parameter(description = "특정 지역의 피드만 조회할 행정구역명 (예: 서울 강남구). placeId와 동시에 사용 시 placeId 우선")
			@RequestParam(required = false) String region,
    	@Parameter(description = "피드 유형 필터. ALL, GENERAL, DAILY_QUESTION")
			@RequestParam(required = false) String type,
			@Parameter(description = "태그된 팀 멤버 ID 목록. 모든 ID가 동시에 태그된 피드만 조회")
			@RequestParam(required = false) List<Long> taggedTeamMemberIds
	) {
		FeedResponseDTO.FeedListResponseDTO response = feedService.listFeeds(
				memberId, teamId, cursor, size, placeId, region, type, taggedTeamMemberIds);
		return CommonResponse.success(response);
	}

	@GetMapping("/{feedId}")
	@Operation(
			summary = "피드 상세 조회",
			description = "특정 피드의 상세 정보, 이미지 목록, 장소, 태그된 팀원, 댓글 수, 반응 요약과 내 반응을 조회합니다."
	)
	public CommonResponse<FeedResponseDTO.FeedDetailResponseDTO> getFeed(
			@Parameter(hidden = true) @CurrentMemberId Long memberId,
			@Parameter(description = "팀 ID", required = true)
			@PathVariable Long teamId,
			@Parameter(description = "조회할 피드 ID", required = true)
			@PathVariable Long feedId
	) {
		FeedResponseDTO.FeedDetailResponseDTO response = feedService.getFeed(memberId, teamId, feedId);
		return CommonResponse.success(response);
	}

	@PostMapping
	@Operation(
			summary = "일반 피드 작성",
			description = "이미지 1장 이상 10장 이하를 첨부해 일반 피드를 작성합니다. 장소는 최대 1개, 팀원 태그는 최대 11명까지 등록할 수 있습니다."
	)
	public ResponseEntity<CommonResponse<FeedResponseDTO.FeedMutationResponseDTO>> createFeed(
			@Parameter(hidden = true) @CurrentMemberId Long memberId,
			@Parameter(description = "피드를 작성할 팀 ID", required = true)
			@PathVariable Long teamId,
			@Parameter(description = "피드 작성 요청", required = true)
			@Valid @RequestBody FeedRequestDTO.FeedCreateRequestDTO request
	) {
		FeedResponseDTO.FeedMutationResponseDTO response = feedService.createFeed(memberId, teamId, request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(CommonResponse.successWithStatus(response, HttpStatus.CREATED.value()));
	}

	@PatchMapping("/{feedId}")
	@Operation(
			summary = "피드 수정",
			description = "작성자가 피드의 본문, 이미지 목록, 장소, 태그 팀원을 최종 상태 기준으로 수정합니다."
	)
	public CommonResponse<FeedResponseDTO.FeedMutationResponseDTO> updateFeed(
			@Parameter(hidden = true) @CurrentMemberId Long memberId,
			@Parameter(description = "팀 ID", required = true)
			@PathVariable Long teamId,
			@Parameter(description = "수정할 피드 ID", required = true)
			@PathVariable Long feedId,
			@Parameter(description = "피드 수정 요청", required = true)
			@Valid @RequestBody FeedRequestDTO.FeedUpdateRequestDTO request
	) {
		FeedResponseDTO.FeedMutationResponseDTO response = feedService.updateFeed(memberId, teamId, feedId, request);
		return CommonResponse.success(response);
	}

	@DeleteMapping("/{feedId}")
	@Operation(
			summary = "피드 삭제",
			description = "작성자가 피드를 삭제합니다. 삭제된 피드와 연결 이미지는 7일 보존 후 배치로 hard delete 됩니다."
	)
	public CommonResponse<Void> deleteFeed(
			@Parameter(hidden = true) @CurrentMemberId Long memberId,
			@Parameter(description = "팀 ID", required = true)
			@PathVariable Long teamId,
			@Parameter(description = "삭제할 피드 ID", required = true)
			@PathVariable Long feedId
	) {
		feedService.deleteFeed(memberId, teamId, feedId);
		return CommonResponse.success(null);
	}

	@PutMapping("/{feedId}/reaction")
	@Operation(
			summary = "피드 반응 등록 또는 변경",
			description = "피드에 내 반응을 등록하거나 기존 반응을 다른 반응으로 변경합니다. 한 사용자는 한 피드에 하나의 반응만 가질 수 있습니다."
	)
	public CommonResponse<FeedResponseDTO.FeedReactionResponseDTO> upsertReaction(
			@Parameter(hidden = true) @CurrentMemberId Long memberId,
			@Parameter(description = "팀 ID", required = true)
			@PathVariable Long teamId,
			@Parameter(description = "반응을 등록하거나 변경할 피드 ID", required = true)
			@PathVariable Long feedId,
			@Parameter(description = "피드 반응 요청", required = true)
			@Valid @RequestBody FeedRequestDTO.ReactionRequestDTO request
	) {
		FeedResponseDTO.FeedReactionResponseDTO response = feedService.upsertReaction(memberId, teamId, feedId, request);
		return CommonResponse.success(response);
	}

	@DeleteMapping("/{feedId}/reaction")
	@Operation(
			summary = "피드 반응 취소",
			description = "피드에 등록한 내 반응을 취소합니다. 이미 반응이 없어도 성공 응답을 반환합니다."
	)
	public CommonResponse<FeedResponseDTO.FeedReactionResponseDTO> deleteReaction(
			@Parameter(hidden = true) @CurrentMemberId Long memberId,
			@Parameter(description = "팀 ID", required = true)
			@PathVariable Long teamId,
			@Parameter(description = "반응을 취소할 피드 ID", required = true)
			@PathVariable Long feedId
	) {
		FeedResponseDTO.FeedReactionResponseDTO response = feedService.deleteReaction(memberId, teamId, feedId);
		return CommonResponse.success(response);
	}
}
