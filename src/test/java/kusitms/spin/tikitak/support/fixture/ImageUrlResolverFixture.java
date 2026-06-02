package kusitms.spin.tikitak.support.fixture;

import kusitms.spin.tikitak.global.config.ImageOptimizationProperties;
import kusitms.spin.tikitak.global.config.R2Properties;
import kusitms.spin.tikitak.service.media.ImageUrlResolver;

public final class ImageUrlResolverFixture {

	private ImageUrlResolverFixture() {
	}

	public static ImageUrlResolver disabledImageUrlResolver() {
		R2Properties r2Properties = new R2Properties();
		r2Properties.setPublicBaseUrl("https://example.com");
		ImageOptimizationProperties properties = new ImageOptimizationProperties();
		properties.setEnabled(false);
		return new ImageUrlResolver(r2Properties, properties);
	}
}
