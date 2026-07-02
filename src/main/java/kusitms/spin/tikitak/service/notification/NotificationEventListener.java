package kusitms.spin.tikitak.service.notification;

import kusitms.spin.tikitak.domain.notification.enums.NotificationType;
import kusitms.spin.tikitak.domain.notification.event.DailyAnswerPostedEvent;
import kusitms.spin.tikitak.domain.notification.event.FeedCommentCreatedEvent;
import kusitms.spin.tikitak.domain.notification.event.FeedCommentRepliedEvent;
import kusitms.spin.tikitak.service.notification.dto.NotificationPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

	private final NotificationService notificationService;

	@Async("notificationExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handleFeedCommentCreated(FeedCommentCreatedEvent event) {
		NotificationPayload payload = NotificationPayload.builder()
				.type(NotificationType.FEED_COMMENT)
				.title("새 댓글이 도착했어요")
				.body(event.actorNickname() + "님이 댓글을 남겼어요.")
				.data(Map.of(
						"feedId", String.valueOf(event.feedId()),
						"teamId", String.valueOf(event.teamId())
				))
				.build();

		try {
			notificationService.send(event.recipientMemberId(), payload);
		} catch (Exception e) {
			log.error("알림 전송 중 예외가 발생했습니다. memberId={}, type={}", event.recipientMemberId(), payload.getType(), e);
		}
	}

	@Async("notificationExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handleFeedCommentReplied(FeedCommentRepliedEvent event) {
		NotificationPayload payload = NotificationPayload.builder()
				.type(NotificationType.FEED_COMMENT_REPLIED)
				.title("새 댓글이 도착했어요")
				.body(event.actorNickname() + "님이 회원님의 댓글에 답글을 남겼어요.")
				.data(Map.of(
						"feedId", String.valueOf(event.feedId()),
						"teamId", String.valueOf(event.teamId())
				))
				.build();

		try {
			notificationService.send(event.recipientMemberId(), payload);
		} catch (Exception e) {
			log.error("알림 전송 중 예외가 발생했습니다. memberId={}, type={}", event.recipientMemberId(), payload.getType(), e);
		}
	}

	@Async("notificationExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handleDailyAnswerPosted(DailyAnswerPostedEvent event) {
		NotificationPayload payload = NotificationPayload.builder()
				.type(NotificationType.DAILY_QUESTION_UPLOADED)
				.title("오늘의 질문 답변이 올라왔어요")
				.body(event.actorNickname() + "님이 오늘의 질문에 답변했어요.")
				.data(Map.of(
						"feedId", String.valueOf(event.feedId()),
						"teamId", String.valueOf(event.teamId())
				))
				.build();

		event.recipientMemberIds().forEach(memberId -> {
			try {
				notificationService.send(memberId, payload);
			} catch (Exception e) {
				log.error("알림 전송 중 예외가 발생했습니다. memberId={}, type={}", memberId, payload.getType(), e);
			}
		});
	}
}
