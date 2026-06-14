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
import java.util.List;

@Configuration
@RequiredArgsConstructor
@ConditionalOnExpression("!'${firebase.credentials-base64:}'.isEmpty()")
public class FirebaseConfig {

	private final FirebaseProperties firebaseProperties;

	@Bean
	public FirebaseApp firebaseApp() throws IOException {
		byte[] decoded = Base64.getDecoder().decode(firebaseProperties.getCredentialsBase64());
		FirebaseOptions options = FirebaseOptions.builder()
				.setCredentials(GoogleCredentials.fromStream(new ByteArrayInputStream(decoded)))
				.build();

		List<FirebaseApp> apps = FirebaseApp.getApps();
		if (!apps.isEmpty()) {
			return apps.get(0);
		}
		return FirebaseApp.initializeApp(options);
	}

	@Bean
	public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
		return FirebaseMessaging.getInstance(firebaseApp);
	}
}
