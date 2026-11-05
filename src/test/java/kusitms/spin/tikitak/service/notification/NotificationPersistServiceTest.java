package kusitms.spin.tikitak.service.notification;

import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.notification.dto.NotificationPayload;
import kusitms.spin.tikitak.domain.notification.entity.MemberDeviceToken;
import kusitms.spin.tikitak.domain.notification.entity.Notification;
import kusitms.spin.tikitak.domain.notification.enums.DevicePlatform;
import kusitms.spin.tikitak.domain.notification.enums.NotificationType;
import kusitms.spin.tikitak.repository.member.MemberRepository;
import kusitms.spin.tikitak.repository.notification.MemberDeviceTokenRepository;
import kusitms.spin.tikitak.repository.notification.NotificationRepository;
import kusitms.spin.tikitak.service.notification.NotificationPersistService.DeviceTokenTarget;
import kusitms.spin.tikitak.service.notification.NotificationPersistService.SavedNotification;
import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static kusitms.spin.tikitak.support.fixture.MemberFixture.activeMember;
import static kusitms.spin.tikitak.support.fixture.MemberFixture.inactiveMember;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NotificationPersistServiceTest extends UnitTest {

	@Mock
	private MemberRepository memberRepository;

	@Mock
	private NotificationRepository notificationRepository;

	@Mock
	private MemberDeviceTokenRepository deviceTokenRepository;

	@InjectMocks
	private NotificationPersistService notificationPersistService;

	@Test
	@DisplayName("탈퇴한 회원이면 알림을 저장하지 않고 토큰도 조회하지 않는다")
	void returnsEmptyWhenMemberIsInactive() {
		when(memberRepository.findById(1L)).thenReturn(Optional.of(inactiveMember(1L)));

		Optional<SavedNotification> result = notificationPersistService.saveForActiveMember(1L, somePayload(), true);

		assertThat(result).isEmpty();
		verify(notificationRepository, never()).save(any());
		verifyNoInteractions(deviceTokenRepository);
	}

	@Test
	@DisplayName("존재하지 않는 회원이면 알림을 저장하지 않고 토큰도 조회하지 않는다")
	void returnsEmptyWhenMemberDoesNotExist() {
		when(memberRepository.findById(1L)).thenReturn(Optional.empty());

		Optional<SavedNotification> result = notificationPersistService.saveForActiveMember(1L, somePayload(), true);

		assertThat(result).isEmpty();
		verify(notificationRepository, never()).save(any());
		verifyNoInteractions(deviceTokenRepository);
	}

	@Test
	@DisplayName("토큰 조회를 요청하지 않으면(FCM 미설정) 알림만 저장하고 토큰은 조회하지 않는다")
	void savesNotificationWithoutLoadingTokens() {
		Member member = activeMember(1L);
		when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
		when(notificationRepository.save(any(Notification.class))).thenReturn(notification(100L, member));

		Optional<SavedNotification> result = notificationPersistService.saveForActiveMember(1L, somePayload(), false);

		assertThat(result).contains(new SavedNotification(100L, List.of()));
		verify(notificationRepository).save(argThat(notification ->
				notification.getMember() == member && notification.getType() == NotificationType.FEED_COMMENT));
		verifyNoInteractions(deviceTokenRepository);
	}

	@Test
	@DisplayName("활성 회원이면 알림을 저장하고 토큰의 ID와 값만 담아 반환한다")
	void savesNotificationAndReturnsTokenValues() {
		Member member = activeMember(1L);
		when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
		when(notificationRepository.save(any(Notification.class))).thenReturn(notification(100L, member));
		when(deviceTokenRepository.findAllByMemberId(1L)).thenReturn(List.of(
				deviceToken(10L, member, "token-a"),
				deviceToken(11L, member, "token-b")
		));

		Optional<SavedNotification> result = notificationPersistService.saveForActiveMember(1L, somePayload(), true);

		assertThat(result).contains(new SavedNotification(100L, List.of(
				new DeviceTokenTarget(10L, "token-a"),
				new DeviceTokenTarget(11L, "token-b")
		)));
	}

	@Test
	@DisplayName("디바이스 토큰을 ID로 삭제한다")
	void deletesDeviceTokensById() {
		notificationPersistService.deleteDeviceTokens(List.of(10L, 11L));

		verify(deviceTokenRepository).deleteAllById(List.of(10L, 11L));
	}

	private Notification notification(Long id, Member member) {
		return Notification.builder()
				.id(id)
				.member(member)
				.type(NotificationType.FEED_COMMENT)
				.title("title")
				.body("body")
				.isRead(false)
				.createdAt(LocalDateTime.now())
				.build();
	}

	private MemberDeviceToken deviceToken(Long id, Member member, String fcmToken) {
		LocalDateTime now = LocalDateTime.now();
		return MemberDeviceToken.builder()
				.id(id)
				.member(member)
				.fcmToken(fcmToken)
				.platform(DevicePlatform.ANDROID)
				.createdAt(now)
				.updatedAt(now)
				.build();
	}

	private NotificationPayload somePayload() {
		return NotificationPayload.builder()
				.type(NotificationType.FEED_COMMENT)
				.title("title")
				.body("body")
				.build();
	}
}
