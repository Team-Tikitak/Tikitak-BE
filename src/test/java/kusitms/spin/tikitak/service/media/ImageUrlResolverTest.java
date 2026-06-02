package kusitms.spin.tikitak.service.media;

import kusitms.spin.tikitak.global.config.ImageOptimizationProperties;
import kusitms.spin.tikitak.global.config.R2Properties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImageUrlResolverTest {

	@Test
	void appendsPresetQueryToR2PublicUrl() {
		ImageUrlResolver resolver = resolver(true, "https://media.tikitak.space/");

		String result = resolver.resolve(
				"https://media.tikitak.space/media/feed-image/test.jpg",
				ImagePreset.FEED_THUMB
		);

		assertThat(result).isEqualTo(
				"https://media.tikitak.space/media/feed-image/test.jpg?preset=feed_thumb");
	}

	@Test
	void appendsPresetWithAmpersandWhenUrlAlreadyHasQuery() {
		ImageUrlResolver resolver = resolver(true, "https://media.tikitak.space");

		String result = resolver.resolve(
				"https://media.tikitak.space/media/feed-image/test.jpg?v=1",
				ImagePreset.FEED_DETAIL
		);

		assertThat(result).isEqualTo(
				"https://media.tikitak.space/media/feed-image/test.jpg?v=1&preset=feed_detail");
	}

	@Test
	void returnsOriginalUrlWhenOptimizationDisabled() {
		ImageUrlResolver resolver = resolver(false, "https://media.tikitak.space");

		String result = resolver.resolve(
				"https://media.tikitak.space/media/feed-image/test.jpg",
				ImagePreset.FEED_THUMB
		);

		assertThat(result).isEqualTo("https://media.tikitak.space/media/feed-image/test.jpg");
	}

	@Test
	void returnsExternalUrlUnchanged() {
		ImageUrlResolver resolver = resolver(true, "https://media.tikitak.space");

		String result = resolver.resolve(
				"https://example.com/profile.jpg",
				ImagePreset.PROFILE_AVATAR
		);

		assertThat(result).isEqualTo("https://example.com/profile.jpg");
	}

	@Test
	void doesNotAppendDuplicatePreset() {
		ImageUrlResolver resolver = resolver(true, "https://media.tikitak.space");

		String result = resolver.resolve(
				"https://media.tikitak.space/media/feed-image/test.jpg?preset=feed_thumb",
				ImagePreset.FEED_DETAIL
		);

		assertThat(result).isEqualTo(
				"https://media.tikitak.space/media/feed-image/test.jpg?preset=feed_thumb");
	}

	private ImageUrlResolver resolver(boolean enabled, String publicBaseUrl) {
		R2Properties r2Properties = new R2Properties();
		r2Properties.setPublicBaseUrl(publicBaseUrl);
		ImageOptimizationProperties properties = new ImageOptimizationProperties();
		properties.setEnabled(enabled);
		return new ImageUrlResolver(r2Properties, properties);
	}
}
