package kusitms.spin.tikitak.service.notification;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.SendResponse;
import kusitms.spin.tikitak.domain.feed.entity.Feed;
import kusitms.spin.tikitak.domain.feed.entity.FeedImage;
import kusitms.spin.tikitak.domain.notification.dto.NotificationPayload;
import kusitms.spin.tikitak.domain.notification.entity.Notification;
import kusitms.spin.tikitak.domain.notification.enums.NotificationType;
import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.repository.feed.FeedImageRepository;
import kusitms.spin.tikitak.repository.notification.NotificationRepository;
import kusitms.spin.tikitak.repository.team.TeamMemberRepository;
import kusitms.spin.tikitak.service.me.DefaultProfileImageResolver;
import kusitms.spin.tikitak.service.media.ImagePreset;
import kusitms.spin.tikitak.service.media.ImageUrlResolver;
import kusitms.spin.tikitak.service.notification.NotificationPersistService.DeviceTokenTarget;
import kusitms.spin.tikitak.service.notification.NotificationPersistService.SavedNotification;
import kusitms.spin.tikitak.service.notification.dto.NotificationResponseDTO;
import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static kusitms.spin.tikitak.support.fixture.MemberFixture.activeMember;
import static kusitms.spin.tikitak.support.fixture.TeamFixture.activeTeam;
import static kusitms.spin.tikitak.support.fixture.TeamMemberFixture.activeMemberWithoutProfileImg;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NotificationServiceTest extends UnitTest {

	@Mock
	private NotificationPersistService notificationPersistService;

	@Mock
	private NotificationRepository notificationRepository;

	@Mock
	private TeamMemberRepository teamMemberRepository;

	@Mock
	private FeedImageRepository feedImageRepository;

	@Mock
	private DefaultProfileImageResolver defaultProfileImageResolver;

	@Mock
	private ImageUrlResolver imageUrlResolver;

	@Mock
	private FirebaseMessaging firebaseMessaging;

	@Test
	@DisplayName("존재하지 않거나 탈퇴한 회원에게는 알림을 저장/전송하지 않는다")
	void doesNotSaveOrSendWhenMemberIsNotActive() {
		NotificationService notificationService = notificationService(Optional.of(firebaseMessaging));
		NotificationPayload payload = somePayload();
		when(notificationPersistService.saveForActiveMember(1L, payload, true)).thenReturn(Optional.empty());

		notificationService.send(1L, payload);

		verify(notificationPersistService).saveForActiveMember(1L, payload, true);
		verify(notificationPersistService, never()).deleteDeviceTokens(anyList());
		verifyNoInteractions(firebaseMessaging);
	}

	@Test
	@DisplayName("FCM이 설정되지 않은 경우에도 알림은 저장하되 전송하지 않는다")
	void doesNotSendWhenFirebaseMessagingIsNotConfigured() {
		NotificationService notificationService = notificationService(Optional.empty());
		NotificationPayload payload = somePayload();
		when(notificationPersistService.saveForActiveMember(1L, payload, false))
				.thenReturn(Optional.of(new SavedNotification(100L, List.of())));

		notificationService.send(1L, payload);

		verify(notificationPersistService).saveForActiveMember(1L, payload, false);
		verify(notificationPersistService, never()).deleteDeviceTokens(anyList());
	}

	@Test
	@DisplayName("등록된 디바이스 토큰이 없으면 FCM을 호출하지 않는다")
	void doesNotCallFcmWhenNoDeviceTokens() {
		NotificationService notificationService = notificationService(Optional.of(firebaseMessaging));
		NotificationPayload payload = somePayload();
		when(notificationPersistService.saveForActiveMember(1L, payload, true))
				.thenReturn(Optional.of(new SavedNotification(100L, List.of())));

		notificationService.send(1L, payload);

		verifyNoInteractions(firebaseMessaging);
		verify(notificationPersistService, never()).deleteDeviceTokens(anyList());
	}

	@Test
	@DisplayName("전송 결과가 UNREGISTERED 오류이면 해당 디바이스 토큰을 삭제한다")
	void deletesDeviceTokenWhenUnregistered() throws Exception {
		NotificationService notificationService = notificationService(Optional.of(firebaseMessaging));
		NotificationPayload payload = somePayload();
		givenSavedWithTokens(payload, new DeviceTokenTarget(1L, "token-1"));

		List<SendResponse> responses = List.of(failedResponse(MessagingErrorCode.UNREGISTERED));
		BatchResponse batchResponse = mock(BatchResponse.class);
		when(batchResponse.getResponses()).thenReturn(responses);
		when(firebaseMessaging.sendEach(anyList())).thenReturn(batchResponse);

		notificationService.send(1L, payload);

		verify(notificationPersistService).deleteDeviceTokens(List.of(1L));
	}

	@Test
	@DisplayName("전송에 성공하면 디바이스 토큰을 삭제하지 않는다")
	void doesNotDeleteDeviceTokenOnSuccess() throws Exception {
		NotificationService notificationService = notificationService(Optional.of(firebaseMessaging));
		NotificationPayload payload = somePayload();
		givenSavedWithTokens(payload, new DeviceTokenTarget(1L, "token-1"));

		List<SendResponse> responses = List.of(successResponse());
		BatchResponse batchResponse = mock(BatchResponse.class);
		when(batchResponse.getResponses()).thenReturn(responses);
		when(firebaseMessaging.sendEach(anyList())).thenReturn(batchResponse);

		notificationService.send(1L, payload);

		verify(notificationPersistService, never()).deleteDeviceTokens(anyList());
	}

	@Test
	@DisplayName("전송 결과가 UNREGISTERED 이외의 오류이면 디바이스 토큰을 삭제하지 않는다")
	void doesNotDeleteDeviceTokenOnOtherErrorCodes() throws Exception {
		NotificationService notificationService = notificationService(Optional.of(firebaseMessaging));
		NotificationPayload payload = somePayload();
		givenSavedWithTokens(payload, new DeviceTokenTarget(1L, "token-1"));

		List<SendResponse> responses = List.of(failedResponse(MessagingErrorCode.UNAVAILABLE));
		BatchResponse batchResponse = mock(BatchResponse.class);
		when(batchResponse.getResponses()).thenReturn(responses);
		when(firebaseMessaging.sendEach(anyList())).thenReturn(batchResponse);

		notificationService.send(1L, payload);

		verify(notificationPersistService, never()).deleteDeviceTokens(anyList());
	}

	@Test
	@DisplayName("등록된 모든 디바이스 토큰으로 전송하고 실패한 토큰만 삭제한다")
	void sendsToAllDeviceTokensAndDeletesOnlyFailedOnes() throws Exception {
		NotificationService notificationService = notificationService(Optional.of(firebaseMessaging));
		NotificationPayload payload = somePayload();
		givenSavedWithTokens(
				payload,
				new DeviceTokenTarget(1L, "token-valid"),
				new DeviceTokenTarget(2L, "token-invalid")
		);

		List<SendResponse> responses = List.of(successResponse(), failedResponse(MessagingErrorCode.UNREGISTERED));
		BatchResponse batchResponse = mock(BatchResponse.class);
		when(batchResponse.getResponses()).thenReturn(responses);
		when(firebaseMessaging.sendEach(anyList())).thenReturn(batchResponse);

		notificationService.send(1L, payload);

		verify(firebaseMessaging).sendEach(argThat(messages -> messages.size() == 2));
		verify(notificationPersistService).deleteDeviceTokens(List.of(2L));
	}

	@Test
	@DisplayName("FCM 전송 중 예외가 발생해도 호출자에게 전파하지 않는다")
	void doesNotPropagateExceptionWhenFcmCallFails() throws Exception {
		NotificationService notificationService = notificationService(Optional.of(firebaseMessaging));
		NotificationPayload payload = somePayload();
		givenSavedWithTokens(payload, new DeviceTokenTarget(1L, "token-1"));

		FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
		when(firebaseMessaging.sendEach(anyList())).thenThrow(exception);

		notificationService.send(1L, payload);

		verify(notificationPersistService, never()).deleteDeviceTokens(anyList());
	}

	@Test
	@DisplayName("FCM이 RuntimeException을 던지면 알림 저장(별도 트랜잭션)은 이미 끝난 뒤이고, 예외는 전파되며 토큰은 삭제하지 않는다")
	void notificationIsSavedBeforeFcmRuntimeExceptionPropagates() throws Exception {
		NotificationService notificationService = notificationService(Optional.of(firebaseMessaging));
		NotificationPayload payload = somePayload();
		givenSavedWithTokens(payload, new DeviceTokenTarget(1L, "token-1"));
		RuntimeException fcmFailure = new RuntimeException("fcm failed");
		when(firebaseMessaging.sendEach(anyList())).thenThrow(fcmFailure);

		assertThatThrownBy(() -> notificationService.send(1L, payload)).isSameAs(fcmFailure);

		InOrder inOrder = inOrder(notificationPersistService, firebaseMessaging);
		inOrder.verify(notificationPersistService).saveForActiveMember(1L, payload, true);
		inOrder.verify(firebaseMessaging).sendEach(anyList());
		verify(notificationPersistService, never()).deleteDeviceTokens(anyList());
	}

	@Test
	@DisplayName("커서 없이 알림 목록을 조회하면 최신순 첫 페이지를 반환한다")
	void listsFirstPageWhenCursorIsAbsent() {
		NotificationService notificationService = notificationService(Optional.empty());
		Notification notification = notification(1L, false);
		when(notificationRepository.findFirstPage(eq(1L), isNull(), any())).thenReturn(List.of(notification));
		when(notificationRepository.countByMemberIdAndTeamId(1L, null)).thenReturn(1L);

		NotificationResponseDTO.NotificationListResponseDTO response =
				notificationService.listNotifications(1L, null, null, null);

		assertThat(response.getItems()).hasSize(1);
		assertThat(response.getItems().get(0).getNotificationId()).isEqualTo(1L);
		assertThat(response.getPageInfo().isHasNext()).isFalse();
		assertThat(response.getPageInfo().getTotalCount()).isEqualTo(1L);
	}

	@Test
	@DisplayName("teamId를 전달하면 해당 팀 알림만 조회한다")
	void listsNotificationsFilteredByTeamId() {
		NotificationService notificationService = notificationService(Optional.empty());
		Notification notification = notification(1L, false);
		when(notificationRepository.findFirstPage(eq(1L), eq(10L), any())).thenReturn(List.of(notification));
		when(notificationRepository.countByMemberIdAndTeamId(1L, 10L)).thenReturn(1L);

		NotificationResponseDTO.NotificationListResponseDTO response =
				notificationService.listNotifications(1L, 10L, null, null);

		assertThat(response.getItems()).hasSize(1);
		assertThat(response.getPageInfo().getTotalCount()).isEqualTo(1L);
		verify(notificationRepository, never()).findFirstPage(eq(1L), isNull(), any());
	}

	@Test
	@DisplayName("알림 목록 조회 시 작성자 프로필과 피드 이미지를 함께 반환한다")
	void listNotificationsIncludesActorProfileAndFeedImages() {
		NotificationService notificationService = notificationService(Optional.empty());
		Notification notification = notification(1L, false);
		TeamMember actor = activeMemberWithoutProfileImg(30L, activeMember(3L), activeTeam(10L));
		FeedImage firstImage = FeedImage.builder()
				.id(40L)
				.feed(Feed.builder().id(20L).build())
				.imgUrl("https://example.com/feed.png")
				.orderIndex(0)
				.build();
		when(notificationRepository.findFirstPage(eq(1L), isNull(), any())).thenReturn(List.of(notification));
		when(notificationRepository.countByMemberIdAndTeamId(1L, null)).thenReturn(1L);
		when(teamMemberRepository.findByIdsWithMember(List.of(30L))).thenReturn(List.of(actor));
		when(feedImageRepository.findFirstActiveByFeedIds(List.of(20L))).thenReturn(List.of(firstImage));
		when(defaultProfileImageResolver.resolveForTeamMember(actor)).thenReturn("https://example.com/profile.png");
		when(imageUrlResolver.resolve("https://example.com/feed.png", ImagePreset.FEED_THUMB))
				.thenReturn("https://example.com/feed.png?preset=feed_thumb");
		when(imageUrlResolver.resolve("https://example.com/feed.png", ImagePreset.FEED_HERO_PREVIEW))
				.thenReturn("https://example.com/feed.png?preset=feed_hero_preview");

		NotificationResponseDTO.NotificationListResponseDTO response =
				notificationService.listNotifications(1L, null, null, null);

		NotificationResponseDTO.NotificationListItemDTO item = response.getItems().get(0);
		assertThat(item.getProfileImageUrl()).isEqualTo("https://example.com/profile.png");
		assertThat(item.getThumbnailImageUrl()).isEqualTo("https://example.com/feed.png?preset=feed_thumb");
		assertThat(item.getHeroPreviewUrl()).isEqualTo("https://example.com/feed.png?preset=feed_hero_preview");
	}

	@Test
	@DisplayName("안읽은 알림 개수를 조회한다")
	void returnsUnreadCount() {
		NotificationService notificationService = notificationService(Optional.empty());
		when(notificationRepository.countByMemberIdAndTeamIdAndIsReadFalse(1L, null)).thenReturn(3L);

		NotificationResponseDTO.UnreadCountResponseDTO response = notificationService.getUnreadCount(1L, null);

		assertThat(response.getUnreadCount()).isEqualTo(3L);
	}

	@Test
	@DisplayName("teamId를 전달하면 해당 팀의 안읽은 알림 개수만 조회한다")
	void returnsUnreadCountFilteredByTeamId() {
		NotificationService notificationService = notificationService(Optional.empty());
		when(notificationRepository.countByMemberIdAndTeamIdAndIsReadFalse(1L, 10L)).thenReturn(2L);

		NotificationResponseDTO.UnreadCountResponseDTO response = notificationService.getUnreadCount(1L, 10L);

		assertThat(response.getUnreadCount()).isEqualTo(2L);
	}

	@Test
	@DisplayName("본인 소유의 알림을 읽음 처리한다")
	void marksOwnNotificationAsRead() {
		NotificationService notificationService = notificationService(Optional.empty());
		Notification notification = notification(1L, false);
		when(notificationRepository.findByIdAndMemberId(1L, 1L)).thenReturn(Optional.of(notification));

		notificationService.markAsRead(1L, 1L);

		assertThat(notification.isRead()).isTrue();
	}

	@Test
	@DisplayName("존재하지 않거나 본인 소유가 아닌 알림을 읽음 처리하면 예외가 발생한다")
	void throwsWhenMarkingNotOwnedNotificationAsRead() {
		NotificationService notificationService = notificationService(Optional.empty());
		when(notificationRepository.findByIdAndMemberId(1L, 1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> notificationService.markAsRead(1L, 1L))
				.isInstanceOf(BusinessException.class);
	}

	@Test
	@DisplayName("전체 알림을 읽음 처리하면 벌크 업데이트를 호출한다")
	void marksAllNotificationsAsRead() {
		NotificationService notificationService = notificationService(Optional.empty());

		notificationService.markAllAsRead(1L, null);

		verify(notificationRepository).updateAllAsReadByMemberId(eq(1L), isNull(), any(LocalDateTime.class));
	}

	@Test
	@DisplayName("teamId를 전달하면 해당 팀 알림만 읽음 처리한다")
	void marksTeamNotificationsAsRead() {
		NotificationService notificationService = notificationService(Optional.empty());

		notificationService.markAllAsRead(1L, 10L);

		verify(notificationRepository).updateAllAsReadByMemberId(eq(1L), eq(10L), any(LocalDateTime.class));
	}

	private NotificationService notificationService(Optional<FirebaseMessaging> firebaseMessaging) {
		return new NotificationService(
				notificationPersistService,
				notificationRepository,
				teamMemberRepository,
				feedImageRepository,
				defaultProfileImageResolver,
				imageUrlResolver,
				firebaseMessaging
		);
	}

	private Notification notification(Long id, boolean isRead) {
		return Notification.builder()
				.id(id)
				.member(activeMember(1L))
				.type(NotificationType.FEED_COMMENT)
				.title("title")
				.body("body")
				.teamId(10L)
				.feedId(20L)
				.actorTeamMemberId(30L)
				.isRead(isRead)
				.createdAt(LocalDateTime.now())
				.build();
	}

	private void givenSavedWithTokens(NotificationPayload payload, DeviceTokenTarget... deviceTokens) {
		when(notificationPersistService.saveForActiveMember(1L, payload, true))
				.thenReturn(Optional.of(new SavedNotification(100L, List.of(deviceTokens))));
	}

	private SendResponse successResponse() {
		SendResponse response = mock(SendResponse.class);
		when(response.isSuccessful()).thenReturn(true);
		return response;
	}

	private SendResponse failedResponse(MessagingErrorCode errorCode) {
		FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
		when(exception.getMessagingErrorCode()).thenReturn(errorCode);
		SendResponse response = mock(SendResponse.class);
		when(response.isSuccessful()).thenReturn(false);
		when(response.getException()).thenReturn(exception);
		return response;
	}

	private NotificationPayload somePayload() {
		return NotificationPayload.builder()
				.type(NotificationType.FEED_COMMENT)
				.title("title")
				.body("body")
				.build();
	}
}
