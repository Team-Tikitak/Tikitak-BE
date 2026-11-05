package kusitms.spin.tikitak.service.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import kusitms.spin.tikitak.global.config.LoginCodeProperties;
import kusitms.spin.tikitak.service.auth.dto.LoginCodePayload;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class RedisLoginCodeStoreIntegrationTest {

	private static final GenericContainer<?> REDIS =
			new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

	private static LettuceConnectionFactory connectionFactory;

	private RedisLoginCodeStore store;

	@BeforeAll
	static void startRedis() {
		REDIS.start();
		connectionFactory = new LettuceConnectionFactory(
				new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
		connectionFactory.afterPropertiesSet();
	}

	@AfterAll
	static void stopRedis() {
		connectionFactory.destroy();
		REDIS.stop();
	}

	@BeforeEach
	void setUp() {
		StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
		LoginCodeProperties properties = new LoginCodeProperties("test:auth:login-code:", Duration.ofSeconds(2));
		store = new RedisLoginCodeStore(redisTemplate, new ObjectMapper(), properties);
	}

	@Test
	@DisplayName("저장한 loginCode는 한 번만 consume할 수 있다")
	void savedLoginCodeCanBeConsumedOnlyOnce() {
		LoginCodePayload payload = LoginCodePayload.builder()
				.memberId(1L)
				.newMember(true)
				.agreedRequiredTerms(false)
				.activeTeamId(10L)
				.build();
		store.save("code-1", payload);

		Optional<LoginCodePayload> first = store.consume("code-1");
		Optional<LoginCodePayload> second = store.consume("code-1");

		assertThat(first).isPresent();
		assertThat(first.get().getMemberId()).isEqualTo(1L);
		assertThat(first.get().isNewMember()).isTrue();
		assertThat(first.get().isAgreedRequiredTerms()).isFalse();
		assertThat(first.get().getActiveTeamId()).isEqualTo(10L);
		assertThat(second).isEmpty();
	}

	@Test
	@DisplayName("TTL이 지나면 loginCode는 더 이상 조회되지 않는다")
	void loginCodeExpiresAfterTtl() throws InterruptedException {
		store.save("code-2", LoginCodePayload.builder().memberId(2L).build());

		Thread.sleep(2_500);

		assertThat(store.consume("code-2")).isEmpty();
	}

	@Test
	@DisplayName("존재하지 않는 loginCode를 consume하면 빈 값을 반환한다")
	void consumeReturnsEmptyForUnknownCode() {
		assertThat(store.consume("unknown")).isEmpty();
	}
}
