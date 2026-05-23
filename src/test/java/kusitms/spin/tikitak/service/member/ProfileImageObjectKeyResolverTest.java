package kusitms.spin.tikitak.service.member;

import kusitms.spin.tikitak.global.config.R2Properties;
import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileImageObjectKeyResolverTest extends UnitTest {

	private ProfileImageObjectKeyResolver resolver;

	@BeforeEach
	void setUp() {
		R2Properties r2Properties = new R2Properties();
		r2Properties.setBucketName("tikitak-dev-media");
		r2Properties.setEndpoint("https://example.r2.cloudflarestorage.com");
		r2Properties.setPublicBaseUrl("https://cdn.tikitak.com");
		resolver = new ProfileImageObjectKeyResolver(r2Properties);
	}

	@Test
	@DisplayName("public base URL로 시작하는 프로필 이미지 URL에서 object key를 추출한다")
	void resolveObjectKeyFromPublicBaseUrl() {
		assertThat(resolver.resolveObjectKey("https://cdn.tikitak.com/media/profile/abc.png?version=1"))
				.contains("media/profile/abc.png");
	}

	@Test
	@DisplayName("R2 endpoint bucket URL에서 object key를 추출한다")
	void resolveObjectKeyFromEndpointBucketUrl() {
		assertThat(resolver.resolveObjectKey("https://example.r2.cloudflarestorage.com/tikitak-dev-media/media/profile/abc.png"))
				.contains("media/profile/abc.png");
	}

	@Test
	@DisplayName("외부 OAuth 프로필 이미지 URL은 삭제 대상 object key로 보지 않는다")
	void ignoresExternalProfileImageUrl() {
		assertThat(resolver.resolveObjectKey("https://k.kakaocdn.net/profile/abc.png"))
				.isEmpty();
	}

	@Test
	@DisplayName("host가 없는 상대 경로는 삭제 대상 object key로 보지 않는다")
	void ignoresRelativePath() {
		assertThat(resolver.resolveObjectKey("media/profile/abc.png"))
				.isEmpty();
	}
}
