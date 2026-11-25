package kusitms.spin.tikitak.service.notification;

import kusitms.spin.tikitak.global.config.LoadTestProperties;
import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.boot.DefaultApplicationArguments;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class LoadTestDailyQuestionOpenedRunnerTest extends UnitTest {

	@Mock
	private DailyQuestionOpenedNotificationBatchService batchService;

	@Test
	@DisplayName("run-on-startup이 false면 배치를 부르지 않는다")
	void doesNotRunBatchWhenDisabled() {
		runner(false).run(new DefaultApplicationArguments());

		verify(batchService, never()).sendDailyQuestionOpenedNotifications();
	}

	@Test
	@DisplayName("run-on-startup이 true면 배치를 한 번 부른다")
	void runsBatchOnceWhenEnabled() {
		runner(true).run(new DefaultApplicationArguments());

		verify(batchService, times(1)).sendDailyQuestionOpenedNotifications();
	}

	private LoadTestDailyQuestionOpenedRunner runner(boolean runOnStartup) {
		LoadTestProperties properties = new LoadTestProperties(
				null, new LoadTestProperties.DailyQuestionOpened(runOnStartup));
		return new LoadTestDailyQuestionOpenedRunner(properties, batchService);
	}
}
