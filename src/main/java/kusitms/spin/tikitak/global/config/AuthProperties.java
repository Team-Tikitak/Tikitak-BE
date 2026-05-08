package kusitms.spin.tikitak.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth")
public record AuthProperties(
		OAuth oauth,
		Jwt jwt
) {

	public record OAuth(
			String frontendRedirectUri,
			Google google
	) {
	}

	public record Google(
			String clientId,
			String clientSecret,
			String redirectUri
	) {
	}

	public record Jwt(
			String secret,
			long accessTokenExpiresIn,
			long refreshTokenExpiresIn,
			boolean cookieSecure
	) {
	}
}
