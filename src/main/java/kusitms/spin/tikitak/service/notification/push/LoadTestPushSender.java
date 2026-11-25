package kusitms.spin.tikitak.service.notification.push;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import kusitms.spin.tikitak.global.config.LoadTestConfig;
import kusitms.spin.tikitak.global.config.LoadTestProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 로컬 부하 측정(#215) 전용 가짜 {@link PushSender}. 실제 FCM을 부르지 않고, 설정한 지연과 비율로 결과를 만든다.
 * {@code loadtest & !prod} 프로필에서만 등록되고 {@link Primary}라서, 그때만 {@link FcmPushSender} 대신 주입된다.
 * <ul>
 *   <li>{@link #send} 호출(회원 한 명)마다 {@code delay-ms}만큼 한 번 쉰다. FCM {@code sendEach} 한 번의 왕복을 흉내 낸다.</li>
 *   <li>토큰마다 (토큰 문자열 + seed) 해시로 [0, 1) 값을 만들어 {@code invalid-token-rate} 미만이면
 *   {@code INVALID_TOKEN}, {@code invalid-token-rate + failure-rate} 미만이면 {@code FAILED}, 나머지는 {@code SUCCESS}.
 *   같은 입력이면 항상 같은 결과다.</li>
 *   <li>결과는 입력과 크기·순서가 같다({@link PushSender#send} 규약).</li>
 * </ul>
 * docs/decisions/2026-11-24-push-sender-loadtest-selection.md
 */
@Slf4j
@Primary
@Component
@Profile(LoadTestConfig.PROFILE)
public class LoadTestPushSender implements PushSender {

	public static final String RESULTS_METRIC = "loadtest.push.results";
	public static final String STATUS_TAG = "status";

	static final String INVALID_TOKEN_ERROR_CODE = "LOADTEST_INVALID_TOKEN";
	static final String FAILED_ERROR_CODE = "LOADTEST_FAILED";
	static final String INTERRUPTED_ERROR_CODE = "LOADTEST_INTERRUPTED";

	private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
	private static final long FNV_PRIME = 0x100000001b3L;
	private static final long GOLDEN_GAMMA = 0x9e3779b97f4a7c15L;
	private static final double UNIT_53 = 0x1.0p-53;

	private final LoadTestProperties.Push properties;
	private final Map<PushStatus, Counter> counters;

	public LoadTestPushSender(LoadTestProperties properties, MeterRegistry meterRegistry) {
		this.properties = properties.push();
		this.counters = new EnumMap<>(PushStatus.class);
		for (PushStatus status : PushStatus.values()) {
			counters.put(status, Counter.builder(RESULTS_METRIC)
					.tag(STATUS_TAG, status.name())
					.register(meterRegistry));
		}
		log.info("Load test push sender enabled. delayMs={}, failureRate={}, invalidTokenRate={}, seed={}",
				this.properties.delayMs(), this.properties.failureRate(), this.properties.invalidTokenRate(),
				this.properties.seed());
	}

	@Override
	public boolean isAvailable() {
		return true;
	}

	@Override
	public List<PushResult> send(List<String> tokens, PushMessage message) {
		if (tokens.isEmpty()) {
			return List.of();
		}
		if (!sleep()) {
			counters.get(PushStatus.FAILED).increment(tokens.size());
			return Collections.nCopies(tokens.size(),
					PushResult.failed(INTERRUPTED_ERROR_CODE, "Load test push delay was interrupted."));
		}

		List<PushResult> results = new ArrayList<>(tokens.size());
		for (String token : tokens) {
			PushResult result = resultOf(token);
			counters.get(result.status()).increment();
			results.add(result);
		}
		return results;
	}

	/**
	 * @return 쉬는 동안 인터럽트되지 않았으면 true. 인터럽트되면 플래그를 복원하고 false
	 */
	private boolean sleep() {
		long delayMs = properties.delayMs();
		if (delayMs <= 0) {
			return true;
		}
		try {
			Thread.sleep(delayMs);
			return true;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log.warn("Load test push delay was interrupted.");
			return false;
		}
	}

	private PushResult resultOf(String token) {
		double value = unitValue(token, properties.seed());
		double invalidTokenRate = properties.invalidTokenRate();
		if (value < invalidTokenRate) {
			return PushResult.invalidToken(INVALID_TOKEN_ERROR_CODE, "Simulated invalid token.");
		}
		if (value < invalidTokenRate + properties.failureRate()) {
			return PushResult.failed(FAILED_ERROR_CODE, "Simulated send failure.");
		}
		return PushResult.success();
	}

	/**
	 * 토큰 문자열(UTF-8)의 FNV-1a 64비트 해시에 seed를 섞고 SplitMix64 마무리 함수로 퍼뜨려 [0, 1) 값으로 바꾼다.
	 * 번호만 다른 토큰(loadtest-1-1, loadtest-2-1, ...)도 고르게 퍼지게 하려고 마무리 함수를 쓴다.
	 */
	static double unitValue(String token, long seed) {
		long hash = FNV_OFFSET_BASIS;
		for (byte b : token.getBytes(StandardCharsets.UTF_8)) {
			hash ^= (b & 0xff);
			hash *= FNV_PRIME;
		}
		long mixed = mix64(hash + seed * GOLDEN_GAMMA);
		return (mixed >>> 11) * UNIT_53;
	}

	private static long mix64(long value) {
		long z = value;
		z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
		z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
		return z ^ (z >>> 31);
	}
}
