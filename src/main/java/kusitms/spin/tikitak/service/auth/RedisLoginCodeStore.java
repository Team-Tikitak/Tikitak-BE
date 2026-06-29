package kusitms.spin.tikitak.service.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kusitms.spin.tikitak.service.auth.dto.LoginCodePayload;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RedisLoginCodeStore implements LoginCodeStore {

	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;

	@Value("${auth.login-code.redis-key-prefix:tikitak:local:auth:login-code:}")
	private String keyPrefix;

	@Value("${auth.login-code.ttl:PT5M}")
	private Duration ttl;

	@Override
	public void save(String code, LoginCodePayload payload) {
		redisTemplate.opsForValue().set(key(code), serialize(payload), ttl);
	}

	@Override
	public Optional<LoginCodePayload> consume(String code) {
		String raw = redisTemplate.opsForValue().getAndDelete(key(code));
		return Optional.ofNullable(raw).map(this::deserialize);
	}

	private String key(String code) {
		return keyPrefix + code;
	}

	private String serialize(LoginCodePayload payload) {
		try {
			return objectMapper.writeValueAsString(payload);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to serialize login code payload.", e);
		}
	}

	private LoginCodePayload deserialize(String raw) {
		try {
			return objectMapper.readValue(raw, LoginCodePayload.class);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to deserialize login code payload.", e);
		}
	}
}
