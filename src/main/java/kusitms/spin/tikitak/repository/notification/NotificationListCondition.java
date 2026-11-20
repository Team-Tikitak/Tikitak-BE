package kusitms.spin.tikitak.repository.notification;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 알림 목록 조회 조건. {@link NotificationRepositoryCustom#findPage}가 받는다.
 * 범위 술어는 {@link #scope()}로 count·일괄 읽음 처리와 같은 조건을 쓴다.
 *
 * @param memberId 알림을 받은 회원 ID (필수)
 * @param teamId 팀 ID. null이면 그 회원의 모든 알림
 * @param cursorCreatedAt 커서 알림의 createdAt. cursorId와 둘 다 null이거나 둘 다 non-null
 * @param cursorId 커서 알림의 ID
 */
public record NotificationListCondition(
		Long memberId,
		Long teamId,
		LocalDateTime cursorCreatedAt,
		Long cursorId
) {

	public NotificationListCondition {
		Objects.requireNonNull(memberId, "memberId must not be null");
		if ((cursorCreatedAt == null) != (cursorId == null)) {
			throw new IllegalArgumentException("cursorCreatedAt and cursorId must be both null or both non-null");
		}
	}

	public boolean hasCursor() {
		return cursorCreatedAt != null;
	}

	public NotificationScope scope() {
		return new NotificationScope(memberId, teamId);
	}
}
