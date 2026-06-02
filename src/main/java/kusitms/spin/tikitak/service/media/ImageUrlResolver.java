package kusitms.spin.tikitak.service.media;

import kusitms.spin.tikitak.global.config.ImageOptimizationProperties;
import kusitms.spin.tikitak.global.config.R2Properties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ImageUrlResolver {

	private final R2Properties r2Properties;
	private final ImageOptimizationProperties properties;

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
		if (publicBaseUrl == null || !originalUrl.startsWith(publicBaseUrl + "/")) {
			return originalUrl;
		}
		String paramName = properties.getPresetQueryParam();
		if (paramName == null || paramName.isBlank() || hasPresetParam(originalUrl, paramName)) {
			return originalUrl;
		}
		String delimiter = originalUrl.contains("?") ? "&" : "?";
		return originalUrl + delimiter + paramName + "=" + preset.queryValue();
	}

	private boolean hasPresetParam(String url, String paramName) {
		return url.contains("?" + paramName + "=") || url.contains("&" + paramName + "=");
	}

	private String normalizeBaseUrl(String baseUrl) {
		if (baseUrl == null || baseUrl.isBlank()) {
			return null;
		}
		return baseUrl.replaceAll("/+$", "");
	}
}
