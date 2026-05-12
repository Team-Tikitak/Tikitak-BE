package kusitms.spin.tikitak.global.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "r2")
@Getter
@Setter
public class R2Properties {
    private String accountId;
    private String region = "auto";
    @NotBlank
    private String accessKeyId;
    @NotBlank
    private String secretAccessKey;
    @NotBlank
    private String bucketName;
    @NotBlank
    private String endpoint;
    private String publicBaseUrl;
}
