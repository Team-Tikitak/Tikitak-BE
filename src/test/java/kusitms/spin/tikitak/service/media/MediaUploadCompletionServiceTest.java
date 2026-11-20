package kusitms.spin.tikitak.service.media;

import kusitms.spin.tikitak.domain.media.entity.Media;
import kusitms.spin.tikitak.domain.media.entity.MediaUpload;
import kusitms.spin.tikitak.domain.media.enums.MediaPurpose;
import kusitms.spin.tikitak.domain.media.enums.MediaStatus;
import kusitms.spin.tikitak.domain.media.enums.MediaUploadStatus;
import kusitms.spin.tikitak.global.config.R2Properties;
import kusitms.spin.tikitak.global.dto.media.MediaUploadCompleteRequest;
import kusitms.spin.tikitak.global.dto.media.MediaUploadCompleteResponse;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.repository.media.MediaRepository;
import kusitms.spin.tikitak.repository.media.MediaUploadRepository;
import kusitms.spin.tikitak.service.media.MediaUploadCompletionService.CompletionPlan;
import kusitms.spin.tikitak.service.media.MediaUploadCompletionService.UploadTarget;
import kusitms.spin.tikitak.service.media.MediaUploadCompletionService.UploadedObject;
import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaUploadCompletionServiceTest extends UnitTest {

	private static final Long MEMBER_ID = 1L;
	private static final Long OTHER_MEMBER_ID = 2L;
	private static final Long MEDIA_ID = 10L;
	private static final Long UPLOAD_ID = 20L;
	private static final UUID MEDIA_PUBLIC_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID OTHER_MEDIA_PUBLIC_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
	private static final UUID UPLOAD_PUBLIC_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
	private static final String OBJECT_KEY = "media/feed-image/11111111-1111-1111-1111-111111111111.png";
	private static final String CONTENT_TYPE = "image/png";
	private static final Long SIZE = 1000L;

	@Mock
	private MediaRepository mediaRepository;

	@Mock
	private MediaUploadRepository mediaUploadRepository;

	@Mock
	private R2Properties r2Properties;

	private MediaUploadCompletionService completionService;

	@BeforeEach
	void setUp() {
		completionService = new MediaUploadCompletionService(mediaRepository, mediaUploadRepository, r2Properties);
	}

	@Test
	@DisplayName("트랜잭션 설정: 사전 단계는 readOnly, 락 단계는 만료 예외에서 롤백하지 않는다")
	void transactionalSettings() throws NoSuchMethodException {
		Transactional prepare = MediaUploadCompletionService.class
				.getMethod("prepare", Long.class, UUID.class, List.class)
				.getAnnotation(Transactional.class);
		Transactional complete = MediaUploadCompletionService.class
				.getMethod("complete", Long.class, UUID.class, List.class, Map.class)
				.getAnnotation(Transactional.class);

		assertThat(prepare.readOnly()).isTrue();
		assertThat(complete.readOnly()).isFalse();
		assertThat(complete.noRollbackFor()).containsExactly(MediaUploadCompletionService.ExpiredUploadException.class);
	}

	@Test
	@DisplayName("사전 단계: 락 없이 읽고 headObject 대상 값만 반환한다")
	void prepareReturnsTargetsWithoutLock() {
		when(mediaUploadRepository.findByPublicId(UPLOAD_PUBLIC_ID)).thenReturn(Optional.of(upload(notExpired())));
		when(mediaRepository.findByUploadId(UPLOAD_ID)).thenReturn(List.of(media(MediaStatus.PENDING)));

		CompletionPlan plan = completionService.prepare(MEMBER_ID, UPLOAD_PUBLIC_ID, items());

		assertThat(plan.expired()).isFalse();
		assertThat(plan.targets()).containsExactly(new UploadTarget(MEDIA_ID, OBJECT_KEY, CONTENT_TYPE, SIZE));
		verify(mediaUploadRepository, never()).findByPublicIdForUpdate(any(UUID.class));
		verify(mediaRepository, never()).findByUploadIdForUpdate(any(Long.class));
	}

	@Test
	@DisplayName("사전 단계: 만료된 upload는 예외 없이 만료 신호를 반환하고 상태를 바꾸지 않는다")
	void prepareReturnsExpiredPlan() {
		MediaUpload upload = upload(LocalDateTime.now().minusMinutes(1));
		when(mediaUploadRepository.findByPublicId(UPLOAD_PUBLIC_ID)).thenReturn(Optional.of(upload));

		CompletionPlan plan = completionService.prepare(MEMBER_ID, UPLOAD_PUBLIC_ID, items());

		assertThat(plan.expired()).isTrue();
		assertThat(plan.targets()).isEmpty();
		assertThat(upload.getStatus()).isEqualTo(MediaUploadStatus.PENDING);
		verify(mediaRepository, never()).findByUploadId(any(Long.class));
	}

	@Test
	@DisplayName("사전 단계: upload가 없으면 MEDIA016")
	void prepareThrowsWhenUploadNotFound() {
		when(mediaUploadRepository.findByPublicId(UPLOAD_PUBLIC_ID)).thenReturn(Optional.empty());

		assertErrorCode(() -> completionService.prepare(MEMBER_ID, UPLOAD_PUBLIC_ID, items()), ErrorCode.MEDIA016);
	}

	@Test
	@DisplayName("사전 단계: 다른 회원의 upload면 MEDIA015")
	void prepareThrowsWhenNotOwner() {
		when(mediaUploadRepository.findByPublicId(UPLOAD_PUBLIC_ID))
				.thenReturn(Optional.of(upload(OTHER_MEMBER_ID, MediaUploadStatus.PENDING, notExpired())));

		assertErrorCode(() -> completionService.prepare(MEMBER_ID, UPLOAD_PUBLIC_ID, items()), ErrorCode.MEDIA015);
		verify(mediaRepository, never()).findByUploadId(any(Long.class));
	}

	@Test
	@DisplayName("사전 단계: upload가 PENDING이 아니면 만료 여부와 관계없이 MEDIA014")
	void prepareThrowsWhenUploadNotPending() {
		when(mediaUploadRepository.findByPublicId(UPLOAD_PUBLIC_ID))
				.thenReturn(Optional.of(upload(MEMBER_ID, MediaUploadStatus.COMPLETED, LocalDateTime.now().minusMinutes(1))));

		assertErrorCode(() -> completionService.prepare(MEMBER_ID, UPLOAD_PUBLIC_ID, items()), ErrorCode.MEDIA014);
		verify(mediaRepository, never()).findByUploadId(any(Long.class));
	}

	@Test
	@DisplayName("사전 단계: media 수가 요청과 다르면 MEDIA013")
	void prepareThrowsWhenMediaCountDiffers() {
		when(mediaUploadRepository.findByPublicId(UPLOAD_PUBLIC_ID)).thenReturn(Optional.of(upload(notExpired())));
		when(mediaRepository.findByUploadId(UPLOAD_ID)).thenReturn(List.of());

		assertErrorCode(() -> completionService.prepare(MEMBER_ID, UPLOAD_PUBLIC_ID, items()), ErrorCode.MEDIA013);
	}

	@Test
	@DisplayName("사전 단계: 요청 publicId가 media와 다르면 MEDIA013")
	void prepareThrowsWhenPublicIdDiffers() {
		when(mediaUploadRepository.findByPublicId(UPLOAD_PUBLIC_ID)).thenReturn(Optional.of(upload(notExpired())));
		when(mediaRepository.findByUploadId(UPLOAD_ID)).thenReturn(List.of(media(MediaStatus.PENDING)));
		List<MediaUploadCompleteRequest.Item> items = List.of(
				new MediaUploadCompleteRequest.Item(OTHER_MEDIA_PUBLIC_ID, CONTENT_TYPE, SIZE));

		assertErrorCode(() -> completionService.prepare(MEMBER_ID, UPLOAD_PUBLIC_ID, items), ErrorCode.MEDIA013);
	}

	@Test
	@DisplayName("사전 단계: media가 PENDING이 아니면 MEDIA014")
	void prepareThrowsWhenMediaNotPending() {
		when(mediaUploadRepository.findByPublicId(UPLOAD_PUBLIC_ID)).thenReturn(Optional.of(upload(notExpired())));
		when(mediaRepository.findByUploadId(UPLOAD_ID)).thenReturn(List.of(media(MediaStatus.UPLOADED)));

		assertErrorCode(() -> completionService.prepare(MEMBER_ID, UPLOAD_PUBLIC_ID, items()), ErrorCode.MEDIA014);
	}

	@Test
	@DisplayName("사전 단계: 요청 타입이 media와 다르면 MEDIA013")
	void prepareThrowsWhenContentTypeDiffers() {
		when(mediaUploadRepository.findByPublicId(UPLOAD_PUBLIC_ID)).thenReturn(Optional.of(upload(notExpired())));
		when(mediaRepository.findByUploadId(UPLOAD_ID)).thenReturn(List.of(media(MediaStatus.PENDING)));
		List<MediaUploadCompleteRequest.Item> items = List.of(
				new MediaUploadCompleteRequest.Item(MEDIA_PUBLIC_ID, "image/jpeg", SIZE));

		assertErrorCode(() -> completionService.prepare(MEMBER_ID, UPLOAD_PUBLIC_ID, items), ErrorCode.MEDIA013);
	}

	@Test
	@DisplayName("락 단계: 모든 검증을 통과하면 media는 UPLOADED, upload는 COMPLETED가 된다")
	void completeTransitionsStatus() {
		MediaUpload upload = upload(notExpired());
		Media media = media(MediaStatus.PENDING);
		when(mediaUploadRepository.findByPublicIdForUpdate(UPLOAD_PUBLIC_ID)).thenReturn(Optional.of(upload));
		when(mediaRepository.findByUploadIdForUpdate(UPLOAD_ID)).thenReturn(List.of(media));
		when(r2Properties.getPublicBaseUrl()).thenReturn("https://media.tikitak.xyz/");

		MediaUploadCompleteResponse response = completionService.complete(
				MEMBER_ID, UPLOAD_PUBLIC_ID, items(), Map.of(MEDIA_ID, new UploadedObject(CONTENT_TYPE, SIZE)));

		assertThat(upload.getStatus()).isEqualTo(MediaUploadStatus.COMPLETED);
		assertThat(media.getStatus()).isEqualTo(MediaStatus.UPLOADED);
		assertThat(media.getUrl()).isEqualTo("https://media.tikitak.xyz/" + OBJECT_KEY);
		assertThat(response.getUploadStatus()).isEqualTo(MediaUploadStatus.COMPLETED);
		assertThat(response.getItems()).hasSize(1);
	}

	@Test
	@DisplayName("락 단계: 만료된 upload는 expire()를 반영하고 MEDIA014 만료 예외를 던진다")
	void completeExpiresUpload() {
		MediaUpload upload = upload(LocalDateTime.now().minusMinutes(1));
		when(mediaUploadRepository.findByPublicIdForUpdate(UPLOAD_PUBLIC_ID)).thenReturn(Optional.of(upload));

		assertThatThrownBy(() -> completionService.complete(MEMBER_ID, UPLOAD_PUBLIC_ID, items(), Map.of()))
				.isInstanceOfSatisfying(MediaUploadCompletionService.ExpiredUploadException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEDIA014));
		assertThat(upload.getStatus()).isEqualTo(MediaUploadStatus.EXPIRED);
		verify(mediaRepository, never()).findByUploadIdForUpdate(any(Long.class));
	}

	@Test
	@DisplayName("락 단계: 사전 단계 뒤 다른 요청이 upload를 완료했으면 MEDIA014")
	void completeThrowsWhenUploadCompletedConcurrently() {
		when(mediaUploadRepository.findByPublicIdForUpdate(UPLOAD_PUBLIC_ID))
				.thenReturn(Optional.of(upload(MEMBER_ID, MediaUploadStatus.COMPLETED, notExpired())));

		assertErrorCode(() -> completionService.complete(
				MEMBER_ID, UPLOAD_PUBLIC_ID, items(), Map.of(MEDIA_ID, new UploadedObject(CONTENT_TYPE, SIZE))),
				ErrorCode.MEDIA014);
		verify(mediaRepository, never()).findByUploadIdForUpdate(any(Long.class));
	}

	@Test
	@DisplayName("락 단계: 사전 단계 뒤 upload가 지워졌으면 MEDIA016")
	void completeThrowsWhenUploadDeletedConcurrently() {
		when(mediaUploadRepository.findByPublicIdForUpdate(UPLOAD_PUBLIC_ID)).thenReturn(Optional.empty());

		assertErrorCode(() -> completionService.complete(
				MEMBER_ID, UPLOAD_PUBLIC_ID, items(), Map.of(MEDIA_ID, new UploadedObject(CONTENT_TYPE, SIZE))),
				ErrorCode.MEDIA016);
	}

	@Test
	@DisplayName("락 단계: media가 PENDING이 아니면 MEDIA014이고 상태를 바꾸지 않는다")
	void completeThrowsWhenMediaNotPending() {
		MediaUpload upload = upload(notExpired());
		Media media = media(MediaStatus.DELETED);
		when(mediaUploadRepository.findByPublicIdForUpdate(UPLOAD_PUBLIC_ID)).thenReturn(Optional.of(upload));
		when(mediaRepository.findByUploadIdForUpdate(UPLOAD_ID)).thenReturn(List.of(media));

		assertErrorCode(() -> completionService.complete(
				MEMBER_ID, UPLOAD_PUBLIC_ID, items(), Map.of(MEDIA_ID, new UploadedObject(CONTENT_TYPE, SIZE))),
				ErrorCode.MEDIA014);
		assertThat(media.getStatus()).isEqualTo(MediaStatus.DELETED);
		assertThat(upload.getStatus()).isEqualTo(MediaUploadStatus.PENDING);
		verify(r2Properties, never()).getPublicBaseUrl();
	}

	@Test
	@DisplayName("락 단계: R2 확인 결과가 없는 media는 MEDIA013")
	void completeThrowsWhenUploadedObjectMissing() {
		MediaUpload upload = upload(notExpired());
		Media media = media(MediaStatus.PENDING);
		when(mediaUploadRepository.findByPublicIdForUpdate(UPLOAD_PUBLIC_ID)).thenReturn(Optional.of(upload));
		when(mediaRepository.findByUploadIdForUpdate(UPLOAD_ID)).thenReturn(List.of(media));

		assertErrorCode(() -> completionService.complete(MEMBER_ID, UPLOAD_PUBLIC_ID, items(), Map.of()), ErrorCode.MEDIA013);
		assertThat(media.getStatus()).isEqualTo(MediaStatus.PENDING);
		assertThat(upload.getStatus()).isEqualTo(MediaUploadStatus.PENDING);
	}

	@Test
	@DisplayName("락 단계: R2 확인 결과의 타입이 media와 다르면 MEDIA013")
	void completeThrowsWhenUploadedObjectDiffers() {
		MediaUpload upload = upload(notExpired());
		Media media = media(MediaStatus.PENDING);
		when(mediaUploadRepository.findByPublicIdForUpdate(UPLOAD_PUBLIC_ID)).thenReturn(Optional.of(upload));
		when(mediaRepository.findByUploadIdForUpdate(UPLOAD_ID)).thenReturn(List.of(media));

		assertErrorCode(() -> completionService.complete(
				MEMBER_ID, UPLOAD_PUBLIC_ID, items(), Map.of(MEDIA_ID, new UploadedObject("image/jpeg", SIZE))),
				ErrorCode.MEDIA013);
		assertThat(media.getStatus()).isEqualTo(MediaStatus.PENDING);
	}

	@Test
	@DisplayName("UploadedObject: 크기가 없거나 다르면 일치하지 않는다")
	void uploadedObjectMatches() {
		assertThat(new UploadedObject(CONTENT_TYPE, SIZE).matches(CONTENT_TYPE, SIZE)).isTrue();
		assertThat(new UploadedObject(CONTENT_TYPE, null).matches(CONTENT_TYPE, SIZE)).isFalse();
		assertThat(new UploadedObject(CONTENT_TYPE, 999L).matches(CONTENT_TYPE, SIZE)).isFalse();
		assertThat(new UploadedObject(null, SIZE).matches(CONTENT_TYPE, SIZE)).isFalse();
	}

	private static void assertErrorCode(Runnable call, ErrorCode errorCode) {
		assertThatThrownBy(call::run)
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(errorCode));
	}

	private static LocalDateTime notExpired() {
		return LocalDateTime.now().plusDays(1);
	}

	private static MediaUpload upload(LocalDateTime expiresAt) {
		return upload(MEMBER_ID, MediaUploadStatus.PENDING, expiresAt);
	}

	private static MediaUpload upload(Long memberId, MediaUploadStatus status, LocalDateTime expiresAt) {
		return MediaUpload.builder()
				.id(UPLOAD_ID)
				.publicId(UPLOAD_PUBLIC_ID)
				.purpose(MediaPurpose.FEED_IMAGE)
				.status(status)
				.memberId(memberId)
				.expiresAt(expiresAt)
				.build();
	}

	private static Media media(MediaStatus status) {
		return Media.builder()
				.id(MEDIA_ID)
				.publicId(MEDIA_PUBLIC_ID)
				.purpose(MediaPurpose.FEED_IMAGE)
				.status(status)
				.fileName("test.png")
				.contentType(CONTENT_TYPE)
				.size(SIZE)
				.key(OBJECT_KEY)
				.memberId(MEMBER_ID)
				.build();
	}

	private static List<MediaUploadCompleteRequest.Item> items() {
		return List.of(new MediaUploadCompleteRequest.Item(MEDIA_PUBLIC_ID, CONTENT_TYPE, SIZE));
	}
}
