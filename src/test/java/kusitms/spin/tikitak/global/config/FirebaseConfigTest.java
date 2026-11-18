package kusitms.spin.tikitak.global.config;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseOptions;
import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FirebaseConfigTest extends UnitTest {

	@Test
	@DisplayName("FCM 옵션에 연결 3초, 읽기 10초, 쓰기 10초 타임아웃을 넣는다")
	void buildOptionsSetsTimeouts() {
		GoogleCredentials credentials = GoogleCredentials.create(new AccessToken("token", null));

		FirebaseOptions options = FirebaseConfig.buildOptions(credentials);

		assertThat(options.getConnectTimeout()).isEqualTo(3_000);
		assertThat(options.getReadTimeout()).isEqualTo(10_000);
		assertThat(options.getWriteTimeout()).isEqualTo(10_000);
	}
}
