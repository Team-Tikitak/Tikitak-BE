package kusitms.spin.tikitak.repository.notification;

import kusitms.spin.tikitak.domain.notification.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	@Query("""
			select n
			from Notification n
			where n.member.id = :memberId
			order by n.createdAt desc, n.id desc
			""")
	List<Notification> findFirstPage(@Param("memberId") Long memberId, Pageable pageable);

	@Query("""
			select n
			from Notification n
			where n.member.id = :memberId
				and (
					n.createdAt < :cursorCreatedAt
					or (n.createdAt = :cursorCreatedAt and n.id < :cursorId)
				)
			order by n.createdAt desc, n.id desc
			""")
	List<Notification> findCursorPage(
			@Param("memberId") Long memberId,
			@Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
			@Param("cursorId") Long cursorId,
			Pageable pageable
	);

	long countByMemberId(Long memberId);

	long countByMemberIdAndIsReadFalse(Long memberId);

	Optional<Notification> findByIdAndMemberId(Long id, Long memberId);

	@Modifying
	@Query("""
			update Notification n
			set n.isRead = true, n.readAt = :readAt
			where n.member.id = :memberId
				and n.isRead = false
			""")
	void updateAllAsReadByMemberId(@Param("memberId") Long memberId, @Param("readAt") LocalDateTime readAt);
}
