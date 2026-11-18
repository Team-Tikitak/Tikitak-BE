package kusitms.spin.tikitak.global.config;

import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class R2ConfigTest extends UnitTest {

	@Test
	@DisplayName("S3Client에 시도당 5초, 호출 전체 15초 타임아웃을 넣는다")
	void s3ClientSetsApiCallTimeouts() {
		R2Properties properties = new R2Properties();
		properties.setRegion("auto");
		properties.setEndpoint("https://example.r2.cloudflarestorage.com");
		properties.setAccessKeyId("test-access-key");
		properties.setSecretAccessKey("test-secret-key");
		properties.setBucketName("test-bucket");

		try (S3Client s3Client = new R2Config(properties).s3Client()) {
			ClientOverrideConfiguration override = s3Client.serviceClientConfiguration().overrideConfiguration();

			assertThat(override.apiCallAttemptTimeout()).contains(Duration.ofSeconds(5));
			assertThat(override.apiCallTimeout()).contains(Duration.ofSeconds(15));
		}
	}
}
