package kusitms.spin.tikitak.domain.notification.event;

import java.util.List;

public record DailyAnswerPostedEvent(
		List<Long> recipientMemberIds,
		String actorNickname,
		Long feedId,
		Long teamId
) {
}
