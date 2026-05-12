package kusitms.spin.tikitak.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({AuthProperties.class, R2Properties.class})
public class PropertiesConfig {
}
