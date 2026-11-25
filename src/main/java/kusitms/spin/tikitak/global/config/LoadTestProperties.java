package kusitms.spin.tikitak.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 로컬 부하 측정 전용 설정(#215). {@link LoadTestConfig}가 {@code loadtest & !prod} 프로필에서만 등록하므로,
 * 다른 프로필에는 이 빈이 없다. 값이 잘못되면 바인딩이 실패해 기동이 멈춘다.
 */
@ConfigurationProperties(prefix = "loadtest")
public record LoadTestProperties(
		Push push,
		DailyQuestionOpened dailyQuestionOpened
) {

	public LoadTestProperties {
		if (push == null) {
			push = new Push(0L, 0.0, 0.0, 0L);
		}
		if (dailyQuestionOpened == null) {
			dailyQuestionOpened = new DailyQuestionOpened(false);
		}
	}

	/**
	 * 가짜 푸시 발송기 설정.
	 *
	 * @param delayMs          {@code send} 호출(회원 한 명)마다 한 번 쉬는 시간. 0 이상
	 * @param failureRate      토큰별 {@code FAILED} 비율. 0~1
	 * @param invalidTokenRate 토큰별 {@code INVALID_TOKEN} 비율. 0~1, {@code failureRate}와 합이 1 이하
	 * @param seed             토큰별 결과를 정하는 해시의 시드
	 */
	public record Push(
			long delayMs,
			double failureRate,
			double invalidTokenRate,
			long seed
	) {

		public Push {
			if (delayMs < 0) {
				throw new IllegalArgumentException("loadtest.push.delay-ms must be >= 0. value=" + delayMs);
			}
			if (!isRate(failureRate)) {
				throw new IllegalArgumentException("loadtest.push.failure-rate must be between 0 and 1. value=" + failureRate);
			}
			if (!isRate(invalidTokenRate)) {
				throw new IllegalArgumentException(
						"loadtest.push.invalid-token-rate must be between 0 and 1. value=" + invalidTokenRate);
			}
			if (failureRate + invalidTokenRate > 1.0) {
				throw new IllegalArgumentException(
						"loadtest.push.failure-rate + loadtest.push.invalid-token-rate must be <= 1. failureRate="
								+ failureRate + ", invalidTokenRate=" + invalidTokenRate);
			}
		}

		private static boolean isRate(double value) {
			return value >= 0.0 && value <= 1.0;
		}
	}

	/**
	 * @param runOnStartup true면 기동 직후 오늘의 질문 공개 알림 배치를 한 번 실행한다
	 */
	public record DailyQuestionOpened(
			boolean runOnStartup
	) {
	}
}
