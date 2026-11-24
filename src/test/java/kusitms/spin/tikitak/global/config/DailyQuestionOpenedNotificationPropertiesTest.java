package kusitms.spin.tikitak.global.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import kusitms.spin.tikitak.repository.team.TeamMemberRepository;
import kusitms.spin.tikitak.service.notification.DailyQuestionOpenedNotificationBatchService;
import kusitms.spin.tikitak.service.notification.NotificationService;
import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DailyQuestionOpenedNotificationPropertiesTest extends UnitTest {

	private final ApplicationContextRunner propertiesRunner = new ApplicationContextRunner()
			.withUserConfiguration(PropertiesOnlyConfig.class);

	/**
	 * {@link PropertiesConfig}와 같은 {@code @EnableConfigurationProperties} 등록 방식으로 Properties 빈을 올리고,
	 * 실제 배치 빈의 {@code @Scheduled} SpEL이 그 빈을 참조하는지 본다.
	 * {@link PropertiesConfig}를 그대로 쓰면 다른 Properties(R2 등)의 필수 값까지 필요해서 이 Properties만 등록한다.
	 */
	private final ApplicationContextRunner schedulingRunner = new ApplicationContextRunner()
			.withUserConfiguration(PropertiesOnlyConfig.class, SchedulingConfig.class)
			.withBean(TeamMemberRepository.class, () -> mock(TeamMemberRepository.class))
			.withBean(NotificationService.class, () -> mock(NotificationService.class))
			.withBean(MeterRegistry.class, SimpleMeterRegistry::new)
			.withBean(DailyQuestionOpenedNotificationBatchService.class);

	@Test
	@DisplayName("설정 키가 없으면 바인딩된 빈의 cron은 매일 자정이다")
	void bindsDefaultCronWhenKeyIsAbsent() {
		propertiesRunner.run(context -> {
			assertThat(context).hasNotFailed();

			assertThat(context.getBean(DailyQuestionOpenedNotificationProperties.class).cron())
					.isEqualTo("0 0 0 * * *");
		});
	}

	@Test
	@DisplayName("설정 값이 빈 문자열이면 바인딩된 빈의 cron은 매일 자정이다")
	void bindsDefaultCronWhenValueIsEmpty() {
		propertiesRunner
				.withPropertyValues("notification.daily-question-opened.cron=")
				.run(context -> {
					assertThat(context).hasNotFailed();

					assertThat(context.getBean(DailyQuestionOpenedNotificationProperties.class).cron())
							.isEqualTo("0 0 0 * * *");
				});
	}

	@Test
	@DisplayName("설정 값이 있으면 바인딩된 빈이 그 cron을 갖는다")
	void bindsGivenCron() {
		propertiesRunner
				.withPropertyValues("notification.daily-question-opened.cron=0 30 9 * * *")
				.run(context -> {
					assertThat(context).hasNotFailed();

					assertThat(context.getBean(DailyQuestionOpenedNotificationProperties.class).cron())
							.isEqualTo("0 30 9 * * *");
				});
	}

	@Test
	@DisplayName("@EnableConfigurationProperties로 등록된 빈 이름은 <prefix>-<FQCN>이다")
	void registersBeanWithPrefixAndFqcnName() {
		propertiesRunner.run(context -> {
			assertThat(context).hasNotFailed();

			assertThat(context.getBeanNamesForType(DailyQuestionOpenedNotificationProperties.class))
					.containsExactly(DailyQuestionOpenedNotificationProperties.BEAN_NAME);
			assertThat(DailyQuestionOpenedNotificationProperties.BEAN_NAME).isEqualTo(
					"notification.daily-question-opened-"
							+ "kusitms.spin.tikitak.global.config.DailyQuestionOpenedNotificationProperties"
			);
		});
	}

	@Test
	@DisplayName("cron을 설정하면 배치가 그 표현식의 cron 작업으로 등록된다")
	void registersCronTaskWithConfiguredExpression() {
		schedulingRunner
				.withPropertyValues("notification.daily-question-opened.cron=0 30 9 * * *")
				.run(context -> {
					assertThat(context).hasNotFailed();

					assertThat(cronExpressions(context.getBean(ScheduledTaskHolder.class)))
							.containsExactly("0 30 9 * * *");
				});
	}

	@Test
	@DisplayName("cron 키가 없으면 배치가 기본값(매일 자정) cron 작업으로 등록된다")
	void registersCronTaskWithDefaultExpression() {
		schedulingRunner.run(context -> {
			assertThat(context).hasNotFailed();

			assertThat(cronExpressions(context.getBean(ScheduledTaskHolder.class)))
					.containsExactly("0 0 0 * * *");
		});
	}

	@Test
	@DisplayName("cron이 -이면 배치가 스케줄에 등록되지 않는다")
	void doesNotRegisterTaskWhenCronIsDisabled() {
		schedulingRunner
				.withPropertyValues("notification.daily-question-opened.cron=-")
				.run(context -> {
					assertThat(context).hasNotFailed();

					assertThat(context.getBean(ScheduledTaskHolder.class).getScheduledTasks()).isEmpty();
				});
	}

	private List<String> cronExpressions(ScheduledTaskHolder holder) {
		return holder.getScheduledTasks().stream()
				.map(ScheduledTask::getTask)
				.filter(CronTask.class::isInstance)
				.map(task -> ((CronTask)task).getExpression())
				.toList();
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(DailyQuestionOpenedNotificationProperties.class)
	static class PropertiesOnlyConfig {
	}

	@Configuration(proxyBeanMethods = false)
	@EnableScheduling
	static class SchedulingConfig {
	}
}
