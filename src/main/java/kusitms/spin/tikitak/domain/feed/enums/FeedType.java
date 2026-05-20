package kusitms.spin.tikitak.domain.feed.enums;

import kusitms.spin.tikitak.domain.feed.entity.Feed;

import java.util.Objects;

public enum FeedType {
	GENERAL,
	DAILY_QUESTION;

	public static FeedType from(Feed feed) {
		Objects.requireNonNull(feed, "feed must not be null");
		return feed.hasQuestion() ? DAILY_QUESTION : GENERAL;
	}
}
