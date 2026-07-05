package kusitms.spin.tikitak.domain.notification.event;

public record FeedCommentRepliedEvent(
		Long recipientMemberId,
		Long actorTeamMemberId,
		String actorNickname,
		Long feedId,
		Long teamId
) {
}
