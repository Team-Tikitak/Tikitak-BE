package kusitms.spin.tikitak.service.team;

import kusitms.spin.tikitak.domain.media.entity.Media;
import kusitms.spin.tikitak.domain.media.enums.MediaPurpose;
import kusitms.spin.tikitak.domain.media.enums.MediaStatus;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.repository.media.MediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 팀 프로필 이미지로 붙일 media를 검증하고 USED로 바꾼다.
 * 트랜잭션은 열지 않고 호출한 서비스의 트랜잭션에 참여한다.
 */
@Component
@RequiredArgsConstructor
public class TeamProfileMediaResolver {

	private final MediaRepository mediaRepository;

	public Media resolve(Long memberId, UUID mediaPublicId) {
		if (mediaPublicId == null) return null;
		Media media = mediaRepository.findByPublicIdForUpdate(mediaPublicId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEDIA018));
		if (!media.getMemberId().equals(memberId)
				|| media.getPurpose() != MediaPurpose.PROFILE_IMAGE
				|| media.getStatus() != MediaStatus.UPLOADED) {
			throw new BusinessException(ErrorCode.MEDIA018);
		}
		media.markUsed();
		return media;
	}
}
