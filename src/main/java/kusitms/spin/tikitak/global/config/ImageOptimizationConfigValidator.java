package kusitms.spin.tikitak.global.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 이미지 최적화 설정을 기동 시점에 검증한다.
 * ImageOptimizationProperties와 R2Properties를 함께 봐야 해서 단일 Properties의 @Validated로는 표현할 수 없다.
 * 설정 오류는 HTTP 응답이 아니라 기동 실패로 드러나야 하므로 IllegalStateException을 던진다.
 */
@Component
@RequiredArgsConstructor
public class ImageOptimizationConfigValidator {

	private final R2Properties r2Properties;
	private final ImageOptimizationProperties properties;

	@PostConstruct
	void validate() {
		if (!properties.isEnabled()) {
			return;
		}
		if (isBlank(r2Properties.getPublicBaseUrl())) {
			throw new IllegalStateException("r2.public-base-url is required when image optimization is enabled.");
		}
		if (isBlank(properties.getPresetQueryParam())) {
			throw new IllegalStateException("image.optimization.preset-query-param is required when image optimization is enabled.");
		}
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
