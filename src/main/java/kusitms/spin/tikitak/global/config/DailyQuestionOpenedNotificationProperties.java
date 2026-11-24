package kusitms.spin.tikitak.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.daily-question-opened")
public record DailyQuestionOpenedNotificationProperties(
		String cron
) {

	/**
	 * {@code @EnableConfigurationProperties}로 등록될 때 받는 빈 이름({@code <prefix>-<FQCN>}). {@code @Scheduled} cron SpEL에서 참조한다.
	 */
	public static final String BEAN_NAME =
			"notification.daily-question-opened-kusitms.spin.tikitak.global.config.DailyQuestionOpenedNotificationProperties";

	public static final String DEFAULT_CRON = "0 0 0 * * *";

	public DailyQuestionOpenedNotificationProperties {
		if (cron == null || cron.isBlank()) {
			cron = DEFAULT_CRON;
		}
	}
}
