package kusitms.spin.tikitak.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
		AuthProperties.class,
		R2Properties.class,
		KakaoMapProperties.class,
		ImageOptimizationProperties.class,
		FirebaseProperties.class,
		OAuthStateProperties.class,
		LoginCodeProperties.class
})
public class PropertiesConfig {
}
