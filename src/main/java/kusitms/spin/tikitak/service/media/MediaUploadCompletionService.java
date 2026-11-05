package kusitms.spin.tikitak.service.media;

import kusitms.spin.tikitak.domain.media.entity.Media;
import kusitms.spin.tikitak.domain.media.entity.MediaUpload;
import kusitms.spin.tikitak.domain.media.enums.MediaStatus;
import kusitms.spin.tikitak.domain.media.enums.MediaUploadStatus;
import kusitms.spin.tikitak.global.config.R2Properties;
import kusitms.spin.tikitak.global.dto.media.MediaUploadCompleteRequest;
import kusitms.spin.tikitak.global.dto.media.MediaUploadCompleteResponse;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.repository.media.MediaRepository;
import kusitms.spin.tikitak.repository.media.MediaUploadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 업로드 완료의 DB 단계를 R2 {@code headObject} 앞뒤 두 트랜잭션으로 나눠 실행한다.
 * {@code headObject}는 {@link MediaService}에서 두 단계 사이, 트랜잭션 밖에서 호출한다.
 *
 * <ol>
 *   <li>{@link #prepare}: 락 없이 읽고 검증한 뒤 {@code headObject}에 필요한 값만 반환한다(엔티티 반환 금지, 트랜잭션 밖 LAZY 접근 방지).</li>
 *   <li>{@link #complete}: PESSIMISTIC_WRITE 락을 잡고 모든 검증을 다시 한 뒤 상태를 바꾼다.
 *       사전 단계와 락 단계 사이에 다른 요청(미디어 삭제, 정리 배치, 중복 완료 요청)이 상태를 바꿀 수 있기 때문이다.</li>
 * </ol>
 *
 * <p>upload 만료 전이({@code expire()})는 업로드 완료 경로에서만 일어나므로 락 단계에서
 * {@link ExpiredUploadException}을 던져도 롤백하지 않고 커밋한다.
 */
@Service
@RequiredArgsConstructor
public class MediaUploadCompletionService {

	private final MediaRepository mediaRepository;
	private final MediaUploadRepository mediaUploadRepository;
	private final R2Properties r2Properties;

	/**
	 * 락 없이 upload·media를 읽어 검증한다. 만료된 upload는 예외 없이 {@link CompletionPlan#expiredPlan()}을 반환해
	 * 호출자가 {@code headObject}를 건너뛰고 락 단계에서 만료 전이를 저장하게 한다.
	 */
	@Transactional(readOnly = true)
	public CompletionPlan prepare(Long memberId, UUID uploadId, List<MediaUploadCompleteRequest.Item> items) {
		MediaUpload upload = mediaUploadRepository.findByPublicId(uploadId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEDIA016));
		validateUpload(upload, memberId);
		if (upload.isExpired(LocalDateTime.now())) {
			return CompletionPlan.expiredPlan();
		}

		List<Media> medias = mediaRepository.findByUploadId(upload.getId());
		Map<UUID, MediaUploadCompleteRequest.Item> requestItemsByPublicId = validateMediaCount(medias, items);
		List<UploadTarget> targets = medias.stream()
				.map(media -> {
					validateCompletableMedia(media, requestItemsByPublicId);
					return new UploadTarget(media.getId(), media.getKey(), media.getContentType(), media.getSize());
				})
				.toList();
		return new CompletionPlan(false, targets);
	}

	/**
	 * 락을 잡고 모든 검증을 다시 한 뒤 media를 UPLOADED, upload를 COMPLETED로 바꾼다.
	 *
	 * @param uploadedObjects {@code headObject} 결과. media ID별
	 */
	@Transactional(noRollbackFor = ExpiredUploadException.class)
	public MediaUploadCompleteResponse complete(
			Long memberId,
			UUID uploadId,
			List<MediaUploadCompleteRequest.Item> items,
			Map<Long, UploadedObject> uploadedObjects
	) {
		MediaUpload upload = mediaUploadRepository.findByPublicIdForUpdate(uploadId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEDIA016));
		validateUpload(upload, memberId);
		if (upload.isExpired(LocalDateTime.now())) {
			upload.expire();
			throw new ExpiredUploadException();
		}

		List<Media> medias = mediaRepository.findByUploadIdForUpdate(upload.getId());
		Map<UUID, MediaUploadCompleteRequest.Item> requestItemsByPublicId = validateMediaCount(medias, items);
		medias.forEach(media -> {
			MediaUploadCompleteRequest.Item item = validateCompletableMedia(media, requestItemsByPublicId);
			UploadedObject object = uploadedObjects.get(media.getId());
			// 사전 단계에서 확인하지 않은 media는 R2 검증 결과가 없으므로 완료하지 않는다.
			if (object == null || !object.matches(media.getContentType(), item.getSize())) {
				throw new BusinessException(ErrorCode.MEDIA013);
			}
		});

		List<MediaUploadCompleteResponse.Item> responseItems = medias.stream()
				.map(this::completeMedia)
				.toList();
		upload.complete();
		return new MediaUploadCompleteResponse(
				upload.getPublicId(),
				upload.getStatus(),
				upload.getCompletedAt(),
				responseItems
		);
	}

	private void validateUpload(MediaUpload upload, Long memberId) {
		if (!upload.getMemberId().equals(memberId)) {
			throw new BusinessException(ErrorCode.MEDIA015);
		}
		if (upload.getStatus() != MediaUploadStatus.PENDING) {
			throw new BusinessException(ErrorCode.MEDIA014);
		}
	}

	private Map<UUID, MediaUploadCompleteRequest.Item> validateMediaCount(
			List<Media> medias,
			List<MediaUploadCompleteRequest.Item> items
	) {
		if (medias.size() != items.size()) {
			throw new BusinessException(ErrorCode.MEDIA013);
		}
		return items.stream()
				.collect(Collectors.toMap(
						MediaUploadCompleteRequest.Item::getMediaPublicId,
						item -> item,
						(left, right) -> {
							throw new BusinessException(ErrorCode.MEDIA013);
						}
				));
	}

	private MediaUploadCompleteRequest.Item validateCompletableMedia(
			Media media,
			Map<UUID, MediaUploadCompleteRequest.Item> requestItemsByPublicId
	) {
		MediaUploadCompleteRequest.Item item = requestItemsByPublicId.get(media.getPublicId());
		if (item == null) {
			throw new BusinessException(ErrorCode.MEDIA013);
		}
		if (media.getStatus() != MediaStatus.PENDING) {
			throw new BusinessException(ErrorCode.MEDIA014);
		}
		if (!media.getContentType().equals(item.getContentType()) || !media.getSize().equals(item.getSize())) {
			throw new BusinessException(ErrorCode.MEDIA013);
		}
		return item;
	}

	private MediaUploadCompleteResponse.Item completeMedia(Media media) {
		String imageUrl = buildPublicUrl(media);
		media.completeUpload(imageUrl);
		return new MediaUploadCompleteResponse.Item(
				media.getPublicId(),
				media.getStatus(),
				MediaUploadStatus.COMPLETED,
				imageUrl
		);
	}

	private String buildPublicUrl(Media media) {
		String publicBaseUrl = r2Properties.getPublicBaseUrl();
		if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
			return media.getKey();
		}
		return publicBaseUrl.replaceAll("/+$", "") + "/" + media.getKey();
	}

	/** 사전 단계 결과. {@code expired}가 true면 {@code targets}는 비어 있고 {@code headObject}를 하지 않는다. */
	public record CompletionPlan(boolean expired, List<UploadTarget> targets) {

		static CompletionPlan expiredPlan() {
			return new CompletionPlan(true, List.of());
		}
	}

	/** {@code headObject} 대상. 기대 contentType·size는 사전 단계에서 요청 값과 일치함을 확인한 media 값이다. */
	public record UploadTarget(Long mediaId, String key, String contentType, Long size) {
	}

	/** {@code headObject} 결과 중 완료 검증에 쓰는 값. */
	public record UploadedObject(String contentType, Long contentLength) {

		public boolean matches(String expectedContentType, Long expectedSize) {
			return contentLength != null
					&& contentLength.equals(expectedSize)
					&& expectedContentType.equals(contentType);
		}
	}

	static class ExpiredUploadException extends BusinessException {
		ExpiredUploadException() {
			super(ErrorCode.MEDIA014);
		}
	}
}
