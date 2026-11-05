package kusitms.spin.tikitak.service.notification;

import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.member.enums.MemberStatus;
import kusitms.spin.tikitak.domain.notification.dto.NotificationPayload;
import kusitms.spin.tikitak.domain.notification.entity.Notification;
import kusitms.spin.tikitak.repository.member.MemberRepository;
import kusitms.spin.tikitak.repository.notification.MemberDeviceTokenRepository;
import kusitms.spin.tikitak.repository.notification.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 알림 전송 전후의 DB 작업(회원 확인, 알림 저장, 디바이스 토큰 조회/삭제)을 각각 짧은 트랜잭션으로 실행한다.
 * FCM 호출은 {@link NotificationService}에서 트랜잭션 밖에서 실행한다.
 * 반환값은 엔티티가 아니라 전송에 필요한 값(ID, 토큰 문자열)만 담는다(트랜잭션 밖 LAZY 접근 방지).
 */
@Service
@RequiredArgsConstructor
public class NotificationPersistService {

	private final MemberRepository memberRepository;
	private final NotificationRepository notificationRepository;
	private final MemberDeviceTokenRepository deviceTokenRepository;

	/**
	 * 활성 회원이면 알림을 저장하고, {@code loadDeviceTokens}가 true면 회원의 디바이스 토큰도 함께 조회한다.
	 *
	 * @return 존재하지 않거나 탈퇴한 회원이면 빈 값
	 */
	@Transactional
	public Optional<SavedNotification> saveForActiveMember(Long memberId, NotificationPayload payload, boolean loadDeviceTokens) {
		Optional<Member> activeMember = memberRepository.findById(memberId)
				.filter(member -> member.getStatus() == MemberStatus.ACTIVE);
		if (activeMember.isEmpty()) {
			return Optional.empty();
		}
		Notification savedNotification = notificationRepository.save(Notification.create(activeMember.get(), payload));
		Long notificationId = savedNotification == null ? null : savedNotification.getId();

		List<DeviceTokenTarget> deviceTokens = loadDeviceTokens
				? deviceTokenRepository.findAllByMemberId(memberId).stream()
						.map(deviceToken -> new DeviceTokenTarget(deviceToken.getId(), deviceToken.getFcmToken()))
						.toList()
				: List.of();
		return Optional.of(new SavedNotification(notificationId, deviceTokens));
	}

	@Transactional
	public void deleteDeviceTokens(List<Long> deviceTokenIds) {
		deviceTokenRepository.deleteAllById(deviceTokenIds);
	}

	public record SavedNotification(Long notificationId, List<DeviceTokenTarget> deviceTokens) {
	}

	public record DeviceTokenTarget(Long id, String fcmToken) {
	}
}
