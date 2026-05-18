package kusitms.spin.tikitak.global.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "kakao.map")
public record KakaoMapProperties(
        @NotBlank
        String restApiKey
) {
}
