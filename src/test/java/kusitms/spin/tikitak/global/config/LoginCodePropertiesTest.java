package kusitms.spin.tikitak.global.config;

import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LoginCodePropertiesTest extends UnitTest {

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
}
