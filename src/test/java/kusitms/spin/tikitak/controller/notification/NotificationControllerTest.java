package kusitms.spin.tikitak.controller.notification;

import kusitms.spin.tikitak.domain.notification.enums.NotificationType;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.service.notification.NotificationService;
import kusitms.spin.tikitak.service.notification.dto.NotificationResponseDTO;
import kusitms.spin.tikitak.support.ApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationControllerTest extends ApiTest {

	@Mock
	private NotificationService notificationService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = mockMvc(new NotificationController(notificationService));
	}

	@Test
	@DisplayName("GET /api/v1/me/notifications는 알림 목록을 반환한다")
	void listNotifications() throws Exception {
		NotificationResponseDTO.NotificationListItemDTO item = NotificationResponseDTO.NotificationListItemDTO.builder()
				.notificationId(1L)
				.type(NotificationType.FEED_COMMENT)
				.title("title")
				.body("body")
				.teamId(10L)
				.feedId(20L)
				.profileImageUrl("https://example.com/profile.png")
				.thumbnailImageUrl("https://example.com/feed-thumb.png")
				.heroPreviewUrl("https://example.com/feed-hero.png")
				.isRead(false)
				.createdAt(LocalDateTime.of(2026, 3, 4, 20, 30))
				.build();
		NotificationResponseDTO.NotificationListResponseDTO response = NotificationResponseDTO.NotificationListResponseDTO.builder()
				.items(List.of(item))
				.pageInfo(NotificationResponseDTO.PageInfoDTO.builder()
						.hasNext(false)
						.size(20)
						.totalCount(1L)
						.build())
				.build();
		when(notificationService.listNotifications(eq(TEST_MEMBER_ID), isNull(), isNull(), isNull())).thenReturn(response);

		mockMvc.perform(get("/api/v1/me/notifications"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.items[0].notificationId").value(1L))
				.andExpect(jsonPath("$.data.items[0].profileImageUrl").value("https://example.com/profile.png"))
				.andExpect(jsonPath("$.data.items[0].thumbnailImageUrl").value("https://example.com/feed-thumb.png"))
				.andExpect(jsonPath("$.data.items[0].heroPreviewUrl").value("https://example.com/feed-hero.png"))
				.andExpect(jsonPath("$.data.pageInfo.totalCount").value(1L));
	}

	@Test
	@DisplayName("GET /api/v1/me/notifications?teamId=10은 해당 팀 알림만 조회한다")
	void listNotificationsFilteredByTeamId() throws Exception {
		NotificationResponseDTO.NotificationListResponseDTO response = NotificationResponseDTO.NotificationListResponseDTO.builder()
				.items(List.of())
				.pageInfo(NotificationResponseDTO.PageInfoDTO.builder()
						.hasNext(false)
						.size(20)
						.totalCount(0L)
						.build())
				.build();
		when(notificationService.listNotifications(eq(TEST_MEMBER_ID), eq(10L), isNull(), isNull())).thenReturn(response);

		mockMvc.perform(get("/api/v1/me/notifications").param("teamId", "10"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.pageInfo.totalCount").value(0L));
	}

	@Test
	@DisplayName("GET /api/v1/me/notifications/unread-count는 안읽은 알림 개수를 반환한다")
	void getUnreadCount() throws Exception {
		when(notificationService.getUnreadCount(eq(TEST_MEMBER_ID), isNull())).thenReturn(
				NotificationResponseDTO.UnreadCountResponseDTO.builder().unreadCount(3L).build());

		mockMvc.perform(get("/api/v1/me/notifications/unread-count"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.unreadCount").value(3L));
	}

	@Test
	@DisplayName("GET /api/v1/me/notifications/unread-count?teamId=10은 해당 팀의 안읽은 알림 개수를 반환한다")
	void getUnreadCountFilteredByTeamId() throws Exception {
		when(notificationService.getUnreadCount(eq(TEST_MEMBER_ID), eq(10L))).thenReturn(
				NotificationResponseDTO.UnreadCountResponseDTO.builder().unreadCount(2L).build());

		mockMvc.perform(get("/api/v1/me/notifications/unread-count").param("teamId", "10"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.unreadCount").value(2L));
	}

	@Test
	@DisplayName("PATCH /api/v1/me/notifications/{id}/read는 알림을 읽음 처리한다")
	void markAsRead() throws Exception {
		mockMvc.perform(patch("/api/v1/me/notifications/1/read"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));

		verify(notificationService).markAsRead(TEST_MEMBER_ID, 1L);
	}

	@Test
	@DisplayName("PATCH /api/v1/me/notifications/{id}/read는 존재하지 않는 알림이면 404를 반환한다")
	void markAsReadReturnsNotFoundWhenNotificationMissing() throws Exception {
		doThrow(new BusinessException(ErrorCode.NOTIFICATION001))
				.when(notificationService).markAsRead(TEST_MEMBER_ID, 999L);

		mockMvc.perform(patch("/api/v1/me/notifications/999/read"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.success").value(false));
	}

	@Test
	@DisplayName("PATCH /api/v1/me/notifications/read-all은 전체 알림을 읽음 처리한다")
	void markAllAsRead() throws Exception {
		mockMvc.perform(patch("/api/v1/me/notifications/read-all"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));

		verify(notificationService).markAllAsRead(TEST_MEMBER_ID, null);
	}

	@Test
	@DisplayName("PATCH /api/v1/me/notifications/read-all?teamId=10은 해당 팀 알림만 읽음 처리한다")
	void markAllAsReadFilteredByTeamId() throws Exception {
		mockMvc.perform(patch("/api/v1/me/notifications/read-all").param("teamId", "10"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));

		verify(notificationService).markAllAsRead(TEST_MEMBER_ID, 10L);
	}
}
