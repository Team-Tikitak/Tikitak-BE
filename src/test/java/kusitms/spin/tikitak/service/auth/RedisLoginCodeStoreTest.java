package kusitms.spin.tikitak.service.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kusitms.spin.tikitak.global.config.LoginCodeProperties;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.service.auth.dto.LoginCodePayload;
import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

class RedisLoginCodeStoreTest extends UnitTest {

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private ValueOperations<String, String> valueOperations;

	@Mock
	private ObjectMapper failingObjectMapper;

	private final LoginCodeProperties properties = new LoginCodeProperties(null, null);

	@Test
	@DisplayName("payload 직렬화에 실패하면 COMMON004 BusinessException이 발생한다")
	void saveThrowsCommon004WhenSerializationFails() throws JsonProcessingException {
		given(redisTemplate.opsForValue()).willReturn(valueOperations);
		given(failingObjectMapper.writeValueAsString(any())).willThrow(JsonProcessingException.class);
		RedisLoginCodeStore store = new RedisLoginCodeStore(redisTemplate, failingObjectMapper, properties);

		assertThatThrownBy(() -> store.save("code", LoginCodePayload.builder().memberId(1L).build()))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.COMMON004);
		verifyNoInteractions(valueOperations);
	}

	@Test
	@DisplayName("저장된 값을 역직렬화하지 못하면 COMMON004 BusinessException이 발생한다")
	void consumeThrowsCommon004WhenDeserializationFails() {
		given(redisTemplate.opsForValue()).willReturn(valueOperations);
		given(valueOperations.getAndDelete(any())).willReturn("not-json");
		RedisLoginCodeStore store = new RedisLoginCodeStore(redisTemplate, new ObjectMapper(), properties);

		assertThatThrownBy(() -> store.consume("code"))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.COMMON004);
	}
}
