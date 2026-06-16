package kusitms.spin.tikitak.service.notification.dto;

import kusitms.spin.tikitak.domain.notification.enums.NotificationType;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class NotificationPayload {

	private final NotificationType type;
	private final String title;
	private final String body;

	@Builder.Default
	private final Map<String, String> data = Map.of();
}
