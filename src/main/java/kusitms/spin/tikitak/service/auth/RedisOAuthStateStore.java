package kusitms.spin.tikitak.service.auth;

import kusitms.spin.tikitak.global.config.OAuthStateProperties;
import kusitms.spin.tikitak.service.auth.exception.OAuthAuthenticationException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisOAuthStateStore implements OAuthStateStore {

	private final StringRedisTemplate redisTemplate;
	private final OAuthStateProperties properties;

	@Override
	public void save(String state, String provider) {
		redisTemplate.opsForValue().set(key(state), normalize(provider), properties.ttl());
	}

	@Override
	public boolean consume(String state, String provider) {
		String storedProvider;
		try {
			storedProvider = redisTemplate.opsForValue().getAndDelete(key(state));
		} catch (Exception e) {
			throw new OAuthAuthenticationException("Failed to consume OAuth state.", e);
		}
		return storedProvider != null && storedProvider.equals(normalize(provider));
	}

	private String key(String state) {
		return properties.redisKeyPrefix() + state;
	}

	private String normalize(String provider) {
		return provider == null ? "" : provider.toLowerCase();
	}
}
