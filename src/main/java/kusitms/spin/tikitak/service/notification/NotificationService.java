package kusitms.spin.tikitak.service.notification;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import kusitms.spin.tikitak.domain.notification.entity.MemberDeviceToken;
import kusitms.spin.tikitak.repository.notification.MemberDeviceTokenRepository;
import kusitms.spin.tikitak.service.notification.dto.NotificationPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

	private final MemberDeviceTokenRepository deviceTokenRepository;
	private final Optional<FirebaseMessaging> firebaseMessaging;

	public void send(Long memberId, NotificationPayload payload) {
		if (firebaseMessaging.isEmpty()) {
			log.warn("FCM이 설정되지 않아 알림을 전송하지 않습니다. memberId={}, type={}", memberId, payload.getType());
			return;
		}

		List<MemberDeviceToken> deviceTokens = deviceTokenRepository.findAllByMemberId(memberId);
		if (deviceTokens.isEmpty()) {
			return;
		}

		List<Message> messages = deviceTokens.stream()
				.map(deviceToken -> toMessage(deviceToken, payload))
				.toList();

		try {
			BatchResponse batchResponse = firebaseMessaging.get().sendEach(messages);
			handleResponses(deviceTokens, batchResponse.getResponses());
		} catch (FirebaseMessagingException e) {
			log.error("FCM 알림 전송에 실패했습니다. memberId={}, type={}", memberId, payload.getType(), e);
		}
	}

	private Message toMessage(MemberDeviceToken deviceToken, NotificationPayload payload) {
		return Message.builder()
				.setToken(deviceToken.getFcmToken())
				.setNotification(Notification.builder()
						.setTitle(payload.getTitle())
						.setBody(payload.getBody())
						.build())
				.putAllData(payload.getData())
				.putData("type", payload.getType().name())
				.build();
	}

	private void handleResponses(List<MemberDeviceToken> deviceTokens, List<SendResponse> responses) {
		for (int i = 0; i < responses.size(); i++) {
			SendResponse response = responses.get(i);
			if (!response.isSuccessful()) {
				handleFailure(deviceTokens.get(i), response.getException());
			}
		}
	}

	private void handleFailure(MemberDeviceToken deviceToken, FirebaseMessagingException exception) {
		MessagingErrorCode errorCode = exception.getMessagingErrorCode();
		if (errorCode == MessagingErrorCode.UNREGISTERED) {
			log.info("유효하지 않은 디바이스 토큰을 삭제합니다. deviceTokenId={}, errorCode={}", deviceToken.getId(), errorCode);
			deviceTokenRepository.delete(deviceToken);
			return;
		}
		log.warn("FCM 알림 전송에 실패했습니다. deviceTokenId={}, errorCode={}", deviceToken.getId(), errorCode, exception);
	}
}
