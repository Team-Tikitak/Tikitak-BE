package kusitms.spin.tikitak.service.notification;

import kusitms.spin.tikitak.domain.notification.enums.NotificationType;
import kusitms.spin.tikitak.domain.notification.event.DailyAnswerPostedEvent;
import kusitms.spin.tikitak.domain.notification.event.FeedCommentCreatedEvent;
import kusitms.spin.tikitak.domain.notification.event.FeedCommentRepliedEvent;
import kusitms.spin.tikitak.service.notification.dto.NotificationPayload;
import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

class NotificationEventListenerTest extends UnitTest {

	@Mock
	private NotificationService notificationService;

	@InjectMocks
	private NotificationEventListener notificationEventListener;

	@Test
	@DisplayName("FeedCommentCreatedEvent sends notification with actor team member id")
	void handleFeedCommentCreatedSendsNotificationToRecipient() {
		FeedCommentCreatedEvent event = new FeedCommentCreatedEvent(1L, 7L, "actor", 10L, 100L);

		notificationEventListener.handleFeedCommentCreated(event);

		ArgumentCaptor<NotificationPayload> captor = ArgumentCaptor.forClass(NotificationPayload.class);
		verify(notificationService).send(eq(1L), captor.capture());

		NotificationPayload payload = captor.getValue();
		assertThat(payload.getType()).isEqualTo(NotificationType.FEED_COMMENT);
		assertThat(payload.getActorTeamMemberId()).isEqualTo(7L);
		assertThat(payload.getTitle()).isNotBlank();
		assertThat(payload.getBody()).contains("actor");
		assertThat(payload.getData()).containsEntry("feedId", "10");
		assertThat(payload.getData()).containsEntry("teamId", "100");
	}

	@Test
	@DisplayName("FeedCommentRepliedEvent sends notification with actor team member id")
	void handleFeedCommentRepliedSendsNotificationToRecipient() {
		FeedCommentRepliedEvent event = new FeedCommentRepliedEvent(5L, 7L, "actor", 10L, 100L);

		notificationEventListener.handleFeedCommentReplied(event);

		ArgumentCaptor<NotificationPayload> captor = ArgumentCaptor.forClass(NotificationPayload.class);
		verify(notificationService).send(eq(5L), captor.capture());

		NotificationPayload payload = captor.getValue();
		assertThat(payload.getType()).isEqualTo(NotificationType.FEED_COMMENT_REPLIED);
		assertThat(payload.getActorTeamMemberId()).isEqualTo(7L);
		assertThat(payload.getTitle()).isNotBlank();
		assertThat(payload.getBody()).contains("actor");
		assertThat(payload.getData()).containsEntry("feedId", "10");
		assertThat(payload.getData()).containsEntry("teamId", "100");
	}

	@Test
	@DisplayName("DailyAnswerPostedEvent sends notification with actor team member id")
	void handleDailyAnswerPostedSendsNotificationToAllRecipients() {
		DailyAnswerPostedEvent event = new DailyAnswerPostedEvent(List.of(2L, 3L), 7L, "actor", 20L, 200L);

		notificationEventListener.handleDailyAnswerPosted(event);

		ArgumentCaptor<NotificationPayload> captor = ArgumentCaptor.forClass(NotificationPayload.class);
		verify(notificationService).send(eq(2L), captor.capture());
		verify(notificationService).send(eq(3L), captor.capture());

		NotificationPayload payload = captor.getValue();
		assertThat(payload.getType()).isEqualTo(NotificationType.DAILY_QUESTION_UPLOADED);
		assertThat(payload.getActorTeamMemberId()).isEqualTo(7L);
		assertThat(payload.getBody()).contains("actor");
		assertThat(payload.getData()).containsEntry("feedId", "20");
		assertThat(payload.getData()).containsEntry("teamId", "200");
	}
}
