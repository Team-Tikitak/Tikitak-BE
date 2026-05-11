package kusitms.spin.tikitak.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "r2")
@Getter
@Setter
public class R2Properties {
    private String accountId;
    private String accessKeyId;
    private String secretAccessKey;
    private String bucketName;
    private String endpoint;
    private String publicBaseUrl;
}