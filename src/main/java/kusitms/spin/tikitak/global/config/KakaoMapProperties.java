package kusitms.spin.tikitak.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kakao.map")
public record KakaoMapProperties(
        String restApiKey
) {
}
