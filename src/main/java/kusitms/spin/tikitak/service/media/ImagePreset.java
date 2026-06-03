package kusitms.spin.tikitak.service.media;

public enum ImagePreset {
	PROFILE_AVATAR("profile_avatar"),
	FEED_THUMB("feed_thumb"),
	FEED_DETAIL("feed_detail"),
	FEED_HERO_PREVIEW("feed_hero_preview"),
	PLACE_CARD("place_card"),
	ORIGINAL(null);

	private final String queryValue;

	ImagePreset(String queryValue) {
		this.queryValue = queryValue;
	}

	public String queryValue() {
		return queryValue;
	}
}
