package kusitms.spin.tikitak.service.member;

import kusitms.spin.tikitak.global.config.R2Properties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ProfileImageObjectKeyResolver {

	private final R2Properties r2Properties;

	public Optional<String> resolveObjectKey(String profileImgUrl) {
		if (profileImgUrl == null || profileImgUrl.isBlank()) {
			return Optional.empty();
		}

		return fromBaseUrl(profileImgUrl, r2Properties.getPublicBaseUrl())
				.or(() -> fromEndpointBucketUrl(profileImgUrl));
	}

	private Optional<String> fromBaseUrl(String url, String baseUrl) {
		if (baseUrl == null || baseUrl.isBlank()) {
			return Optional.empty();
		}

		String normalizedBaseUrl = trimTrailingSlash(baseUrl);
		if (!url.startsWith(normalizedBaseUrl + "/")) {
			return Optional.empty();
		}

		String objectKey = url.substring(normalizedBaseUrl.length() + 1);
		int queryIndex = objectKey.indexOf('?');
		if (queryIndex >= 0) {
			objectKey = objectKey.substring(0, queryIndex);
		}
		return nonBlank(objectKey);
	}

	private Optional<String> fromEndpointBucketUrl(String url) {
		if (r2Properties.getEndpoint() == null || r2Properties.getEndpoint().isBlank()
				|| r2Properties.getBucketName() == null || r2Properties.getBucketName().isBlank()) {
			return Optional.empty();
		}

		try {
			URI uri = URI.create(url);
			URI endpointUri = URI.create(r2Properties.getEndpoint());
			String host = uri.getHost();
			String endpointHost = endpointUri.getHost();
			if (host == null || endpointHost == null || !host.equalsIgnoreCase(endpointHost)) {
				return Optional.empty();
			}

			String bucketPrefix = "/" + r2Properties.getBucketName() + "/";
			String path = uri.getPath();
			if (path == null || !path.startsWith(bucketPrefix)) {
				return Optional.empty();
			}
			return nonBlank(path.substring(bucketPrefix.length()));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	private Optional<String> nonBlank(String value) {
		return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
	}

	private String trimTrailingSlash(String value) {
		String result = value;
		while (result.endsWith("/")) {
			result = result.substring(0, result.length() - 1);
		}
		return result;
	}
}
