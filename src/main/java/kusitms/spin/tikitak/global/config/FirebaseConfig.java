package kusitms.spin.tikitak.global.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;

@Configuration
@RequiredArgsConstructor
@ConditionalOnExpression("!'${firebase.credentials-base64:}'.isEmpty()")
public class FirebaseConfig {

	private static final int CONNECT_TIMEOUT_MILLIS = 3_000;
	private static final int READ_TIMEOUT_MILLIS = 10_000;
	private static final int WRITE_TIMEOUT_MILLIS = 10_000;

	private final FirebaseProperties firebaseProperties;

	@Bean
	public FirebaseApp firebaseApp() throws IOException {
		byte[] decoded = Base64.getDecoder().decode(firebaseProperties.getCredentialsBase64());
		FirebaseOptions options = buildOptions(GoogleCredentials.fromStream(new ByteArrayInputStream(decoded)));

		return FirebaseApp.getApps().stream()
				.filter(app -> FirebaseApp.DEFAULT_APP_NAME.equals(app.getName()))
				.findFirst()
				.orElseGet(() -> FirebaseApp.initializeApp(options));
	}

	static FirebaseOptions buildOptions(GoogleCredentials credentials) {
		return FirebaseOptions.builder()
				.setCredentials(credentials)
				.setConnectTimeout(CONNECT_TIMEOUT_MILLIS)
				.setReadTimeout(READ_TIMEOUT_MILLIS)
				.setWriteTimeout(WRITE_TIMEOUT_MILLIS)
				.build();
	}

	@Bean
	public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
		return FirebaseMessaging.getInstance(firebaseApp);
	}
}
