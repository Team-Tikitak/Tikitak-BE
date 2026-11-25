package kusitms.spin.tikitak.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 로컬 부하 측정 전용 설정을 등록한다(#215). {@link PropertiesConfig}에 넣지 않아서 {@code loadtest} 프로필이 없거나
 * {@code prod}가 함께 켜지면 {@link LoadTestProperties} 빈도 없다.
 * docs/decisions/2026-11-24-push-sender-loadtest-selection.md
 */
@Configuration
@Profile(LoadTestConfig.PROFILE)
@EnableConfigurationProperties(LoadTestProperties.class)
public class LoadTestConfig {

	/** 측정 전용 빈의 등록 조건. 운영(prod)에서는 loadtest를 함께 켜도 등록되지 않는다. */
	public static final String PROFILE = "loadtest & !prod";
}
