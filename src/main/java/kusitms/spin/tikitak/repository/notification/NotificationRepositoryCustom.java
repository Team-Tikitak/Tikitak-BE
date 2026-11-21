package kusitms.spin.tikitak.repository.notification;

import kusitms.spin.tikitak.domain.notification.entity.Notification;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 알림 목록·개수·일괄 읽음 동적 쿼리(QueryDSL). 구현은 {@link NotificationRepositoryCustomImpl}.
 * docs/decisions/2026-11-19-querydsl-feed-list.md
 */
public interface NotificationRepositoryCustom {

	/**
	 * 조건에 맞는 알림을 createdAt desc, id desc 순으로 최대 limit개 반환한다.
	 */
	List<Notification> findPage(NotificationListCondition condition, int limit);

	/**
	 * 범위에 맞는 알림 수.
	 */
	long countAll(NotificationScope scope);

	/**
	 * 범위에 맞는 안 읽은 알림 수.
	 */
	long countUnread(NotificationScope scope);

	/**
	 * 범위에 맞는 안 읽은 알림을 읽음으로 바꾸고 readAt을 채운다. 갱신 건수를 반환한다.
	 * 이 메서드는 영속성 컨텍스트를 직접 flush·clear하지 않는다. 그래서 같은 트랜잭션에서 이미 로드된 Notification은 갱신 전 상태로 남을 수 있다.
	 */
	long markAllAsRead(NotificationScope scope, LocalDateTime readAt);
}
