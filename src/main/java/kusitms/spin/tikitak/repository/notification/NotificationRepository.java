package kusitms.spin.tikitak.repository.notification;

import kusitms.spin.tikitak.domain.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long>, NotificationRepositoryCustom {

	Optional<Notification> findByIdAndMemberId(Long id, Long memberId);
}
