package kusitms.spin.tikitak.service.notification;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.SendResponse;
import kusitms.spin.tikitak.domain.notification.entity.MemberDeviceToken;
import kusitms.spin.tikitak.domain.notification.enums.DevicePlatform;
import kusitms.spin.tikitak.domain.notification.enums.NotificationType;
import kusitms.spin.tikitak.repository.notification.MemberDeviceTokenRepository;
import kusitms.spin.tikitak.service.notification.dto.NotificationPayload;
import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static kusitms.spin.tikitak.support.fixture.MemberFixture.activeMember;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NotificationServiceTest extends UnitTest {

	@Mock
	private MemberDeviceTokenRepository deviceTokenRepository;

	@Mock
	private FirebaseMessaging firebaseMessaging;

	@Test
	@DisplayName("FCM이 설정되지 않은 경우 알림을 전송하지 않는다")
	void doesNotSendWhenFirebaseMessagingIsNotConfigured() {
		NotificationService notificationService = new NotificationService(deviceTokenRepository, Optional.empty());

		notificationService.send(1L, somePayload());

		verifyNoInteractions(deviceTokenRepository);
	}

	@Test
	@DisplayName("등록된 디바이스 토큰이 없으면 FCM을 호출하지 않는다")
	void doesNotCallFcmWhenNoDeviceTokens() {
		NotificationService notificationService =
				new NotificationService(deviceTokenRepository, Optional.of(firebaseMessaging));
		when(deviceTokenRepository.findAllByMemberId(1L)).thenReturn(List.of());

		notificationService.send(1L, somePayload());

		verifyNoInteractions(firebaseMessaging);
	}

	@Test
	@DisplayName("전송 결과가 UNREGISTERED 오류이면 해당 디바이스 토큰을 삭제한다")
	void deletesDeviceTokenWhenUnregistered() throws Exception {
		NotificationService notificationService =
				new NotificationService(deviceTokenRepository, Optional.of(firebaseMessaging));
		MemberDeviceToken deviceToken = deviceToken("token-1");
		when(deviceTokenRepository.findAllByMemberId(1L)).thenReturn(List.of(deviceToken));

		FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
		when(exception.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
		SendResponse failedResponse = mock(SendResponse.class);
		when(failedResponse.isSuccessful()).thenReturn(false);
		when(failedResponse.getException()).thenReturn(exception);

		BatchResponse batchResponse = mock(BatchResponse.class);
		when(batchResponse.getResponses()).thenReturn(List.of(failedResponse));
		when(firebaseMessaging.sendEach(anyList())).thenReturn(batchResponse);

		notificationService.send(1L, somePayload());

		verify(deviceTokenRepository).delete(deviceToken);
	}

	@Test
	@DisplayName("전송에 성공하면 디바이스 토큰을 삭제하지 않는다")
	void doesNotDeleteDeviceTokenOnSuccess() throws Exception {
		NotificationService notificationService =
				new NotificationService(deviceTokenRepository, Optional.of(firebaseMessaging));
		MemberDeviceToken deviceToken = deviceToken("token-1");
		when(deviceTokenRepository.findAllByMemberId(1L)).thenReturn(List.of(deviceToken));

		SendResponse successResponse = mock(SendResponse.class);
		when(successResponse.isSuccessful()).thenReturn(true);

		BatchResponse batchResponse = mock(BatchResponse.class);
		when(batchResponse.getResponses()).thenReturn(List.of(successResponse));
		when(firebaseMessaging.sendEach(anyList())).thenReturn(batchResponse);

		notificationService.send(1L, somePayload());

		verify(deviceTokenRepository, never()).delete(any());
	}

	private MemberDeviceToken deviceToken(String fcmToken) {
		LocalDateTime now = LocalDateTime.now();
		return MemberDeviceToken.builder()
				.id(1L)
				.member(activeMember(1L))
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
