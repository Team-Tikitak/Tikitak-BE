package kusitms.spin.tikitak.domain.notification.event;

public record FeedCommentCreatedEvent(
		Long recipientMemberId,
		Long actorTeamMemberId,
		String actorNickname,
		Long feedId,
		Long teamId
) {
}
