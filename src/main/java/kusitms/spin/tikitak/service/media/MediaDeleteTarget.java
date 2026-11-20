package kusitms.spin.tikitak.service.media;

import kusitms.spin.tikitak.domain.media.entity.Media;

import java.util.UUID;

/**
 * R2 오브젝트 삭제에 필요한 값. 트랜잭션 밖에서 엔티티(LAZY 연관 포함)를 들고 다니지 않도록
 * {@link MediaDeletionService}가 엔티티 대신 이 값을 돌려준다.
 */
public record MediaDeleteTarget(Long mediaId, UUID publicId, String key) {

	static MediaDeleteTarget from(Media media) {
		return new MediaDeleteTarget(media.getId(), media.getPublicId(), media.getKey());
	}
}
