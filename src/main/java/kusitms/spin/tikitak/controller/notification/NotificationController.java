package kusitms.spin.tikitak.controller.notification;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import kusitms.spin.tikitak.global.dto.CommonResponse;
import kusitms.spin.tikitak.global.security.CurrentMemberId;
import kusitms.spin.tikitak.service.notification.NotificationService;
import kusitms.spin.tikitak.service.notification.dto.NotificationResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/me/notifications")
@Tag(name = "Notification", description = "알림 조회 및 읽음 처리 API")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

	private final NotificationService notificationService;

	@GetMapping
	@Operation(
			summary = "알림 목록 조회",
			description = "현재 로그인한 사용자의 알림 목록을 생성일시 내림차순으로 조회합니다. teamId를 전달하면 해당 팀에서 발생한 알림만 조회합니다."
	)
	public CommonResponse<NotificationResponseDTO.NotificationListResponseDTO> listNotifications(
			@Parameter(hidden = true) @CurrentMemberId Long memberId,
			@Parameter(description = "조회할 팀 ID. 전달 시 해당 팀 알림만 조회합니다.")
			@RequestParam(required = false) Long teamId,
			@Parameter(description = "다음 페이지 조회용 커서. 형식: createdAt_notificationId")
			@RequestParam(required = false) String cursor,
			@Parameter(description = "조회 개수. 기본값 20, 최대 50")
			@RequestParam(required = false) Integer size
	) {
		return CommonResponse.success(notificationService.listNotifications(memberId, teamId, cursor, size));
	}

	@GetMapping("/unread-count")
	@Operation(
			summary = "안읽은 알림 개수 조회",
			description = "현재 로그인한 사용자의 안읽은 알림 개수를 조회합니다. teamId를 전달하면 해당 팀의 안읽은 알림 개수만 조회합니다."
	)
	public CommonResponse<NotificationResponseDTO.UnreadCountResponseDTO> getUnreadCount(
			@Parameter(hidden = true) @CurrentMemberId Long memberId,
			@Parameter(description = "조회할 팀 ID. 전달 시 해당 팀 안읽은 알림 개수만 조회합니다.")
			@RequestParam(required = false) Long teamId
	) {
		return CommonResponse.success(notificationService.getUnreadCount(memberId, teamId));
	}

	@PatchMapping("/{notificationId}/read")
	@Operation(
			summary = "알림 읽음 처리",
			description = "현재 로그인한 사용자의 특정 알림을 읽음 처리합니다."
	)
	public CommonResponse<Void> markAsRead(
			@Parameter(hidden = true) @CurrentMemberId Long memberId,
			@Parameter(description = "읽음 처리할 알림 ID", required = true)
			@PathVariable Long notificationId
	) {
		notificationService.markAsRead(memberId, notificationId);
		return CommonResponse.success(null);
	}

	@PatchMapping("/read-all")
	@Operation(
			summary = "전체 알림 읽음 처리",
			description = "현재 로그인한 사용자의 안읽은 알림을 모두 읽음 처리합니다."
	)
	public CommonResponse<Void> markAllAsRead(
			@Parameter(hidden = true) @CurrentMemberId Long memberId
	) {
		notificationService.markAllAsRead(memberId);
		return CommonResponse.success(null);
	}
}
