package kusitms.spin.tikitak.support.fixture;

import kusitms.spin.tikitak.domain.media.entity.Media;
import kusitms.spin.tikitak.domain.media.enums.MediaPurpose;
import kusitms.spin.tikitak.domain.media.enums.MediaStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public final class MediaFixture {

	private MediaFixture() {
	}

	public static Media uploadedFeedImage(Long id, Long memberId, UUID publicId) {
		return media(id, memberId, publicId, MediaPurpose.FEED_IMAGE, MediaStatus.UPLOADED);
	}

	public static Media media(
			Long id,
			Long memberId,
			UUID publicId,
			MediaPurpose purpose,
			MediaStatus status
	) {
		return Media.builder()
				.id(id)
				.publicId(publicId)
				.memberId(memberId)
				.purpose(purpose)
				.status(status)
				.fileName("feed.png")
				.contentType("image/png")
				.size(1024L)
				.url("https://example.com/feed.png")
				.key("media/feed-image/" + publicId + ".png")
				.createdAt(LocalDateTime.of(2026, 3, 4, 20, 30))
				.updatedAt(LocalDateTime.of(2026, 3, 4, 20, 30))
				.build();
	}
}
