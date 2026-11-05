package kusitms.spin.tikitak.repository.media;

import kusitms.spin.tikitak.domain.media.entity.Media;
import kusitms.spin.tikitak.domain.media.entity.MediaUpload;
import kusitms.spin.tikitak.domain.media.enums.MediaPurpose;
import kusitms.spin.tikitak.domain.media.enums.MediaStatus;
import kusitms.spin.tikitak.domain.media.enums.MediaUploadStatus;
import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MediaUploadRepositoryIntegrationTest extends IntegrationTest {

	@Autowired
	private MediaUploadRepository mediaUploadRepository;

	@Autowired
	private MediaRepository mediaRepository;

	private Member testMember;

	@BeforeEach
	void setUp() {
		testMember = persist(member("cleanup"));
		flushAndClear();
	}

	@Test
	@DisplayName("만료되고 남은 media가 없는 mediaUpload는 조회 대상에 포함된다")
	void findExpiredMediaUploadIds_includesOrphanedExpiredUpload() {
		MediaUpload orphaned = persist(mediaUpload(MediaUploadStatus.COMPLETED, BASE_TIME.minusDays(1)));
		flushAndClear();

		List<Long> ids = mediaUploadRepository.findExpiredMediaUploadIds(BASE_TIME, PageRequest.of(0, 100));

		assertThat(ids).containsExactly(orphaned.getId());
	}

	@Test
	@DisplayName("살아있는 media가 남은 mediaUpload는 만료되어도 제외된다")
	void findExpiredMediaUploadIds_excludesUploadWithLiveChild() {
		MediaUpload upload = persist(mediaUpload(MediaUploadStatus.PENDING, BASE_TIME.minusDays(1)));
		persist(media(upload, MediaStatus.USED));
		flushAndClear();

		List<Long> ids = mediaUploadRepository.findExpiredMediaUploadIds(BASE_TIME, PageRequest.of(0, 100));

		assertThat(ids).doesNotContain(upload.getId());
	}

	@Test
	@DisplayName("아직 만료되지 않은 mediaUpload는 제외된다")
	void findExpiredMediaUploadIds_excludesNotYetExpired() {
		MediaUpload upload = persist(mediaUpload(MediaUploadStatus.COMPLETED, BASE_TIME.plusDays(1)));
		flushAndClear();

		List<Long> ids = mediaUploadRepository.findExpiredMediaUploadIds(BASE_TIME, PageRequest.of(0, 100));

		assertThat(ids).doesNotContain(upload.getId());
	}

	private MediaUpload mediaUpload(MediaUploadStatus status, LocalDateTime expiresAt) {
		return MediaUpload.builder()
				.purpose(MediaPurpose.FEED_IMAGE)
				.status(status)
				.memberId(testMember.getId())
				.expiresAt(expiresAt)
				.build();
	}

	private Media media(MediaUpload upload, MediaStatus status) {
		return Media.builder()
				.publicId(UUID.randomUUID())
				.purpose(MediaPurpose.FEED_IMAGE)
				.status(status)
				.fileName("f.png")
				.contentType("image/png")
				.size(1L)
				.key("media/feed-image/" + UUID.randomUUID() + ".png")
				.memberId(testMember.getId())
				.upload(upload)
				.build();
	}
}
