package kusitms.spin.tikitak.domain.notification.event;

public record FeedCommentRepliedEvent(
		Long recipientMemberId,
		String actorNickname,
		Long feedId,
		Long teamId
) {
}
