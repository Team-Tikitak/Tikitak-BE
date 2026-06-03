package kusitms.spin.tikitak.service.media;

import kusitms.spin.tikitak.global.config.ImageOptimizationProperties;
import kusitms.spin.tikitak.global.config.R2Properties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
	void appendsFeedHeroPreviewPresetQueryToR2PublicUrl() {
		ImageUrlResolver resolver = resolver(true, "https://media.tikitak.space/");

		String result = resolver.resolve(
				"https://media.tikitak.space/media/feed-image/test.jpg",
				ImagePreset.FEED_HERO_PREVIEW
		);

		assertThat(result).isEqualTo(
				"https://media.tikitak.space/media/feed-image/test.jpg?preset=feed_hero_preview");
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

	@Test
	void validateConfigurationThrowsWhenPublicBaseUrlMissingAndOptimizationEnabled() {
		ImageUrlResolver resolver = resolver(true, "");

		assertThatThrownBy(resolver::validateConfiguration)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("r2.public-base-url");
	}

	@Test
	void validateConfigurationThrowsWhenPresetQueryParamMissingAndOptimizationEnabled() {
		R2Properties r2Properties = new R2Properties();
		r2Properties.setPublicBaseUrl("https://media.tikitak.space");
		ImageOptimizationProperties properties = new ImageOptimizationProperties();
		properties.setPresetQueryParam("");
		ImageUrlResolver resolver = new ImageUrlResolver(r2Properties, properties);

		assertThatThrownBy(resolver::validateConfiguration)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("image.optimization.preset-query-param");
	}

	private ImageUrlResolver resolver(boolean enabled, String publicBaseUrl) {
		R2Properties r2Properties = new R2Properties();
		r2Properties.setPublicBaseUrl(publicBaseUrl);
		ImageOptimizationProperties properties = new ImageOptimizationProperties();
		properties.setEnabled(enabled);
		return new ImageUrlResolver(r2Properties, properties);
	}
}
