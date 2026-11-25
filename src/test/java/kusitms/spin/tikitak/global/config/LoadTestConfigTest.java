package kusitms.spin.tikitak.global.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import kusitms.spin.tikitak.service.notification.DailyQuestionOpenedNotificationBatchService;
import kusitms.spin.tikitak.service.notification.LoadTestDailyQuestionOpenedRunner;
import kusitms.spin.tikitak.service.notification.push.FcmPushSender;
import kusitms.spin.tikitak.service.notification.push.LoadTestPushSender;
import kusitms.spin.tikitak.service.notification.push.PushSender;
import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 측정 전용 빈(가짜 발송기, Runner, Properties)이 {@code loadtest & !prod} 프로필에서만 등록되는지,
 * 잘못된 설정이면 기동이 실패하는지 컨텍스트로 확인한다.
 * 컴포넌트 스캔과 같은 조건 평가를 받도록 빈 클래스를 {@code withUserConfiguration}으로 등록한다.
 */
class LoadTestConfigTest extends UnitTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(
					LoadTestConfig.class,
					FcmPushSender.class,
					LoadTestPushSender.class,
					LoadTestDailyQuestionOpenedRunner.class
			)
			.withBean(MeterRegistry.class, SimpleMeterRegistry::new)
			.withBean(DailyQuestionOpenedNotificationBatchService.class,
					() -> mock(DailyQuestionOpenedNotificationBatchService.class));

	@Test
	@DisplayName("프로필이 없으면 측정 전용 빈이 하나도 없고 PushSender는 FcmPushSender다")
	void registersNothingWithoutProfile() {
		runner.run(context -> {
			assertThat(context).hasNotFailed();
			assertNoLoadTestBeans(context);
			assertThat(context.getBean(PushSender.class)).isInstanceOf(FcmPushSender.class);
		});
	}

	@Test
	@DisplayName("loadtest 프로필이면 측정 전용 빈이 등록되고 PushSender 단일 주입은 LoadTestPushSender로 정해진다")
	void registersBeansWithLoadTestProfile() {
		runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("loadtest"))
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context).hasSingleBean(LoadTestProperties.class);
					assertThat(context).hasSingleBean(LoadTestPushSender.class);
					assertThat(context).hasSingleBean(LoadTestDailyQuestionOpenedRunner.class);
					assertThat(context).hasSingleBean(FcmPushSender.class);
					assertThat(context.getBeansOfType(PushSender.class)).hasSize(2);

					assertThat(context.getBean(PushSender.class)).isInstanceOf(LoadTestPushSender.class);
				});
	}

	@Test
	@DisplayName("loadtest 프로필에서 설정 키가 없으면 기본값(지연 0, 비율 0, seed 0, 기동 시 실행 안 함)으로 바인딩된다")
	void bindsDefaultsWithLoadTestProfile() {
		runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("loadtest"))
				.run(context -> {
					assertThat(context).hasNotFailed();

					LoadTestProperties properties = context.getBean(LoadTestProperties.class);
					assertThat(properties.push()).isEqualTo(new LoadTestProperties.Push(0L, 0.0, 0.0, 0L));
					assertThat(properties.dailyQuestionOpened().runOnStartup()).isFalse();
				});
	}

	@Test
	@DisplayName("loadtest 프로필에서 설정 값이 있으면 그대로 바인딩된다")
	void bindsGivenValuesWithLoadTestProfile() {
		runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("loadtest"))
				.withPropertyValues(
						"loadtest.push.delay-ms=50",
						"loadtest.push.failure-rate=0.2",
						"loadtest.push.invalid-token-rate=0.1",
						"loadtest.push.seed=7",
						"loadtest.daily-question-opened.run-on-startup=true"
				)
				.run(context -> {
					assertThat(context).hasNotFailed();

					LoadTestProperties properties = context.getBean(LoadTestProperties.class);
					assertThat(properties.push()).isEqualTo(new LoadTestProperties.Push(50L, 0.2, 0.1, 7L));
					assertThat(properties.dailyQuestionOpened().runOnStartup()).isTrue();
				});
	}

	@Test
	@DisplayName("loadtest와 prod 프로필이 함께 켜지면 측정 전용 빈이 하나도 없다")
	void registersNothingWithLoadTestAndProdProfiles() {
		runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("loadtest", "prod"))
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertNoLoadTestBeans(context);
					assertThat(context.getBean(PushSender.class)).isInstanceOf(FcmPushSender.class);
				});
	}

	@Test
	@DisplayName("loadtest 프로필에서 failure-rate와 invalid-token-rate의 합이 1을 넘으면 기동이 실패한다")
	void failsWhenRateSumExceedsOne() {
		runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("loadtest"))
				.withPropertyValues(
						"loadtest.push.failure-rate=0.6",
						"loadtest.push.invalid-token-rate=0.5"
				)
				.run(context -> {
					assertThat(context).hasFailed();
					assertThat(context.getStartupFailure()).rootCause()
							.hasMessageContaining("must be <= 1");
				});
	}

	@Test
	@DisplayName("loadtest 프로필에서 delay-ms가 음수면 기동이 실패한다")
	void failsWhenDelayIsNegative() {
		runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("loadtest"))
				.withPropertyValues("loadtest.push.delay-ms=-1")
				.run(context -> {
					assertThat(context).hasFailed();
					assertThat(context.getStartupFailure()).rootCause()
							.hasMessageContaining("loadtest.push.delay-ms must be >= 0");
				});
	}

	@Test
	@DisplayName("loadtest 프로필에서 rate가 0~1 밖이면 기동이 실패한다")
	void failsWhenRateIsOutOfRange() {
		runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("loadtest"))
				.withPropertyValues("loadtest.push.failure-rate=-0.1")
				.run(context -> {
					assertThat(context).hasFailed();
					assertThat(context.getStartupFailure()).rootCause()
							.hasMessageContaining("loadtest.push.failure-rate must be between 0 and 1");
				});
	}

	private static void assertNoLoadTestBeans(AssertableApplicationContext context) {
		assertThat(context).doesNotHaveBean(LoadTestProperties.class);
		assertThat(context).doesNotHaveBean(LoadTestPushSender.class);
		assertThat(context).doesNotHaveBean(LoadTestDailyQuestionOpenedRunner.class);
		assertThat(context).doesNotHaveBean(LoadTestConfig.class);
	}
}
