package kusitms.spin.tikitak.repository.notification;

import java.util.Objects;

/**
 * 알림 범위 조건. count와 일괄 읽음 처리가 받으며, 커서를 담지 않는다.
 *
 * @param memberId 알림을 받은 회원 ID (필수)
 * @param teamId 팀 ID. null이면 그 회원의 모든 알림(teamId가 null인 알림 포함)
 */
public record NotificationScope(Long memberId, Long teamId) {

	public NotificationScope {
		Objects.requireNonNull(memberId, "memberId must not be null");
	}
}
