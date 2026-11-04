package kusitms.spin.tikitak.global.config;

import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageOptimizationConfigValidatorTest extends UnitTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(ValidatorTestConfig.class)
			.withPropertyValues(
					"r2.access-key-id=test-access-key",
					"r2.secret-access-key=test-secret-key",
					"r2.bucket-name=test-bucket",
					"r2.endpoint=https://example.com"
			);

	@Test
	void validateThrowsWhenPublicBaseUrlMissingAndOptimizationEnabled() {
		ImageOptimizationConfigValidator validator = validator(true, "", "preset");

		assertThatThrownBy(validator::validate)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("r2.public-base-url");
	}

	@Test
	void validateThrowsWhenPresetQueryParamMissingAndOptimizationEnabled() {
		ImageOptimizationConfigValidator validator = validator(true, "https://media.tikitak.space", "");

		assertThatThrownBy(validator::validate)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("image.optimization.preset-query-param");
	}

	@Test
	@DisplayName("빈으로 등록되면 컨텍스트 기동 시점에 검증이 실행되어 기동이 실패한다")
	void contextFailsToStartWhenPublicBaseUrlMissingAndOptimizationEnabled() {
		contextRunner
				.withPropertyValues("image.optimization.enabled=true")
				.run(context -> assertThat(context)
						.hasFailed()
						.getFailure()
						.rootCause()
						.isInstanceOf(IllegalStateException.class)
						.hasMessageContaining("r2.public-base-url"));
	}

	@Test
	@DisplayName("최적화가 꺼져 있으면 public-base-url이 없어도 기동한다")
	void contextStartsWhenOptimizationDisabled() {
		contextRunner
				.withPropertyValues("image.optimization.enabled=false")
				.run(context -> assertThat(context)
						.hasNotFailed()
						.hasSingleBean(ImageOptimizationConfigValidator.class));
	}

	private ImageOptimizationConfigValidator validator(boolean enabled, String publicBaseUrl, String presetQueryParam) {
		R2Properties r2Properties = new R2Properties();
		r2Properties.setPublicBaseUrl(publicBaseUrl);
		ImageOptimizationProperties properties = new ImageOptimizationProperties();
		properties.setEnabled(enabled);
		properties.setPresetQueryParam(presetQueryParam);
		return new ImageOptimizationConfigValidator(r2Properties, properties);
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties({R2Properties.class, ImageOptimizationProperties.class})
	@Import(ImageOptimizationConfigValidator.class)
	static class ValidatorTestConfig {
	}
}
