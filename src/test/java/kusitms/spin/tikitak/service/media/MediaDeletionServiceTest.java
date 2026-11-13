package kusitms.spin.tikitak.service.media;

import kusitms.spin.tikitak.domain.media.entity.Media;
import kusitms.spin.tikitak.domain.media.enums.MediaPurpose;
import kusitms.spin.tikitak.domain.media.enums.MediaStatus;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.repository.media.MediaRepository;
import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaDeletionServiceTest extends UnitTest {

	private static final Long MEMBER_ID = 1L;
	private static final Long OTHER_MEMBER_ID = 2L;
	private static final Long MEDIA_ID = 10L;
	private static final UUID MEDIA_PUBLIC_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final String OBJECT_KEY = "media/feed-image/11111111-1111-1111-1111-111111111111.png";

	@Mock
	private MediaRepository mediaRepository;

	private MediaDeletionService mediaDeletionService;

	@BeforeEach
	void setUp() {
		mediaDeletionService = new MediaDeletionService(mediaRepository);
	}

	@ParameterizedTest
	@EnumSource(value = MediaStatus.class, names = {"PENDING", "UPLOADED"})
	@DisplayName("사용하지 않은 본인 미디어를 락을 잡고 DELETED로 바꾸고 R2 삭제 대상을 돌려준다")
	void markUnusedMediaDeleted(MediaStatus status) {
		Media media = media(status, MEMBER_ID);
		when(mediaRepository.findByPublicIdForUpdate(MEDIA_PUBLIC_ID)).thenReturn(Optional.of(media));

		MediaDeleteTarget target = mediaDeletionService.markUnusedMediaDeleted(MEMBER_ID, MEDIA_PUBLIC_ID);

		assertThat(media.getStatus()).isEqualTo(MediaStatus.DELETED);
		assertThat(media.getDeletedAt()).isNotNull();
		assertThat(target).isEqualTo(new MediaDeleteTarget(MEDIA_ID, MEDIA_PUBLIC_ID, OBJECT_KEY));
	}

	@Test
	@DisplayName("미디어가 없으면 MEDIA009 예외가 발생한다")
	void markUnusedMediaDeletedThrowsWhenNotFound() {
		when(mediaRepository.findByPublicIdForUpdate(MEDIA_PUBLIC_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> mediaDeletionService.markUnusedMediaDeleted(MEMBER_ID, MEDIA_PUBLIC_ID))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEDIA009));
	}

	@Test
	@DisplayName("DELETED 미디어는 소유자 검사보다 먼저 MEDIA009 예외가 발생한다")
	void markUnusedMediaDeletedThrowsWhenAlreadyDeletedBeforeOwnerCheck() {
		Media media = media(MediaStatus.DELETED, OTHER_MEMBER_ID);
		when(mediaRepository.findByPublicIdForUpdate(MEDIA_PUBLIC_ID)).thenReturn(Optional.of(media));

		assertThatThrownBy(() -> mediaDeletionService.markUnusedMediaDeleted(MEMBER_ID, MEDIA_PUBLIC_ID))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEDIA009));
		assertThat(media.getDeletedAt()).isNull();
	}

	@Test
	@DisplayName("업로드한 사용자가 아니면 USED 검사보다 먼저 MEDIA008 예외가 발생한다")
	void markUnusedMediaDeletedThrowsWhenNotOwnerBeforeUsedCheck() {
		Media media = media(MediaStatus.USED, OTHER_MEMBER_ID);
		when(mediaRepository.findByPublicIdForUpdate(MEDIA_PUBLIC_ID)).thenReturn(Optional.of(media));

		assertThatThrownBy(() -> mediaDeletionService.markUnusedMediaDeleted(MEMBER_ID, MEDIA_PUBLIC_ID))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEDIA008));
		assertThat(media.getStatus()).isEqualTo(MediaStatus.USED);
	}

	@Test
	@DisplayName("USED 미디어는 MEDIA007 예외가 발생하고 상태를 바꾸지 않는다")
	void markUnusedMediaDeletedThrowsWhenUsed() {
		Media media = media(MediaStatus.USED, MEMBER_ID);
		when(mediaRepository.findByPublicIdForUpdate(MEDIA_PUBLIC_ID)).thenReturn(Optional.of(media));

		assertThatThrownBy(() -> mediaDeletionService.markUnusedMediaDeleted(MEMBER_ID, MEDIA_PUBLIC_ID))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEDIA007));
		assertThat(media.getStatus()).isEqualTo(MediaStatus.USED);
	}

	@ParameterizedTest
	@EnumSource(value = MediaStatus.class, names = {"PENDING", "UPLOADED"})
	@DisplayName("만료 미디어가 기대 상태면 락을 잡고 DELETED로 바꾸고 R2 삭제 대상을 돌려준다")
	void markExpiredMediaDeleted(MediaStatus status) {
		Media media = media(status, MEMBER_ID);
		when(mediaRepository.findByIdForUpdate(MEDIA_ID)).thenReturn(Optional.of(media));

		Optional<MediaDeleteTarget> target = mediaDeletionService.markExpiredMediaDeleted(MEDIA_ID, status);

		assertThat(target).contains(new MediaDeleteTarget(MEDIA_ID, MEDIA_PUBLIC_ID, OBJECT_KEY));
		assertThat(media.getStatus()).isEqualTo(MediaStatus.DELETED);
	}

	@Test
	@DisplayName("만료 미디어의 상태가 기대와 다르면 바꾸지 않고 빈 값을 돌려준다")
	void markExpiredMediaDeletedSkipsWhenStatusChanged() {
		Media media = media(MediaStatus.USED, MEMBER_ID);
		when(mediaRepository.findByIdForUpdate(MEDIA_ID)).thenReturn(Optional.of(media));

		Optional<MediaDeleteTarget> target = mediaDeletionService.markExpiredMediaDeleted(MEDIA_ID, MediaStatus.UPLOADED);

		assertThat(target).isEmpty();
		assertThat(media.getStatus()).isEqualTo(MediaStatus.USED);
	}

	@Test
	@DisplayName("만료 미디어가 없으면 빈 값을 돌려준다")
	void markExpiredMediaDeletedSkipsWhenNotFound() {
		when(mediaRepository.findByIdForUpdate(MEDIA_ID)).thenReturn(Optional.empty());

		assertThat(mediaDeletionService.markExpiredMediaDeleted(MEDIA_ID, MediaStatus.PENDING)).isEmpty();
	}

	@Test
	@DisplayName("락을 잡고 아직 DELETED면 행을 삭제한다")
	void deleteRowIfStillDeleted() {
		Media media = media(MediaStatus.DELETED, MEMBER_ID);
		when(mediaRepository.findByIdForUpdate(MEDIA_ID)).thenReturn(Optional.of(media));

		boolean deleted = mediaDeletionService.deleteRowIfStillDeleted(MEDIA_ID);

		assertThat(deleted).isTrue();
		verify(mediaRepository).delete(media);
	}

	@Test
	@DisplayName("락 단계에서 DELETED가 아니면 행을 삭제하지 않는다")
	void deleteRowIfStillDeletedSkipsWhenNotDeleted() {
		when(mediaRepository.findByIdForUpdate(MEDIA_ID)).thenReturn(Optional.of(media(MediaStatus.USED, MEMBER_ID)));

		boolean deleted = mediaDeletionService.deleteRowIfStillDeleted(MEDIA_ID);

		assertThat(deleted).isFalse();
		verify(mediaRepository, never()).delete(any(Media.class));
	}

	@Test
	@DisplayName("락 단계에서 행이 없으면 삭제하지 않는다")
	void deleteRowIfStillDeletedSkipsWhenNotFound() {
		when(mediaRepository.findByIdForUpdate(MEDIA_ID)).thenReturn(Optional.empty());

		boolean deleted = mediaDeletionService.deleteRowIfStillDeleted(MEDIA_ID);

		assertThat(deleted).isFalse();
		verify(mediaRepository, never()).delete(any(Media.class));
	}

	private Media media(MediaStatus status, Long memberId) {
		return Media.builder()
				.id(MEDIA_ID)
				.publicId(MEDIA_PUBLIC_ID)
				.purpose(MediaPurpose.FEED_IMAGE)
				.status(status)
				.fileName("test.png")
				.contentType("image/png")
				.size(1000L)
				.key(OBJECT_KEY)
				.memberId(memberId)
				.build();
	}
}
