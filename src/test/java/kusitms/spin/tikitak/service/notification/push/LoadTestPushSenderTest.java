package kusitms.spin.tikitak.service.notification.push;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import kusitms.spin.tikitak.global.config.LoadTestProperties;
import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class LoadTestPushSenderTest extends UnitTest {

	private static final PushMessage MESSAGE = new PushMessage("title", "body", Map.of());

	private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

	@AfterEach
	void clearInterruptFlag() {
		// 인터럽트 테스트가 실패해도 다음 테스트에 플래그가 남지 않게 한다
		Thread.interrupted();
	}

	@Test
	@DisplayName("isAvailable은 항상 true다")
	void isAlwaysAvailable() {
		assertThat(sender(0, 0.0, 0.0, 0).isAvailable()).isTrue();
	}

	@Test
	@DisplayName("빈 입력이면 빈 리스트를 돌려주고 카운터를 올리지 않는다")
	void returnsEmptyListForEmptyInput() {
		LoadTestPushSender sender = sender(0, 0.5, 0.5, 0);

		assertThat(sender.send(List.of(), MESSAGE)).isEmpty();
		assertThat(count(PushStatus.SUCCESS) + count(PushStatus.FAILED) + count(PushStatus.INVALID_TOKEN)).isZero();
	}

	@Test
	@DisplayName("결과는 입력과 크기·순서가 같다: 각 위치의 결과가 그 토큰 하나만 보낸 결과와 같다")
	void resultsKeepInputSizeAndOrder() {
		LoadTestPushSender sender = sender(0, 0.3, 0.3, 7);
		List<String> tokens = tokens(200);

		List<PushResult> results = sender.send(tokens, MESSAGE);

		assertThat(results).hasSize(tokens.size());
		// 세 상태가 모두 섞여 있어야 순서 비교가 의미 있다
		assertThat(results).extracting(PushResult::status)
				.contains(PushStatus.SUCCESS, PushStatus.FAILED, PushStatus.INVALID_TOKEN);
		for (int i = 0; i < tokens.size(); i++) {
			assertThat(results.get(i)).isEqualTo(sender.send(List.of(tokens.get(i)), MESSAGE).get(0));
		}
		List<String> reversed = tokens.reversed();
		List<PushResult> reversedResults = sender.send(reversed, MESSAGE);
		assertThat(reversedResults).isEqualTo(results.reversed());
	}

	@Test
	@DisplayName("같은 설정·같은 입력이면 다른 인스턴스에서도 같은 결과다")
	void isDeterministic() {
		List<String> tokens = tokens(500);

		List<PushResult> first = sender(0, 0.2, 0.1, 42).send(tokens, MESSAGE);
		List<PushResult> second = sender(0, 0.2, 0.1, 42).send(tokens, MESSAGE);

		assertThat(second).isEqualTo(first);
	}

	@Test
	@DisplayName("seed가 다르면 토큰별 결과가 달라진다")
	void seedChangesResults() {
		List<String> tokens = tokens(500);

		List<PushResult> seed1 = sender(0, 0.3, 0.3, 1).send(tokens, MESSAGE);
		List<PushResult> seed2 = sender(0, 0.3, 0.3, 2).send(tokens, MESSAGE);

		assertThat(seed2).isNotEqualTo(seed1);
	}

	@Test
	@DisplayName("rate가 모두 0이면 전부 SUCCESS다")
	void allSuccessWhenRatesAreZero() {
		List<PushResult> results = sender(0, 0.0, 0.0, 0).send(tokens(1_000), MESSAGE);

		assertThat(results).extracting(PushResult::status).containsOnly(PushStatus.SUCCESS);
		assertThat(count(PushStatus.SUCCESS)).isEqualTo(1_000);
	}

	@Test
	@DisplayName("invalid-token-rate가 1이면 전부 INVALID_TOKEN이다")
	void allInvalidWhenInvalidRateIsOne() {
		List<PushResult> results = sender(0, 0.0, 1.0, 0).send(tokens(1_000), MESSAGE);

		assertThat(results).extracting(PushResult::status).containsOnly(PushStatus.INVALID_TOKEN);
	}

	@Test
	@DisplayName("failure-rate가 1이면 전부 FAILED다")
	void allFailedWhenFailureRateIsOne() {
		List<PushResult> results = sender(0, 1.0, 0.0, 0).send(tokens(1_000), MESSAGE);

		assertThat(results).extracting(PushResult::status).containsOnly(PushStatus.FAILED);
	}

	@Test
	@DisplayName("토큰 10,000개면 결과 비율이 설정 비율의 ±2%p 안이고, 카운터가 상태별 개수와 같다")
	void ratiosAreApproximatelyConfigured() {
		int size = 10_000;
		List<PushResult> results = sender(0, 0.2, 0.1, 0).send(seedTokens(size), MESSAGE);

		long invalid = results.stream().filter(r -> r.status() == PushStatus.INVALID_TOKEN).count();
		long failed = results.stream().filter(r -> r.status() == PushStatus.FAILED).count();
		long success = results.stream().filter(r -> r.status() == PushStatus.SUCCESS).count();

		assertThat((double)invalid / size).isCloseTo(0.1, within(0.02));
		assertThat((double)failed / size).isCloseTo(0.2, within(0.02));
		assertThat((double)success / size).isCloseTo(0.7, within(0.02));

		assertThat(count(PushStatus.INVALID_TOKEN)).isEqualTo(invalid);
		assertThat(count(PushStatus.FAILED)).isEqualTo(failed);
		assertThat(count(PushStatus.SUCCESS)).isEqualTo(success);
	}

	@Test
	@DisplayName("지연 중 인터럽트되면 전부 FAILED를 돌려주고 인터럽트 플래그를 복원한다")
	void returnsAllFailedAndRestoresFlagWhenInterrupted() {
		LoadTestPushSender sender = sender(60_000, 0.0, 0.0, 0);
		List<String> tokens = tokens(3);

		// 플래그가 켜진 상태면 Thread.sleep이 바로 InterruptedException을 던진다
		Thread.currentThread().interrupt();
		List<PushResult> results = sender.send(tokens, MESSAGE);

		assertThat(Thread.interrupted()).isTrue();
		assertThat(results).hasSize(3)
				.extracting(PushResult::status).containsOnly(PushStatus.FAILED);
		assertThat(results).extracting(PushResult::errorCode)
				.containsOnly(LoadTestPushSender.INTERRUPTED_ERROR_CODE);
		assertThat(count(PushStatus.FAILED)).isEqualTo(3);
		assertThat(count(PushStatus.SUCCESS)).isZero();
	}

	@Test
	@DisplayName("delay-ms만큼 send 호출당 한 번 쉰다")
	void sleepsOncePerCall() {
		LoadTestPushSender sender = sender(50, 0.0, 0.0, 0);

		long start = System.nanoTime();
		sender.send(tokens(100), MESSAGE);
		long elapsedMs = (System.nanoTime() - start) / 1_000_000;

		// 토큰마다 쉬면 5초 이상 걸린다
		assertThat(elapsedMs).isGreaterThanOrEqualTo(50).isLessThan(5_000);
	}

	private LoadTestPushSender sender(long delayMs, double failureRate, double invalidTokenRate, long seed) {
		LoadTestProperties properties = new LoadTestProperties(
				new LoadTestProperties.Push(delayMs, failureRate, invalidTokenRate, seed), null);
		return new LoadTestPushSender(properties, meterRegistry);
	}

	private double count(PushStatus status) {
		return meterRegistry.get(LoadTestPushSender.RESULTS_METRIC)
				.tag(LoadTestPushSender.STATUS_TAG, status.name())
				.counter()
				.count();
	}

	private static List<String> tokens(int size) {
		return IntStream.range(0, size).mapToObj(i -> "token-" + i).toList();
	}

	/** 시드 SQL(40_bulk_notification.sql)과 같은 모양의 토큰. 번호만 다른 토큰에서도 비율이 맞는지 본다. */
	private static List<String> seedTokens(int size) {
		return IntStream.range(0, size).mapToObj(i -> "loadtest-" + (i / 2 + 1) + "-" + (i % 2 + 1)).toList();
	}
}
