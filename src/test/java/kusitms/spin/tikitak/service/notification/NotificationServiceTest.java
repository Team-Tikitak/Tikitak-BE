package kusitms.spin.tikitak.service.notification;

import kusitms.spin.tikitak.domain.feed.entity.Feed;
import kusitms.spin.tikitak.domain.feed.entity.FeedImage;
import kusitms.spin.tikitak.domain.notification.dto.NotificationPayload;
import kusitms.spin.tikitak.domain.notification.entity.Notification;
import kusitms.spin.tikitak.domain.notification.enums.NotificationType;
import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.repository.feed.FeedImageRepository;
import kusitms.spin.tikitak.repository.notification.NotificationListCondition;
import kusitms.spin.tikitak.repository.notification.NotificationRepository;
import kusitms.spin.tikitak.repository.notification.NotificationScope;
import kusitms.spin.tikitak.repository.team.TeamMemberRepository;
import kusitms.spin.tikitak.service.me.DefaultProfileImageResolver;
import kusitms.spin.tikitak.service.media.ImagePreset;
import kusitms.spin.tikitak.service.media.ImageUrlResolver;
import kusitms.spin.tikitak.service.notification.NotificationPersistService.DeviceTokenTarget;
import kusitms.spin.tikitak.service.notification.NotificationPersistService.SavedNotification;
import kusitms.spin.tikitak.service.notification.dto.NotificationResponseDTO;
import kusitms.spin.tikitak.service.notification.push.PushMessage;
import kusitms.spin.tikitak.service.notification.push.PushResult;
import kusitms.spin.tikitak.service.notification.push.PushSender;
import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static kusitms.spin.tikitak.support.fixture.MemberFixture.activeMember;
import static kusitms.spin.tikitak.support.fixture.TeamFixture.activeTeam;
import static kusitms.spin.tikitak.support.fixture.TeamMemberFixture.activeMemberWithoutProfileImg;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
	private PushSender pushSender;

	@Test
	@DisplayName("존재하지 않거나 탈퇴한 회원에게는 알림을 저장/전송하지 않는다")
	void doesNotSaveOrSendWhenMemberIsNotActive() {
		NotificationService notificationService = notificationService();
		NotificationPayload payload = somePayload();
		when(pushSender.isAvailable()).thenReturn(true);
		when(notificationPersistService.saveForActiveMember(1L, payload, true)).thenReturn(Optional.empty());

		notificationService.send(1L, payload);

		verify(notificationPersistService).saveForActiveMember(1L, payload, true);
		verify(notificationPersistService, never()).deleteDeviceTokens(anyList());
		verify(pushSender, never()).send(anyList(), any());
	}

	@Test
	@DisplayName("푸시 발송기를 쓸 수 없으면 토큰을 조회하지 않고 알림만 저장하며 전송하지 않는다")
	void doesNotSendWhenPushSenderIsNotAvailable() {
		NotificationService notificationService = notificationService();
		NotificationPayload payload = somePayload();
		when(pushSender.isAvailable()).thenReturn(false);
		// 토큰이 있어도 발송하지 않는지 보려고 토큰을 넣는다(토큰 0개 분기와 구분)
		when(notificationPersistService.saveForActiveMember(1L, payload, false))
				.thenReturn(Optional.of(new SavedNotification(100L, List.of(new DeviceTokenTarget(1L, "token-1")))));

		notificationService.send(1L, payload);

		verify(notificationPersistService).saveForActiveMember(1L, payload, false);
		verify(pushSender, never()).send(anyList(), any());
		verify(notificationPersistService, never()).deleteDeviceTokens(anyList());
	}

	@Test
	@DisplayName("등록된 디바이스 토큰이 없으면 푸시를 발송하지 않는다")
	void doesNotSendWhenNoDeviceTokens() {
		NotificationService notificationService = notificationService();
		NotificationPayload payload = somePayload();
		when(pushSender.isAvailable()).thenReturn(true);
		when(notificationPersistService.saveForActiveMember(1L, payload, true))
				.thenReturn(Optional.of(new SavedNotification(100L, List.of())));

		notificationService.send(1L, payload);

		verify(pushSender, never()).send(anyList(), any());
		verify(notificationPersistService, never()).deleteDeviceTokens(anyList());
	}

	@Test
	@DisplayName("발송 결과가 INVALID_TOKEN이면 해당 디바이스 토큰을 삭제한다")
	void deletesDeviceTokenWhenInvalidToken() {
		NotificationService notificationService = notificationService();
		NotificationPayload payload = somePayload();
		givenSavedWithTokens(payload, new DeviceTokenTarget(1L, "token-1"));
		when(pushSender.send(anyList(), any()))
				.thenReturn(List.of(PushResult.invalidToken("UNREGISTERED", "unregistered")));

		notificationService.send(1L, payload);

		verify(notificationPersistService).deleteDeviceTokens(List.of(1L));
	}

	@Test
	@DisplayName("발송에 성공하면 디바이스 토큰을 삭제하지 않는다")
	void doesNotDeleteDeviceTokenOnSuccess() {
		NotificationService notificationService = notificationService();
		NotificationPayload payload = somePayload();
		givenSavedWithTokens(payload, new DeviceTokenTarget(1L, "token-1"));
		when(pushSender.send(anyList(), any())).thenReturn(List.of(PushResult.success()));

		notificationService.send(1L, payload);

		verify(pushSender).send(anyList(), any());
		verify(notificationPersistService, never()).deleteDeviceTokens(anyList());
	}

	@Test
	@DisplayName("발송 결과가 FAILED이면 디바이스 토큰을 삭제하지 않는다")
	void doesNotDeleteDeviceTokenOnFailed() {
		NotificationService notificationService = notificationService();
		NotificationPayload payload = somePayload();
		givenSavedWithTokens(payload, new DeviceTokenTarget(1L, "token-1"));
		when(pushSender.send(anyList(), any())).thenReturn(List.of(PushResult.failed("UNAVAILABLE", "unavailable")));

		notificationService.send(1L, payload);

		verify(pushSender).send(anyList(), any());
		verify(notificationPersistService, never()).deleteDeviceTokens(anyList());
	}

	@Test
	@DisplayName("등록된 모든 디바이스 토큰으로 같은 메시지를 발송하고 INVALID_TOKEN인 토큰만 삭제한다")
	void sendsToAllDeviceTokensAndDeletesOnlyInvalidOnes() {
		NotificationService notificationService = notificationService();
		NotificationPayload payload = somePayload();
		givenSavedWithTokens(
				payload,
				new DeviceTokenTarget(1L, "token-valid"),
				new DeviceTokenTarget(2L, "token-invalid"),
				new DeviceTokenTarget(3L, "token-failed")
		);
		List<String> expectedTokens = List.of("token-valid", "token-invalid", "token-failed");
		PushMessage expectedMessage = new PushMessage(
				"title",
				"body",
				Map.of("type", "FEED_COMMENT", "notificationId", "100")
		);
		when(pushSender.send(expectedTokens, expectedMessage))
				.thenReturn(List.of(
						PushResult.success(),
						PushResult.invalidToken("UNREGISTERED", "unregistered"),
						PushResult.failed("UNAVAILABLE", "unavailable")
				));

		notificationService.send(1L, payload);

		verify(pushSender).send(expectedTokens, expectedMessage);
		verify(notificationPersistService).deleteDeviceTokens(List.of(2L));
	}

	@Test
	@DisplayName("메시지 data는 payload data 위에 type, notificationId 순으로 덮어써서 만든다")
	void buildsPushMessageDataWithTypeAndNotificationIdOverridingPayloadData() {
		NotificationService notificationService = notificationService();
		NotificationPayload payload = NotificationPayload.builder()
				.type(NotificationType.FEED_COMMENT)
				.title("댓글 알림")
				.body("새 댓글이 달렸어요")
				.data(Map.of(
						"feedId", "20",
						"type", "payload-type",
						"notificationId", "payload-notification-id"
				))
				.build();
		givenSavedWithTokens(payload, new DeviceTokenTarget(1L, "token-1"));
		when(pushSender.send(anyList(), any())).thenReturn(List.of(PushResult.success()));

		notificationService.send(1L, payload);

		ArgumentCaptor<PushMessage> captor = ArgumentCaptor.forClass(PushMessage.class);
		verify(pushSender).send(eq(List.of("token-1")), captor.capture());
		assertThat(captor.getValue()).isEqualTo(new PushMessage(
				"댓글 알림",
				"새 댓글이 달렸어요",
				Map.of("feedId", "20", "type", "FEED_COMMENT", "notificationId", "100")
		));
	}

	@Test
	@DisplayName("notificationId가 null이면 메시지 data에 notificationId를 덮어쓰지 않는다")
	void doesNotPutNotificationIdWhenNull() {
		NotificationService notificationService = notificationService();
		NotificationPayload payload = NotificationPayload.builder()
				.type(NotificationType.FEED_COMMENT)
				.title("title")
				.body("body")
				.data(Map.of("feedId", "20", "notificationId", "payload-notification-id"))
				.build();
		when(pushSender.isAvailable()).thenReturn(true);
		when(notificationPersistService.saveForActiveMember(1L, payload, true))
				.thenReturn(Optional.of(new SavedNotification(null, List.of(new DeviceTokenTarget(1L, "token-1")))));
		when(pushSender.send(anyList(), any())).thenReturn(List.of(PushResult.success()));

		notificationService.send(1L, payload);

		ArgumentCaptor<PushMessage> captor = ArgumentCaptor.forClass(PushMessage.class);
		verify(pushSender).send(eq(List.of("token-1")), captor.capture());
		assertThat(captor.getValue()).isEqualTo(new PushMessage(
				"title",
				"body",
				Map.of("feedId", "20", "type", "FEED_COMMENT", "notificationId", "payload-notification-id")
		));
	}

	@Test
	@DisplayName("발송 결과를 받지 못해 모든 토큰이 FAILED여도 예외를 전파하지 않고 토큰을 삭제하지 않는다")
	void doesNotPropagateOrDeleteWhenAllTokensFailed() {
		NotificationService notificationService = notificationService();
		NotificationPayload payload = somePayload();
		givenSavedWithTokens(payload, new DeviceTokenTarget(1L, "token-1"), new DeviceTokenTarget(2L, "token-2"));
		PushResult cancelled = PushResult.failed("CANCELLED", "interrupted");
		when(pushSender.send(anyList(), any())).thenReturn(List.of(cancelled, cancelled));

		notificationService.send(1L, payload);

		verify(pushSender).send(anyList(), any());
		verify(notificationPersistService, never()).deleteDeviceTokens(anyList());
	}

	@Test
	@DisplayName("발송기가 RuntimeException을 던지면 알림 저장(별도 트랜잭션)은 이미 끝난 뒤이고, 예외는 전파되며 토큰은 삭제하지 않는다")
	void notificationIsSavedBeforePushSenderRuntimeExceptionPropagates() {
		NotificationService notificationService = notificationService();
		NotificationPayload payload = somePayload();
		givenSavedWithTokens(payload, new DeviceTokenTarget(1L, "token-1"));
		RuntimeException pushFailure = new RuntimeException("push failed");
		when(pushSender.send(anyList(), any())).thenThrow(pushFailure);

		assertThatThrownBy(() -> notificationService.send(1L, payload)).isSameAs(pushFailure);

		InOrder inOrder = inOrder(notificationPersistService, pushSender);
		inOrder.verify(notificationPersistService).saveForActiveMember(1L, payload, true);
		inOrder.verify(pushSender).send(anyList(), any());
		verify(notificationPersistService, never()).deleteDeviceTokens(anyList());
	}

	@Test
	@DisplayName("커서 없이 알림 목록을 조회하면 최신순 첫 페이지를 반환한다")
	void listsFirstPageWhenCursorIsAbsent() {
		NotificationService notificationService = notificationService();
		Notification notification = notification(1L, false);
		when(notificationRepository.findPage(eq(new NotificationListCondition(1L, null, null, null)), eq(21)))
				.thenReturn(List.of(notification));
		when(notificationRepository.countAll(eq(new NotificationScope(1L, null)))).thenReturn(1L);

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
		NotificationService notificationService = notificationService();
		Notification notification = notification(1L, false);
		when(notificationRepository.findPage(eq(new NotificationListCondition(1L, 10L, null, null)), eq(21)))
				.thenReturn(List.of(notification));
		when(notificationRepository.countAll(eq(new NotificationScope(1L, 10L)))).thenReturn(1L);

		NotificationResponseDTO.NotificationListResponseDTO response =
				notificationService.listNotifications(1L, 10L, null, null);

		assertThat(response.getItems()).hasSize(1);
		assertThat(response.getPageInfo().getTotalCount()).isEqualTo(1L);
		verify(notificationRepository, never()).findPage(eq(new NotificationListCondition(1L, null, null, null)), anyInt());
		verify(notificationRepository, never()).countAll(eq(new NotificationScope(1L, null)));
	}

	@Test
	@DisplayName("커서를 전달하면 디코딩한 커서로 다음 페이지를 조회하고, 전체 개수는 커서 없이 조회한다")
	void listsNextPageWithDecodedCursor() {
		NotificationService notificationService = notificationService();
		LocalDateTime cursorCreatedAt = LocalDateTime.of(2026, 11, 20, 10, 30, 15, 123_000_000);
		Notification notification = notification(4L, false);
		when(notificationRepository.findPage(eq(new NotificationListCondition(1L, 10L, cursorCreatedAt, 5L)), eq(3)))
				.thenReturn(List.of(notification));
		when(notificationRepository.countAll(eq(new NotificationScope(1L, 10L)))).thenReturn(7L);

		NotificationResponseDTO.NotificationListResponseDTO response =
				notificationService.listNotifications(1L, 10L, cursorCreatedAt + "_5", 2);

		assertThat(response.getItems()).extracting(NotificationResponseDTO.NotificationListItemDTO::getNotificationId)
				.containsExactly(4L);
		assertThat(response.getPageInfo().isHasNext()).isFalse();
		assertThat(response.getPageInfo().getNextCursor()).isNull();
		assertThat(response.getPageInfo().getSize()).isEqualTo(2);
		assertThat(response.getPageInfo().getTotalCount()).isEqualTo(7L);
	}

	@Test
	@DisplayName("알림 목록 조회 시 작성자 프로필과 피드 이미지를 함께 반환한다")
	void listNotificationsIncludesActorProfileAndFeedImages() {
		NotificationService notificationService = notificationService();
		Notification notification = notification(1L, false);
		TeamMember actor = activeMemberWithoutProfileImg(30L, activeMember(3L), activeTeam(10L));
		FeedImage firstImage = FeedImage.builder()
				.id(40L)
				.feed(Feed.builder().id(20L).build())
				.imgUrl("https://example.com/feed.png")
				.orderIndex(0)
				.build();
		when(notificationRepository.findPage(eq(new NotificationListCondition(1L, null, null, null)), eq(21)))
				.thenReturn(List.of(notification));
		when(notificationRepository.countAll(eq(new NotificationScope(1L, null)))).thenReturn(1L);
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
		NotificationService notificationService = notificationService();
		when(notificationRepository.countUnread(eq(new NotificationScope(1L, null)))).thenReturn(3L);

		NotificationResponseDTO.UnreadCountResponseDTO response = notificationService.getUnreadCount(1L, null);

		assertThat(response.getUnreadCount()).isEqualTo(3L);
	}

	@Test
	@DisplayName("teamId를 전달하면 해당 팀의 안읽은 알림 개수만 조회한다")
	void returnsUnreadCountFilteredByTeamId() {
		NotificationService notificationService = notificationService();
		when(notificationRepository.countUnread(eq(new NotificationScope(1L, 10L)))).thenReturn(2L);

		NotificationResponseDTO.UnreadCountResponseDTO response = notificationService.getUnreadCount(1L, 10L);

		assertThat(response.getUnreadCount()).isEqualTo(2L);
	}

	@Test
	@DisplayName("본인 소유의 알림을 읽음 처리한다")
	void marksOwnNotificationAsRead() {
		NotificationService notificationService = notificationService();
		Notification notification = notification(1L, false);
		when(notificationRepository.findByIdAndMemberId(1L, 1L)).thenReturn(Optional.of(notification));

		notificationService.markAsRead(1L, 1L);

		assertThat(notification.isRead()).isTrue();
	}

	@Test
	@DisplayName("존재하지 않거나 본인 소유가 아닌 알림을 읽음 처리하면 예외가 발생한다")
	void throwsWhenMarkingNotOwnedNotificationAsRead() {
		NotificationService notificationService = notificationService();
		when(notificationRepository.findByIdAndMemberId(1L, 1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> notificationService.markAsRead(1L, 1L))
				.isInstanceOf(BusinessException.class);
	}

	@Test
	@DisplayName("전체 알림을 읽음 처리하면 벌크 업데이트를 호출한다")
	void marksAllNotificationsAsRead() {
		NotificationService notificationService = notificationService();

		notificationService.markAllAsRead(1L, null);

		verify(notificationRepository).markAllAsRead(eq(new NotificationScope(1L, null)), any(LocalDateTime.class));
	}

	@Test
	@DisplayName("teamId를 전달하면 해당 팀 알림만 읽음 처리한다")
	void marksTeamNotificationsAsRead() {
		NotificationService notificationService = notificationService();

		notificationService.markAllAsRead(1L, 10L);

		verify(notificationRepository).markAllAsRead(eq(new NotificationScope(1L, 10L)), any(LocalDateTime.class));
	}

	private NotificationService notificationService() {
		return new NotificationService(
				notificationPersistService,
				notificationRepository,
				teamMemberRepository,
				feedImageRepository,
				defaultProfileImageResolver,
				imageUrlResolver,
				pushSender
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
		when(pushSender.isAvailable()).thenReturn(true);
		when(notificationPersistService.saveForActiveMember(1L, payload, true))
				.thenReturn(Optional.of(new SavedNotification(100L, List.of(deviceTokens))));
	}

	private NotificationPayload somePayload() {
		return NotificationPayload.builder()
				.type(NotificationType.FEED_COMMENT)
				.title("title")
				.body("body")
				.build();
	}
}
