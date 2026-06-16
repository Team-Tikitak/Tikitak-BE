package kusitms.spin.tikitak.domain.notification.event;

public record FeedCommentCreatedEvent(
		Long recipientMemberId,
		String actorNickname,
		Long feedId,
		Long teamId
) {
}
