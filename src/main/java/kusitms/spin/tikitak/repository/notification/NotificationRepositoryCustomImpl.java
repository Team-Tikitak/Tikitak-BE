package kusitms.spin.tikitak.repository.notification;

import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import kusitms.spin.tikitak.domain.notification.entity.Notification;
import kusitms.spin.tikitak.domain.notification.entity.QNotification;

import java.time.LocalDateTime;
import java.util.List;

/**
 * {@link NotificationRepositoryCustom}의 QueryDSL 구현. 공용 {@link JPAQueryFactory} Bean을 주입받는다.
 * 목록, count, 일괄 읽음은 {@link #scope(NotificationScope)}의 where 술어를 공유한다.
 */
public class NotificationRepositoryCustomImpl implements NotificationRepositoryCustom {

	private static final QNotification notification = QNotification.notification;

	private final JPAQueryFactory queryFactory;

	public NotificationRepositoryCustomImpl(JPAQueryFactory queryFactory) {
		this.queryFactory = queryFactory;
	}

	@Override
	public List<Notification> findPage(NotificationListCondition condition, int limit) {
		return queryFactory
				.selectFrom(notification)
				.where(scope(condition.scope()))
				.where(cursor(condition))
				.orderBy(notification.createdAt.desc(), notification.id.desc())
				.limit(limit)
				.fetch();
	}

	@Override
	public long countAll(NotificationScope scope) {
		Long count = queryFactory
				.select(notification.count())
				.from(notification)
				.where(scope(scope))
				.fetchOne();
		return count == null ? 0L : count;
	}

	@Override
	public long countUnread(NotificationScope scope) {
		Long count = queryFactory
				.select(notification.count())
				.from(notification)
				.where(scope(scope))
				.where(notification.isRead.isFalse())
				.fetchOne();
		return count == null ? 0L : count;
	}

	@Override
	public long markAllAsRead(NotificationScope scope, LocalDateTime readAt) {
		return queryFactory
				.update(notification)
				.set(notification.isRead, true)
				.set(notification.readAt, readAt)
				.where(scope(scope))
				.where(notification.isRead.isFalse())
				.execute();
	}

	/**
	 * 범위 술어. memberId는 항상, teamId는 non-null일 때만 붙인다. null 항목은 QueryDSL이 무시한다.
	 */
	private Predicate[] scope(NotificationScope scope) {
		return new Predicate[] {
				notification.member.id.eq(scope.memberId()),
				scope.teamId() == null ? null : notification.teamId.eq(scope.teamId())
		};
	}

	private BooleanExpression cursor(NotificationListCondition condition) {
		if (!condition.hasCursor()) {
			return null;
		}
		LocalDateTime cursorCreatedAt = condition.cursorCreatedAt();
		return notification.createdAt.lt(cursorCreatedAt)
				.or(notification.createdAt.eq(cursorCreatedAt).and(notification.id.lt(condition.cursorId())));
	}
}
