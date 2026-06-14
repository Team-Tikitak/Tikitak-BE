package kusitms.spin.tikitak.repository.notification;

import kusitms.spin.tikitak.domain.notification.entity.MemberDeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberDeviceTokenRepository extends JpaRepository<MemberDeviceToken, Long> {

	Optional<MemberDeviceToken> findByFcmToken(String fcmToken);

	List<MemberDeviceToken> findAllByMemberId(Long memberId);

	void deleteAllByMemberId(Long memberId);

	void deleteByFcmTokenAndMemberId(String fcmToken, Long memberId);
}
