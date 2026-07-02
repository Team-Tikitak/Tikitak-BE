package kusitms.spin.tikitak.service.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kusitms.spin.tikitak.domain.notification.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

public class NotificationResponseDTO {

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class NotificationListResponseDTO {
		private List<NotificationListItemDTO> items;
		private PageInfoDTO pageInfo;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class NotificationListItemDTO {
		private Long notificationId;
		private NotificationType type;
		private String title;
		private String body;
		private Long teamId;
		private Long feedId;
		private boolean isRead;
		private LocalDateTime createdAt;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class PageInfoDTO {
		@Schema(description = "다음 페이지 조회용 커서", example = "2026-03-04T20:30:00_25", nullable = true)
		private String nextCursor;
		@Schema(description = "다음 페이지 존재 여부", example = "true")
		private boolean hasNext;
		@Schema(description = "현재 요청 페이지 크기", example = "20")
		private int size;
		@Schema(description = "전체 알림 수", example = "137")
		private long totalCount;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class UnreadCountResponseDTO {
		private long unreadCount;
	}
}
