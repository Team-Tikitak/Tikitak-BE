package kusitms.spin.tikitak.domain.notification.event;

import java.util.List;

public record FeedPostedEvent(
		List<Long> recipientMemberIds,
		Long actorTeamMemberId,
		String actorNickname,
		Long feedId,
		Long teamId
) {
}
