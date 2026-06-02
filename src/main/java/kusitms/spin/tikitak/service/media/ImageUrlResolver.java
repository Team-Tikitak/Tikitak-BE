package kusitms.spin.tikitak.service.media;

import kusitms.spin.tikitak.global.config.ImageOptimizationProperties;
import kusitms.spin.tikitak.global.config.R2Properties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ImageUrlResolver {

	private final R2Properties r2Properties;
	private final ImageOptimizationProperties properties;

	@PostConstruct
	void validateConfiguration() {
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

	public String resolve(String originalUrl, ImagePreset preset) {
		if (originalUrl == null || originalUrl.isBlank()) {
			return originalUrl;
		}
		if (preset == null || preset == ImagePreset.ORIGINAL || preset.queryValue() == null) {
			return originalUrl;
		}
		if (!properties.isEnabled()) {
			return originalUrl;
		}
		String publicBaseUrl = normalizeBaseUrl(r2Properties.getPublicBaseUrl());
		if (!originalUrl.startsWith(publicBaseUrl + "/")) {
			return originalUrl;
		}
		String paramName = properties.getPresetQueryParam();
		if (hasPresetParam(originalUrl, paramName)) {
			return originalUrl;
		}
		String delimiter = originalUrl.contains("?") ? "&" : "?";
		return originalUrl + delimiter + paramName + "=" + preset.queryValue();
	}

	private boolean hasPresetParam(String url, String paramName) {
		return url.contains("?" + paramName + "=") || url.contains("&" + paramName + "=");
	}

	private String normalizeBaseUrl(String baseUrl) {
		if (isBlank(baseUrl)) {
			return null;
		}
		return baseUrl.replaceAll("/+$", "");
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
