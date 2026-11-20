package kusitms.spin.tikitak.global.config;

import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class JwtSecretValidatorTest extends UnitTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(ValidatorTestConfig.class);

	@Test
	@DisplayName("secret이 31바이트면 기동에 실패한다")
	void contextFailsWhenSecretIs31Bytes() {
		String secret = "a".repeat(31);
		assertThat(secret.getBytes(StandardCharsets.UTF_8)).hasSize(31);

		contextRunner
				.withPropertyValues("auth.jwt.secret=" + secret)
				.run(context -> assertThat(context)
						.hasFailed()
						.getFailure()
						.rootCause()
						.isInstanceOf(IllegalStateException.class)
						.hasMessageContaining("auth.jwt.secret")
						.hasMessageNotContaining(secret));
	}

	@Test
	@DisplayName("secret이 32바이트면 기동에 성공한다")
	void contextStartsWhenSecretIs32Bytes() {
		String secret = "a".repeat(32);
		assertThat(secret.getBytes(StandardCharsets.UTF_8)).hasSize(32);

		contextRunner
				.withPropertyValues("auth.jwt.secret=" + secret)
				.run(context -> assertThat(context)
						.hasNotFailed()
						.hasSingleBean(JwtSecretValidator.class));
	}

	@Test
	@DisplayName("auth.jwt가 설정되지 않으면 기동에 실패한다")
	void contextFailsWhenJwtNotConfigured() {
		contextRunner
				.run(context -> assertThat(context)
						.hasFailed()
						.getFailure()
						.rootCause()
						.isInstanceOf(IllegalStateException.class)
						.hasMessageContaining("auth.jwt.secret"));
	}

	@Test
	@DisplayName("한글 11자(33바이트) secret은 문자 수가 아니라 바이트 수로 판정해 기동에 성공한다")
	void contextStartsWhenSecretIs11KoreanCharacters() {
		String secret = "가".repeat(11);
		assertThat(secret).hasSize(11);
		assertThat(secret.getBytes(StandardCharsets.UTF_8)).hasSize(33);

		contextRunner
				.withPropertyValues("auth.jwt.secret=" + secret)
				.run(context -> assertThat(context)
						.hasNotFailed()
						.hasSingleBean(JwtSecretValidator.class));
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(AuthProperties.class)
	@Import(JwtSecretValidator.class)
	static class ValidatorTestConfig {
	}
}
