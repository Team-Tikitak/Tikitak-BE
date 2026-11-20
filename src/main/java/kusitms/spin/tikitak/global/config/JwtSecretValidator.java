package kusitms.spin.tikitak.global.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * JWT 서명 secret을 기동 시점에 검증한다.
 * HS256 키는 최소 32바이트(256비트)여야 하므로, secret이 없거나 UTF-8 기준 32바이트보다 짧으면 기동을 막는다.
 * 설정 오류는 HTTP 응답이 아니라 기동 실패로 드러나야 하므로 IllegalStateException을 던진다.
 * 메시지에는 secret 값을 넣지 않는다.
 */
@Component
@RequiredArgsConstructor
public class JwtSecretValidator {

	static final int MIN_SECRET_BYTES = 32;

	private final AuthProperties authProperties;

	@PostConstruct
	void validate() {
		AuthProperties.Jwt jwt = authProperties.jwt();
		if (jwt == null || jwt.secret() == null) {
			throw new IllegalStateException("auth.jwt.secret is required.");
		}
		if (jwt.secret().getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
			throw new IllegalStateException("auth.jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes (UTF-8).");
		}
	}
}
