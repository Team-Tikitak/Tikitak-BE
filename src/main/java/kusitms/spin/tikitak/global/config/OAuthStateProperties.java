package kusitms.spin.tikitak.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "auth.oauth.state")
public record OAuthStateProperties(
		String redisKeyPrefix,
		Duration ttl
) {

	public static final String DEFAULT_REDIS_KEY_PREFIX = "tikitak:local:oauth:state:";
	public static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

	public OAuthStateProperties {
		if (redisKeyPrefix == null || redisKeyPrefix.isBlank()) {
			redisKeyPrefix = DEFAULT_REDIS_KEY_PREFIX;
		}
		if (ttl == null) {
			ttl = DEFAULT_TTL;
		}
	}
}
