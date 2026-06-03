package kusitms.spin.tikitak.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "image.optimization")
public class ImageOptimizationProperties {
	private boolean enabled = true;
	private String presetQueryParam = "preset";
}
