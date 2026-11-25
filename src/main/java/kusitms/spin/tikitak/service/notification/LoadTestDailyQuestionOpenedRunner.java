package kusitms.spin.tikitak.service.notification;

import kusitms.spin.tikitak.global.config.LoadTestConfig;
import kusitms.spin.tikitak.global.config.LoadTestProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 로컬 부하 측정(#215) 전용. {@code loadtest.daily-question-opened.run-on-startup=true}면 기동 직후 오늘의 질문 공개 알림
 * 배치를 한 번 실행한다. 배치 javadoc대로 트랜잭션 없이 부른다. 스케줄(cron)은 건드리지 않는다.
 * docs/decisions/2026-11-24-push-sender-loadtest-selection.md
 */
@Slf4j
@Component
@Profile(LoadTestConfig.PROFILE)
@RequiredArgsConstructor
public class LoadTestDailyQuestionOpenedRunner implements ApplicationRunner {

	private final LoadTestProperties properties;
	private final DailyQuestionOpenedNotificationBatchService batchService;

	@Override
	public void run(ApplicationArguments args) {
		if (!properties.dailyQuestionOpened().runOnStartup()) {
			log.info("Load test daily question opened batch is not run on startup.");
			return;
		}
		log.info("Load test daily question opened batch run started.");
		batchService.sendDailyQuestionOpenedNotifications();
		log.info("Load test daily question opened batch run finished.");
	}
}
