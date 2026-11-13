package kusitms.spin.tikitak.service.media;

import kusitms.spin.tikitak.domain.media.entity.Media;
import kusitms.spin.tikitak.domain.media.enums.MediaStatus;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.repository.media.MediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * 미디어 삭제·정리 배치의 DB 단계. 각 메서드는 PESSIMISTIC_WRITE 락을 잡고 상태를 다시 확인한 뒤 DB만 바꾼다.
 * R2 오브젝트 삭제는 {@link MediaService}가 이 빈의 트랜잭션이 커밋된 뒤, 트랜잭션 밖에서 호출한다.
 * 엔티티 대신 {@link MediaDeleteTarget}을 돌려준다(트랜잭션 밖 LAZY 접근 방지).
 */
@Service
@RequiredArgsConstructor
public class MediaDeletionService {

	private final MediaRepository mediaRepository;

	/**
	 * 사용하지 않은 본인 미디어를 DELETED로 바꾼다. 검증 순서: 없음/DELETED(MEDIA009) → 소유자(MEDIA008) → USED(MEDIA007).
	 */
	@Transactional
	public MediaDeleteTarget markUnusedMediaDeleted(Long memberId, UUID mediaPublicId) {
		Media media = mediaRepository.findByPublicIdForUpdate(mediaPublicId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEDIA009));

		if (media.getStatus() == MediaStatus.DELETED) {
			throw new BusinessException(ErrorCode.MEDIA009);
		}
		if (!media.getMemberId().equals(memberId)) {
			throw new BusinessException(ErrorCode.MEDIA008);
		}
		if (media.getStatus() == MediaStatus.USED) {
			throw new BusinessException(ErrorCode.MEDIA007);
		}

		media.updateStatus(MediaStatus.DELETED);
		return MediaDeleteTarget.from(media);
	}

	/**
	 * 만료된 미디어가 아직 {@code expectedStatus}이면 DELETED로 바꾼다. 없거나 상태가 다르면 아무것도 바꾸지 않고 빈 값을 돌려준다.
	 */
	@Transactional
	public Optional<MediaDeleteTarget> markExpiredMediaDeleted(Long mediaId, MediaStatus expectedStatus) {
		Media media = mediaRepository.findByIdForUpdate(mediaId)
				.orElse(null);

		if (media == null || media.getStatus() != expectedStatus) {
			return Optional.empty();
		}

		media.updateStatus(MediaStatus.DELETED);
		return Optional.of(MediaDeleteTarget.from(media));
	}

	/**
	 * R2 오브젝트를 지운 뒤 DELETED 미디어 행을 하드 삭제한다. 락을 잡고 아직 DELETED인지 다시 확인하며,
	 * 없거나 DELETED가 아니면 삭제하지 않고 false를 돌려준다.
	 */
	@Transactional
	public boolean deleteRowIfStillDeleted(Long mediaId) {
		Media media = mediaRepository.findByIdForUpdate(mediaId)
				.orElse(null);

		if (media == null || media.getStatus() != MediaStatus.DELETED) {
			return false;
		}

		mediaRepository.delete(media);
		return true;
	}
}
