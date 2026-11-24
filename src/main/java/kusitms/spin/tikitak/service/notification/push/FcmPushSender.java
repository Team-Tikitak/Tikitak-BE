package kusitms.spin.tikitak.service.notification.push;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Firebase Admin SDK로 푸시를 보내는 {@link PushSender}. FCM이 설정되지 않으면 {@link FirebaseMessaging} 빈이 없고,
 * {@link #isAvailable()}이 false를 돌려준다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FcmPushSender implements PushSender {

	private final Optional<FirebaseMessaging> firebaseMessaging;

	@Override
	public boolean isAvailable() {
		return firebaseMessaging.isPresent();
	}

	@Override
	public List<PushResult> send(List<String> tokens, PushMessage message) {
		if (tokens.isEmpty()) {
			return List.of();
		}
		if (!isAvailable()) {
			return Collections.nCopies(tokens.size(), PushResult.failed(null, null));
		}

		List<Message> messages = tokens.stream()
				.map(token -> toMessage(token, message))
				.toList();

		try {
			BatchResponse batchResponse = firebaseMessaging.get().sendEach(messages);
			return batchResponse.getResponses().stream()
					.map(FcmPushSender::toResult)
					.toList();
		} catch (FirebaseMessagingException e) {
			log.error("FCM 일괄 전송 결과를 받지 못했습니다. tokenCount={}", tokens.size(), e);
			return Collections.nCopies(tokens.size(), PushResult.failed(e.getErrorCode().name(), e.getMessage()));
		}
	}

	private static Message toMessage(String token, PushMessage message) {
		return Message.builder()
				.setToken(token)
				.setNotification(Notification.builder()
						.setTitle(message.title())
						.setBody(message.body())
						.build())
				.putAllData(message.data())
				.build();
	}

	private static PushResult toResult(SendResponse response) {
		if (response.isSuccessful()) {
			return PushResult.success();
		}
		FirebaseMessagingException exception = response.getException();
		MessagingErrorCode errorCode = exception.getMessagingErrorCode();
		String errorCodeName = errorCode == null ? null : errorCode.name();
		if (errorCode == MessagingErrorCode.UNREGISTERED) {
			return PushResult.invalidToken(errorCodeName, exception.getMessage());
		}
		return PushResult.failed(errorCodeName, exception.getMessage());
	}
}
