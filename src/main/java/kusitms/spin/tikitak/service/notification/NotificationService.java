package kusitms.spin.tikitak.service.notification;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.SendResponse;
import kusitms.spin.tikitak.domain.feed.entity.FeedImage;
import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.member.enums.MemberStatus;
import kusitms.spin.tikitak.domain.notification.entity.MemberDeviceToken;
import kusitms.spin.tikitak.domain.notification.entity.Notification;
import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.repository.feed.FeedImageRepository;
import kusitms.spin.tikitak.repository.member.MemberRepository;
import kusitms.spin.tikitak.repository.notification.MemberDeviceTokenRepository;
import kusitms.spin.tikitak.repository.notification.NotificationRepository;
import kusitms.spin.tikitak.repository.team.TeamMemberRepository;
import kusitms.spin.tikitak.service.me.DefaultProfileImageResolver;
import kusitms.spin.tikitak.service.media.ImagePreset;
import kusitms.spin.tikitak.service.media.ImageUrlResolver;
import kusitms.spin.tikitak.service.notification.dto.NotificationPayload;
import kusitms.spin.tikitak.service.notification.dto.NotificationResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final int MAX_PAGE_SIZE = 50;

	private final MemberRepository memberRepository;
	private final MemberDeviceTokenRepository deviceTokenRepository;
	private final NotificationRepository notificationRepository;
	private final TeamMemberRepository teamMemberRepository;
	private final FeedImageRepository feedImageRepository;
	private final DefaultProfileImageResolver defaultProfileImageResolver;
	private final ImageUrlResolver imageUrlResolver;
	private final Optional<FirebaseMessaging> firebaseMessaging;

	@Transactional
	public void send(Long memberId, NotificationPayload payload) {
		Optional<Member> activeMember = memberRepository.findById(memberId)
				.filter(member -> member.getStatus() == MemberStatus.ACTIVE);
		if (activeMember.isEmpty()) {
			log.warn("존재하지 않거나 탈퇴한 회원에게는 알림을 저장/전송하지 않습니다. memberId={}, type={}", memberId, payload.getType());
			return;
		}
		Notification notification = Notification.create(activeMember.get(), payload);
		Notification savedNotification = notificationRepository.save(notification);

		if (firebaseMessaging.isEmpty()) {
			log.warn("FCM이 설정되지 않아 알림을 전송하지 않습니다. memberId={}, type={}", memberId, payload.getType());
			return;
		}

		List<MemberDeviceToken> deviceTokens = deviceTokenRepository.findAllByMemberId(memberId);
		if (deviceTokens.isEmpty()) {
			return;
		}

		List<Message> messages = deviceTokens.stream()
				.map(deviceToken -> toMessage(deviceToken, payload, savedNotification == null ? null : savedNotification.getId()))
				.toList();

		try {
			BatchResponse batchResponse = firebaseMessaging.get().sendEach(messages);
			handleResponses(deviceTokens, batchResponse.getResponses());
		} catch (FirebaseMessagingException e) {
			log.error("FCM 알림 전송에 실패했습니다. memberId={}, type={}", memberId, payload.getType(), e);
		}
	}

	private Message toMessage(MemberDeviceToken deviceToken, NotificationPayload payload, Long notificationId) {
		Message.Builder builder = Message.builder()
				.setToken(deviceToken.getFcmToken())
				.setNotification(com.google.firebase.messaging.Notification.builder()
						.setTitle(payload.getTitle())
						.setBody(payload.getBody())
						.build())
				.putAllData(payload.getData())
				.putData("type", payload.getType().name());
		if (notificationId != null) {
			builder.putData("notificationId", String.valueOf(notificationId));
		}
		return builder.build();
	}

	public NotificationResponseDTO.NotificationListResponseDTO listNotifications(Long memberId, Long teamId, String cursor, Integer size) {
		Cursor parsedCursor = parseCursor(cursor);
		int pageSize = normalizePageSize(size);

		List<Notification> notifications = parsedCursor.createdAt() == null
				? notificationRepository.findFirstPage(memberId, teamId, PageRequest.of(0, pageSize + 1))
				: notificationRepository.findCursorPage(
						memberId, teamId, parsedCursor.createdAt(), parsedCursor.id(), PageRequest.of(0, pageSize + 1));
		long totalCount = notificationRepository.countByMemberIdAndTeamId(memberId, teamId);

		boolean hasNext = notifications.size() > pageSize;
		List<Notification> items = hasNext ? notifications.subList(0, pageSize) : notifications;

		Map<Long, String> profileImageUrls = profileImageUrls(items);
		Map<Long, String> firstImageUrls = firstImageUrls(items);

		List<NotificationResponseDTO.NotificationListItemDTO> responseItems = items.stream()
				.map(notification -> toListItem(notification, profileImageUrls, firstImageUrls))
				.toList();

		String nextCursor = null;
		if (hasNext && !responseItems.isEmpty()) {
			Notification last = items.get(items.size() - 1);
			nextCursor = encodeCursor(last.getCreatedAt(), last.getId());
		}

		return NotificationResponseDTO.NotificationListResponseDTO.builder()
				.items(responseItems)
				.pageInfo(NotificationResponseDTO.PageInfoDTO.builder()
						.nextCursor(nextCursor)
						.hasNext(hasNext)
						.size(pageSize)
						.totalCount(totalCount)
						.build())
				.build();
	}

	public NotificationResponseDTO.UnreadCountResponseDTO getUnreadCount(Long memberId) {
		return NotificationResponseDTO.UnreadCountResponseDTO.builder()
				.unreadCount(notificationRepository.countByMemberIdAndIsReadFalse(memberId))
				.build();
	}

	@Transactional
	public void markAsRead(Long memberId, Long notificationId) {
		Notification notification = notificationRepository.findByIdAndMemberId(notificationId, memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION001));
		notification.markAsRead();
	}

	@Transactional
	public void markAllAsRead(Long memberId) {
		notificationRepository.updateAllAsReadByMemberId(memberId, LocalDateTime.now());
	}

	private NotificationResponseDTO.NotificationListItemDTO toListItem(
			Notification notification,
			Map<Long, String> profileImageUrls,
			Map<Long, String> firstImageUrls
	) {
		String profileImageUrl = notification.getActorTeamMemberId() == null
				? null
				: profileImageUrls.get(notification.getActorTeamMemberId());
		String firstImageUrl = notification.getFeedId() == null
				? null
				: firstImageUrls.get(notification.getFeedId());
		return NotificationResponseDTO.NotificationListItemDTO.builder()
				.notificationId(notification.getId())
				.type(notification.getType())
				.title(notification.getTitle())
				.body(notification.getBody())
				.teamId(notification.getTeamId())
				.feedId(notification.getFeedId())
				.profileImageUrl(profileImageUrl)
				.thumbnailImageUrl(imageUrlResolver.resolve(firstImageUrl, ImagePreset.FEED_THUMB))
				.heroPreviewUrl(imageUrlResolver.resolve(firstImageUrl, ImagePreset.FEED_HERO_PREVIEW))
				.isRead(notification.isRead())
				.createdAt(notification.getCreatedAt())
				.build();
	}

	private Map<Long, String> profileImageUrls(List<Notification> notifications) {
		List<Long> actorTeamMemberIds = notifications.stream()
				.map(Notification::getActorTeamMemberId)
				.filter(id -> id != null)
				.distinct()
				.toList();
		if (actorTeamMemberIds.isEmpty()) {
			return Map.of();
		}
		return teamMemberRepository.findByIdsWithMember(actorTeamMemberIds).stream()
				.collect(Collectors.toMap(
						TeamMember::getId,
						defaultProfileImageResolver::resolveForTeamMember,
						(first, second) -> first
				));
	}

	private Map<Long, String> firstImageUrls(List<Notification> notifications) {
		List<Long> feedIds = notifications.stream()
				.map(Notification::getFeedId)
				.filter(id -> id != null)
				.distinct()
				.toList();
		if (feedIds.isEmpty()) {
			return Map.of();
		}
		return feedImageRepository.findFirstActiveByFeedIds(feedIds).stream()
				.collect(Collectors.toMap(
						image -> image.getFeed().getId(),
						FeedImage::getImgUrl,
						(first, second) -> first
				));
	}

	private int normalizePageSize(Integer size) {
		if (size == null || size < 1) {
			return DEFAULT_PAGE_SIZE;
		}
		return Math.min(size, MAX_PAGE_SIZE);
	}

	private Cursor parseCursor(String cursor) {
		if (cursor == null || cursor.isBlank()) {
			return new Cursor(null, null);
		}
		int separator = cursor.lastIndexOf('_');
		if (separator < 1 || separator == cursor.length() - 1) {
			throw new BusinessException(ErrorCode.COMMON001);
		}
		try {
			return new Cursor(
					LocalDateTime.parse(cursor.substring(0, separator)),
					Long.parseLong(cursor.substring(separator + 1))
			);
		} catch (RuntimeException e) {
			throw new BusinessException(ErrorCode.COMMON001, e);
		}
	}

	private String encodeCursor(LocalDateTime createdAt, Long id) {
		return createdAt + "_" + id;
	}

	private record Cursor(LocalDateTime createdAt, Long id) {
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
