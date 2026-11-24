package kusitms.spin.tikitak.service.notification.push;

import com.google.firebase.ErrorCode;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FcmPushSenderTest extends UnitTest {

	private static final PushMessage MESSAGE = new PushMessage(
			"title",
			"body",
			Map.of("type", "FEED_COMMENT", "notificationId", "100")
	);

	@Mock
	private FirebaseMessaging firebaseMessaging;

	@Test
	@DisplayName("FirebaseMessaging 빈이 있으면 사용할 수 있고, 없으면 사용할 수 없다")
	void isAvailableFollowsFirebaseMessagingPresence() {
		assertThat(new FcmPushSender(Optional.of(firebaseMessaging)).isAvailable()).isTrue();
		assertThat(new FcmPushSender(Optional.empty()).isAvailable()).isFalse();
	}

	@Test
	@DisplayName("성공, UNREGISTERED, 그 밖의 오류 코드 응답을 입력 순서대로 SUCCESS, INVALID_TOKEN, FAILED로 바꾼다")
	void mapsResponsesToResultsInOrder() throws Exception {
		FcmPushSender sender = new FcmPushSender(Optional.of(firebaseMessaging));
		givenResponses(
				successResponse(),
				failedResponse(MessagingErrorCode.UNREGISTERED, "unregistered"),
				failedResponse(MessagingErrorCode.UNAVAILABLE, "unavailable"),
				failedResponse(null, "unknown")
		);

		List<PushResult> results = sender.send(List.of("token-1", "token-2", "token-3", "token-4"), MESSAGE);

		assertThat(results).containsExactly(
				new PushResult(PushStatus.SUCCESS, null, null),
				new PushResult(PushStatus.INVALID_TOKEN, "UNREGISTERED", "unregistered"),
				new PushResult(PushStatus.FAILED, "UNAVAILABLE", "unavailable"),
				new PushResult(PushStatus.FAILED, null, "unknown")
		);
	}

	@Test
	@DisplayName("토큰마다 token, notification(title, body), data를 담은 Message를 만들어 sendEach를 한 번 부른다")
	@SuppressWarnings("unchecked")
	void sendsOneMessagePerTokenWithSameContent() throws Exception {
		FcmPushSender sender = new FcmPushSender(Optional.of(firebaseMessaging));
		givenResponses(successResponse(), successResponse());

		sender.send(List.of("token-1", "token-2"), MESSAGE);

		ArgumentCaptor<List<Message>> captor = ArgumentCaptor.forClass(List.class);
		verify(firebaseMessaging).sendEach(captor.capture());
		List<Message> messages = captor.getValue();
		assertThat(messages).hasSize(2);
		// Message/Notification의 getter는 package-private이라, 리터럴 값으로 만든 기대 Message와 필드 단위(reflection)로 비교한다.
		// 기대 객체에 넣지 않은 필드(android, apns, webpush, topic 등)는 null이어야 같다고 본다.
		assertThat(messages.get(0)).usingRecursiveComparison().isEqualTo(expectedMessage("token-1"));
		assertThat(messages.get(1)).usingRecursiveComparison().isEqualTo(expectedMessage("token-2"));
	}

	@Test
	@DisplayName("sendEach가 FirebaseMessagingException을 던지면 예외를 밖으로 내지 않고 모든 토큰을 FAILED로 돌려준다")
	void returnsAllFailedWhenSendEachThrowsFirebaseMessagingException() throws Exception {
		FcmPushSender sender = new FcmPushSender(Optional.of(firebaseMessaging));
		FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
		when(exception.getErrorCode()).thenReturn(ErrorCode.CANCELLED);
		when(exception.getMessage()).thenReturn("interrupted");
		when(firebaseMessaging.sendEach(anyList())).thenThrow(exception);

		List<PushResult> results = sender.send(List.of("token-1", "token-2"), MESSAGE);

		assertThat(results).containsExactly(
				new PushResult(PushStatus.FAILED, "CANCELLED", "interrupted"),
				new PushResult(PushStatus.FAILED, "CANCELLED", "interrupted")
		);
	}

	@Test
	@DisplayName("sendEach가 RuntimeException을 던지면 그대로 밖으로 나간다")
	void propagatesRuntimeException() throws Exception {
		FcmPushSender sender = new FcmPushSender(Optional.of(firebaseMessaging));
		RuntimeException failure = new RuntimeException("fcm failed");
		when(firebaseMessaging.sendEach(anyList())).thenThrow(failure);

		assertThatThrownBy(() -> sender.send(List.of("token-1"), MESSAGE)).isSameAs(failure);
	}

	@Test
	@DisplayName("토큰이 없으면 sendEach를 부르지 않고 빈 결과를 돌려준다")
	void doesNotCallSdkWhenTokensAreEmpty() {
		FcmPushSender sender = new FcmPushSender(Optional.of(firebaseMessaging));

		List<PushResult> results = sender.send(List.of(), MESSAGE);

		assertThat(results).isEmpty();
		verifyNoInteractions(firebaseMessaging);
	}

	@Test
	@DisplayName("FCM이 설정되지 않았는데 send가 불리면 SDK 없이 모든 토큰을 FAILED로 돌려준다")
	void returnsAllFailedWhenNotAvailable() {
		FcmPushSender sender = new FcmPushSender(Optional.empty());

		List<PushResult> results = sender.send(List.of("token-1", "token-2"), MESSAGE);

		assertThat(results).containsExactly(
				new PushResult(PushStatus.FAILED, null, null),
				new PushResult(PushStatus.FAILED, null, null)
		);
		verifyNoInteractions(firebaseMessaging);
	}

	private void givenResponses(SendResponse... responses) throws FirebaseMessagingException {
		BatchResponse batchResponse = mock(BatchResponse.class);
		when(batchResponse.getResponses()).thenReturn(List.of(responses));
		when(firebaseMessaging.sendEach(anyList())).thenReturn(batchResponse);
	}

	private static Message expectedMessage(String token) {
		return Message.builder()
				.setToken(token)
				.setNotification(Notification.builder()
						.setTitle("title")
						.setBody("body")
						.build())
				.putAllData(Map.of("type", "FEED_COMMENT", "notificationId", "100"))
				.build();
	}

	private static SendResponse successResponse() {
		SendResponse response = mock(SendResponse.class);
		when(response.isSuccessful()).thenReturn(true);
		return response;
	}

	private static SendResponse failedResponse(MessagingErrorCode errorCode, String message) {
		FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
		when(exception.getMessagingErrorCode()).thenReturn(errorCode);
		when(exception.getMessage()).thenReturn(message);
		SendResponse response = mock(SendResponse.class);
		when(response.isSuccessful()).thenReturn(false);
		when(response.getException()).thenReturn(exception);
		return response;
	}
}
