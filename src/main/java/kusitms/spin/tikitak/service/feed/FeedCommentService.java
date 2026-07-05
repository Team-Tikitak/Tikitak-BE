package kusitms.spin.tikitak.service.feed;

import kusitms.spin.tikitak.domain.feed.entity.Feed;
import kusitms.spin.tikitak.domain.feed.entity.FeedComment;
import kusitms.spin.tikitak.domain.feed.entity.FeedImage;
import kusitms.spin.tikitak.domain.notification.event.FeedCommentCreatedEvent;
import kusitms.spin.tikitak.domain.notification.event.FeedCommentRepliedEvent;
import kusitms.spin.tikitak.domain.team.entity.Team;
import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus;
import kusitms.spin.tikitak.domain.team.enums.TeamStatus;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.repository.feed.FeedCommentRepository;
import kusitms.spin.tikitak.repository.feed.FeedImageRepository;
import kusitms.spin.tikitak.repository.feed.FeedRepository;
import kusitms.spin.tikitak.repository.team.TeamMemberRepository;
import kusitms.spin.tikitak.repository.team.TeamRepository;
import kusitms.spin.tikitak.service.feed.dto.FeedCommentRequestDTO;
import kusitms.spin.tikitak.service.feed.dto.FeedCommentResponseDTO;
import kusitms.spin.tikitak.service.me.DefaultProfileImageResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedCommentService {

	private final FeedRepository feedRepository;
	private final FeedImageRepository feedImageRepository;
	private final FeedCommentRepository feedCommentRepository;
	private final TeamRepository teamRepository;
	private final TeamMemberRepository teamMemberRepository;
	private final DefaultProfileImageResolver defaultProfileImageResolver;
	private final ApplicationEventPublisher eventPublisher;

	public FeedCommentResponseDTO.CommentListResponseDTO listComments(
			Long memberId, Long teamId, Long feedId, Long feedImageId, String cursor, Integer size
	) {
		TeamMember viewer = getActiveTeamMember(memberId, teamId);
		Feed feed = getActiveFeed(teamId, feedId);
		if (feedImageId != null) {
			getFeedImage(feed.getId(), feedImageId);
		}

		Cursor parsedCursor = parseCursor(cursor);
		int pageSize = FeedCommentValidator.normalizePageSize(size);
		List<FeedComment> comments = findCommentPage(feed.getId(), feedImageId, parsedCursor, pageSize);

		boolean hasNext = comments.size() > pageSize;
		List<FeedComment> items = hasNext ? comments.subList(0, pageSize) : comments;
		List<FeedCommentResponseDTO.CommentItemDTO> responseItems = items.stream()
				.map(comment -> toCommentItem(comment, viewer.getId()))
				.toList();

		String nextCursor = null;
		if (hasNext && !items.isEmpty()) {
			FeedComment last = items.get(items.size() - 1);
			nextCursor = encodeCursor(last.getCreatedAt(), last.getId());
		}

		return FeedCommentResponseDTO.CommentListResponseDTO.builder()
				.items(responseItems)
				.pageInfo(FeedCommentResponseDTO.PageInfoDTO.builder()
						.nextCursor(nextCursor)
						.hasNext(hasNext)
						.size(pageSize)
						.build())
				.build();
	}

	@Transactional
	public FeedCommentResponseDTO.CommentItemDTO createComment(
			Long memberId,
			Long teamId,
			Long feedId,
			FeedCommentRequestDTO.CommentCreateRequestDTO request
	) {
		TeamMember author = getActiveTeamMember(memberId, teamId);
		Feed feed = getActiveFeed(teamId, feedId);
		if (request == null) {
			throw new BusinessException(ErrorCode.COMMENT002);
		}
		FeedImage feedImage = getFeedImage(feed.getId(), request.getFeedImageId());
		String content = FeedCommentValidator.normalizeContent(request.getContent());
		FeedCommentValidator.validatePositionPair(request.getPositionX(), request.getPositionY());

		FeedComment comment = FeedComment.builder()
				.feed(feed)
				.feedImage(feedImage)
				.teamMember(author)
				.content(content)
				.positionX(request.getPositionX())
				.positionY(request.getPositionY())
				.deleted(false)
				.build();

		FeedComment savedComment = feedCommentRepository.save(comment);

		Long feedOwnerMemberId = feed.getTeamMember().getMember().getId();
		if (!feedOwnerMemberId.equals(memberId)) {
			eventPublisher.publishEvent(
					new FeedCommentCreatedEvent(feedOwnerMemberId, author.getId(), author.getNickname(), feed.getId(), teamId));
		}

		feedCommentRepository.findDistinctMemberIdsByPosition(
						feedImage.getId(), savedComment.getPositionX(), savedComment.getPositionY())
				.stream()
				.filter(id -> !id.equals(memberId) && !id.equals(feedOwnerMemberId))
				.forEach(recipientId -> eventPublisher.publishEvent(
						new FeedCommentRepliedEvent(recipientId, author.getId(), author.getNickname(), feed.getId(), teamId)));

		return toCommentItem(savedComment, author.getId());
	}

	@Transactional
	public FeedCommentResponseDTO.CommentItemDTO updateComment(
			Long memberId,
			Long teamId,
			Long feedId,
			Long commentId,
			FeedCommentRequestDTO.CommentUpdateRequestDTO request
	) {
		TeamMember editor = getActiveTeamMember(memberId, teamId);
		getActiveFeed(teamId, feedId);
		if (request == null) {
			throw new BusinessException(ErrorCode.COMMENT012);
		}
		FeedComment comment = feedCommentRepository.findActiveByIdAndFeedId(commentId, feedId)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMENT006));
		if (!comment.getTeamMember().getId().equals(editor.getId())) {
			throw new BusinessException(ErrorCode.COMMENT008);
		}
		if (request.getContent() == null && request.getPositionX() == null && request.getPositionY() == null) {
			throw new BusinessException(ErrorCode.COMMENT012);
		}

		if (request.getContent() != null) {
			comment.updateContent(FeedCommentValidator.normalizeContent(request.getContent()));
		}
		if (request.getPositionX() != null || request.getPositionY() != null) {
			FeedCommentValidator.validatePositionPair(request.getPositionX(), request.getPositionY());
			comment.movePosition(request.getPositionX(), request.getPositionY());
		}

		return toCommentItem(comment, editor.getId());
	}

	@Transactional
	public void deleteComment(Long memberId, Long teamId, Long feedId, Long commentId) {
		TeamMember caller = getActiveTeamMember(memberId, teamId);
		getActiveFeed(teamId, feedId);
		FeedComment comment = feedCommentRepository.findActiveByIdAndFeedId(commentId, feedId)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMENT006));
		if (!comment.getTeamMember().getId().equals(caller.getId())) {
			throw new BusinessException(ErrorCode.COMMENT005);
		}
		feedCommentRepository.delete(comment);
	}

	private TeamMember getActiveTeamMember(Long memberId, Long teamId) {
		Team team = teamRepository.findById(teamId)
				.orElseThrow(() -> new BusinessException(ErrorCode.TEAM009));
		if (team.getStatus() != TeamStatus.ACTIVE) {
			throw new BusinessException(ErrorCode.TEAM009);
		}
		return teamMemberRepository.findActiveByMemberIdAndTeamId(
						memberId, teamId, TeamMemberStatus.ACTIVE, TeamStatus.ACTIVE)
				.orElseThrow(() -> new BusinessException(ErrorCode.TEAM008));
	}

	private Feed getActiveFeed(Long teamId, Long feedId) {
		return feedRepository.findActiveDetail(teamId, feedId)
				.orElseThrow(() -> new BusinessException(ErrorCode.FEED003));
	}

	private FeedImage getFeedImage(Long feedId, Long feedImageId) {
		if (feedImageId == null) {
			throw new BusinessException(ErrorCode.COMMENT011);
		}
		return feedImageRepository.findByIdAndFeedId(feedImageId, feedId)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMENT011));
	}

	private List<FeedComment> findCommentPage(Long feedId, Long feedImageId, Cursor cursor, int pageSize) {
		PageRequest pageRequest = PageRequest.of(0, pageSize + 1);
		if (feedImageId == null) {
			if (cursor.createdAt() == null) {
				return feedCommentRepository.findActiveFirstPageByFeedId(feedId, pageRequest);
			}
			return feedCommentRepository.findActiveCursorPageByFeedId(
					feedId, cursor.createdAt(), cursor.commentId(), pageRequest);
		}
		if (cursor.createdAt() == null) {
			return feedCommentRepository.findActiveFirstPageByFeedImageId(feedId, feedImageId, pageRequest);
		}
		return feedCommentRepository.findActiveCursorPageByFeedImageId(
				feedId, feedImageId, cursor.createdAt(), cursor.commentId(), pageRequest);
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

	private String encodeCursor(LocalDateTime createdAt, Long commentId) {
		return createdAt + "_" + commentId;
	}

	private FeedCommentResponseDTO.CommentItemDTO toCommentItem(FeedComment comment, Long viewerTeamMemberId) {
		TeamMember author = comment.getTeamMember();
		return FeedCommentResponseDTO.CommentItemDTO.builder()
				.commentId(comment.getId())
				.feedId(comment.getFeed().getId())
				.feedImageId(comment.getFeedImage().getId())
				.content(comment.getContent())
				.positionX(comment.getPositionX())
				.positionY(comment.getPositionY())
				.author(FeedCommentResponseDTO.AuthorDTO.builder()
						.teamMemberId(author.getId())
						.nickname(author.getNickname())
						.profileImageUrl(defaultProfileImageResolver.resolveForTeamMember(author))
						.isAnonymous(false)
						.build())
				.isMine(author.getId().equals(viewerTeamMemberId))
				.createdAt(comment.getCreatedAt())
				.updatedAt(comment.getUpdatedAt())
				.build();
	}

	private record Cursor(LocalDateTime createdAt, Long commentId) {
	}
}
