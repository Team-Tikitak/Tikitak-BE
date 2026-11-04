package kusitms.spin.tikitak.global.config;

import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LoginCodePropertiesTest extends UnitTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(TestConfig.class);

	@Test
	@DisplayName("값이 없으면 기존 @Value 기본값과 같은 프리픽스와 TTL을 쓴다")
	void usesDefaultsWhenValuesAreMissing() {
		LoginCodeProperties properties = new LoginCodeProperties(null, null);

		assertThat(properties.redisKeyPrefix()).isEqualTo("tikitak:local:auth:login-code:");
		assertThat(properties.ttl()).isEqualTo(Duration.parse("PT5M"));
	}

	@Test
	@DisplayName("프리픽스가 공백이면 기본 프리픽스를 쓴다")
	void usesDefaultPrefixWhenBlank() {
		LoginCodeProperties properties = new LoginCodeProperties("  ", Duration.ofMinutes(1));

		assertThat(properties.redisKeyPrefix()).isEqualTo("tikitak:local:auth:login-code:");
	}

	@Test
	@DisplayName("값이 주어지면 그 값을 쓴다")
	void usesGivenValues() {
		LoginCodeProperties properties = new LoginCodeProperties("tikitak:prod:custom:", Duration.ofSeconds(30));

		assertThat(properties.redisKeyPrefix()).isEqualTo("tikitak:prod:custom:");
		assertThat(properties.ttl()).isEqualTo(Duration.ofSeconds(30));
	}

	@Test
	@DisplayName("설정 키가 없으면 바인딩된 빈이 기본 프리픽스와 TTL을 갖는다")
	void bindsDefaultsWhenKeysAreAbsent() {
		contextRunner.run(context -> {
			assertThat(context).hasNotFailed();
			LoginCodeProperties properties = context.getBean(LoginCodeProperties.class);

			assertThat(properties.redisKeyPrefix()).isEqualTo("tikitak:local:auth:login-code:");
			assertThat(properties.ttl()).isEqualTo(Duration.parse("PT5M"));
		});
	}

	@Test
	@DisplayName("설정 키가 있으면 바인딩된 빈이 그 값을 갖는다")
	void bindsGivenValuesWhenKeysArePresent() {
		contextRunner
				.withPropertyValues(
						"auth.login-code.redis-key-prefix=tikitak:test:custom:",
						"auth.login-code.ttl=PT10S"
				)
				.run(context -> {
					assertThat(context).hasNotFailed();
					LoginCodeProperties properties = context.getBean(LoginCodeProperties.class);

					assertThat(properties.redisKeyPrefix()).isEqualTo("tikitak:test:custom:");
					assertThat(properties.ttl()).isEqualTo(Duration.ofSeconds(10));
				});
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(LoginCodeProperties.class)
	static class TestConfig {
	}
}
