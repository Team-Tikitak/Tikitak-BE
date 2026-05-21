package kusitms.spin.tikitak.service.feed;

import kusitms.spin.tikitak.domain.feed.entity.Feed;
import kusitms.spin.tikitak.domain.feed.entity.FeedImage;
import kusitms.spin.tikitak.domain.feed.entity.FeedReaction;
import kusitms.spin.tikitak.domain.feed.entity.FeedTag;
import kusitms.spin.tikitak.domain.feed.enums.FeedReactionType;
import kusitms.spin.tikitak.domain.media.entity.Media;
import kusitms.spin.tikitak.domain.media.enums.MediaPurpose;
import kusitms.spin.tikitak.domain.media.enums.MediaStatus;
import kusitms.spin.tikitak.domain.place.entity.Place;
import kusitms.spin.tikitak.domain.team.entity.Team;
import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus;
import kusitms.spin.tikitak.domain.team.enums.TeamStatus;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.repository.feed.FeedCommentRepository;
import kusitms.spin.tikitak.repository.feed.FeedReactionRepository;
import kusitms.spin.tikitak.repository.feed.FeedRepository;
import kusitms.spin.tikitak.repository.media.MediaRepository;
import kusitms.spin.tikitak.repository.place.PlaceRepository;
import kusitms.spin.tikitak.repository.team.TeamMemberRepository;
import kusitms.spin.tikitak.repository.team.TeamRepository;
import kusitms.spin.tikitak.service.feed.dto.FeedRequestDTO;
import kusitms.spin.tikitak.service.feed.dto.FeedResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedService {

	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final int MAX_PAGE_SIZE = 50;
	private static final int MAX_IMAGE_COUNT = 10;
	private static final int MAX_TAG_COUNT = 11;
	private static final int MAX_CONTENT_LENGTH = 1000;

	private final FeedRepository feedRepository;
	private final FeedReactionRepository feedReactionRepository;
	private final FeedCommentRepository feedCommentRepository;
	private final PlaceRepository placeRepository;
	private final MediaRepository mediaRepository;
	private final TeamRepository teamRepository;
	private final TeamMemberRepository teamMemberRepository;

	public FeedResponseDTO.FeedListResponseDTO listFeeds(
			Long memberId,
			Long teamId,
			String cursor,
			Integer size,
			String placeId,
			String type,
			List<Long> taggedTeamMemberIds
	) {
		TeamMember viewer = getActiveTeamMember(memberId, teamId);
		Cursor parsedCursor = parseCursor(cursor);
		int pageSize = normalizePageSize(size);
		FeedTypeFilter feedType = parseFeedType(type);
		List<Long> normalizedTaggedTeamMemberIds = normalizeTaggedTeamMemberIds(taggedTeamMemberIds);
		validateTaggedTeamMembers(teamId, normalizedTaggedTeamMemberIds);

		List<Feed> feeds = findFeedPage(
				teamId, blankToNull(placeId), feedType, normalizedTaggedTeamMemberIds, parsedCursor, pageSize);

		boolean hasNext = feeds.size() > pageSize;
		List<Feed> items = hasNext ? feeds.subList(0, pageSize) : feeds;
		List<Long> feedIds = items.stream().map(Feed::getId).toList();

		Map<Long, Long> commentCounts = commentCounts(feedIds);
		Map<Long, FeedResponseDTO.ReactionSummaryDTO> summaries = reactionSummaries(feedIds);
		Map<Long, FeedReactionType> myReactions = myReactions(feedIds, viewer.getId());

		List<FeedResponseDTO.FeedListItemDTO> responseItems = items.stream()
				.map(feed -> toListItem(
						feed,
						commentCounts.getOrDefault(feed.getId(), 0L),
						summaries.getOrDefault(feed.getId(), emptyReactionSummary()),
						myReactions.get(feed.getId())
				))
				.toList();

		String nextCursor = null;
		if (hasNext && !responseItems.isEmpty()) {
			Feed last = items.get(items.size() - 1);
			nextCursor = encodeCursor(last.getCreatedAt(), last.getId());
		}

		return FeedResponseDTO.FeedListResponseDTO.builder()
				.items(responseItems)
				.pageInfo(FeedResponseDTO.PageInfoDTO.builder()
						.nextCursor(nextCursor)
						.hasNext(hasNext)
						.size(pageSize)
						.build())
				.build();
	}

	public FeedResponseDTO.FeedDetailResponseDTO getFeed(Long memberId, Long teamId, Long feedId) {
		TeamMember viewer = getActiveTeamMember(memberId, teamId);
		Feed feed = feedRepository.findActiveDetail(teamId, feedId)
				.orElseThrow(() -> new BusinessException(ErrorCode.FEED003));

		return toDetail(
				feed,
				feedCommentRepository.countByFeedIdAndDeletedFalse(feed.getId()),
				reactionSummary(feed.getId()),
				myReaction(feed.getId(), viewer.getId()),
				feed.getTeamMember().getId().equals(viewer.getId())
		);
	}

	@Transactional
	public FeedResponseDTO.FeedMutationResponseDTO createFeed(
			Long memberId, Long teamId, FeedRequestDTO.FeedCreateRequestDTO request
	) {
		TeamMember author = getActiveTeamMember(memberId, teamId);
		String content = normalizeContent(request.getContent());
		List<UUID> mediaPublicIds = validateMediaPublicIds(request.getMediaPublicIds());
		List<Media> medias = validateCreateMedias(memberId, mediaPublicIds);
		Place place = resolvePlace(request.getPlace());
		List<TeamMember> taggedMembers = resolveTaggedMembers(teamId, request.getTaggedTeamMemberIds());

		Feed feed = Feed.builder()
				.team(author.getTeam())
				.teamMember(author)
				.place(place)
				.question(null)
				.content(content)
				.build();

		for (int i = 0; i < medias.size(); i++) {
			Media media = medias.get(i);
			media.markUsed();
			feed.addImage(FeedImage.builder()
					.media(media)
					.imgUrl(media.getUrl())
					.orderIndex(i)
					.build());
		}
		taggedMembers.forEach(teamMember -> feed.addTag(FeedTag.builder()
				.teamMember(teamMember)
				.build()));

		Feed savedFeed = feedRepository.save(feed);
		return toMutation(savedFeed);
	}

	@Transactional
	public FeedResponseDTO.FeedMutationResponseDTO updateFeed(
			Long memberId, Long teamId, Long feedId, FeedRequestDTO.FeedUpdateRequestDTO request
	) {
		TeamMember editor = getActiveTeamMember(memberId, teamId);
		Feed feed = feedRepository.findActiveForUpdate(teamId, feedId)
				.orElseThrow(() -> new BusinessException(ErrorCode.FEED003));
		if (!feed.getTeamMember().getId().equals(editor.getId())) {
			throw new BusinessException(ErrorCode.FEED011);
		}
		if (feed.hasQuestion()) {
			throw new BusinessException(ErrorCode.DAILY_QUESTION008);
		}

		String content = normalizeContent(request.getContent());
		List<UUID> mediaPublicIds = validateMediaPublicIds(request.getMediaPublicIds());
		List<Media> medias = validateUpdateMedias(memberId, feed, mediaPublicIds);
		Place place = resolvePlace(request.getPlace());
		List<TeamMember> taggedMembers = resolveTaggedMembers(teamId, request.getTaggedTeamMemberIds());

		Set<Long> nextMediaIds = medias.stream().map(Media::getId).collect(Collectors.toSet());
		feed.getImages().stream()
				.map(FeedImage::getMedia)
				.filter(Objects::nonNull)
				.filter(media -> !nextMediaIds.contains(media.getId()))
				.forEach(Media::markDeleted);

		for (Media media : medias) {
			media.markUsed();
		}

		List<FeedImage> images = new ArrayList<>();
		for (int i = 0; i < medias.size(); i++) {
			Media media = medias.get(i);
			images.add(FeedImage.builder()
					.media(media)
					.imgUrl(media.getUrl())
					.orderIndex(i)
					.build());
		}

		List<FeedTag> tags = taggedMembers.stream()
				.map(teamMember -> FeedTag.builder().teamMember(teamMember).build())
				.toList();

		feed.updateGeneralFeed(content, place);
		feed.replaceImages(images);
		feed.replaceTags(tags);
		return toMutation(feed);
	}

	@Transactional
	public void deleteFeed(Long memberId, Long teamId, Long feedId) {
		TeamMember caller = getActiveTeamMember(memberId, teamId);
		Feed feed = feedRepository.findActiveForUpdate(teamId, feedId)
				.orElseThrow(() -> new BusinessException(ErrorCode.FEED003));
		if (!feed.getTeamMember().getId().equals(caller.getId())) {
			throw new BusinessException(ErrorCode.FEED015);
		}

		feed.delete();
		feed.getImages().stream()
				.map(FeedImage::getMedia)
				.filter(Objects::nonNull)
				.forEach(Media::markDeleted);
	}

	@Transactional
	public FeedResponseDTO.FeedReactionResponseDTO upsertReaction(
			Long memberId, Long teamId, Long feedId, FeedRequestDTO.ReactionRequestDTO request
	) {
		TeamMember reactor = getActiveTeamMember(memberId, teamId);
		feedRepository.findActiveDetail(teamId, feedId)
				.orElseThrow(() -> new BusinessException(ErrorCode.FEED003));
		FeedReactionType reactionType = parseReactionType(request.getReactionType());

		feedReactionRepository.upsertReaction(feedId, reactor.getId(), reactionType.name());

		return reactionResponse(feedId, reactionType);
	}

	@Transactional
	public FeedResponseDTO.FeedReactionResponseDTO deleteReaction(Long memberId, Long teamId, Long feedId) {
		TeamMember reactor = getActiveTeamMember(memberId, teamId);
		feedRepository.findActiveDetail(teamId, feedId)
				.orElseThrow(() -> new BusinessException(ErrorCode.FEED003));
		feedReactionRepository.deleteByFeedIdAndTeamMemberId(feedId, reactor.getId());
		return reactionResponse(feedId, null);
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

	private FeedReactionType parseReactionType(String reactionType) {
		try {
			return FeedReactionType.valueOf(reactionType);
		} catch (RuntimeException e) {
			throw new BusinessException(ErrorCode.FEED013, e);
		}
	}

	private String normalizeContent(String content) {
		if (content == null || content.isEmpty()) {
			return null;
		}
		if (content.isBlank()) {
			throw new BusinessException(ErrorCode.FEED007);
		}
		if (content.length() > MAX_CONTENT_LENGTH) {
			throw new BusinessException(ErrorCode.FEED007);
		}
		return content;
	}

	private List<UUID> validateMediaPublicIds(List<UUID> mediaPublicIds) {
		if (mediaPublicIds == null || mediaPublicIds.isEmpty()) {
			throw new BusinessException(ErrorCode.FEED005);
		}
		if (mediaPublicIds.size() > MAX_IMAGE_COUNT) {
			throw new BusinessException(ErrorCode.FEED006);
		}
		if (new LinkedHashSet<>(mediaPublicIds).size() != mediaPublicIds.size()) {
			throw new BusinessException(ErrorCode.MEDIA018);
		}
		return mediaPublicIds;
	}

	private List<Media> validateCreateMedias(Long memberId, List<UUID> mediaPublicIds) {
		Map<UUID, Media> mediaByPublicId = mediaRepository.findByPublicIdsForUpdate(mediaPublicIds).stream()
				.collect(Collectors.toMap(Media::getPublicId, Function.identity()));
		return mediaPublicIds.stream()
				.map(mediaByPublicId::get)
				.peek(media -> validateCreateMedia(memberId, media))
				.toList();
	}

	private void validateCreateMedia(Long memberId, Media media) {
		if (media == null
				|| !media.getMemberId().equals(memberId)
				|| media.getPurpose() != MediaPurpose.FEED_IMAGE
				|| media.getStatus() != MediaStatus.UPLOADED) {
			throw new BusinessException(ErrorCode.MEDIA018);
		}
	}

	private List<Media> validateUpdateMedias(Long memberId, Feed feed, List<UUID> mediaPublicIds) {
		Map<UUID, Media> requested = mediaRepository.findByPublicIdsForUpdate(mediaPublicIds).stream()
				.collect(Collectors.toMap(Media::getPublicId, Function.identity()));
		Set<Long> currentMediaIds = feed.getImages().stream()
				.map(FeedImage::getMedia)
				.filter(Objects::nonNull)
				.map(Media::getId)
				.collect(Collectors.toSet());

		return mediaPublicIds.stream()
				.map(requested::get)
				.peek(media -> validateUpdateMedia(memberId, currentMediaIds, media))
				.toList();
	}

	private void validateUpdateMedia(Long memberId, Set<Long> currentMediaIds, Media media) {
		if (media == null
				|| !media.getMemberId().equals(memberId)
				|| media.getPurpose() != MediaPurpose.FEED_IMAGE) {
			throw new BusinessException(ErrorCode.MEDIA018);
		}
		if (currentMediaIds.contains(media.getId())) {
			if (media.getStatus() != MediaStatus.USED) {
				throw new BusinessException(ErrorCode.MEDIA018);
			}
			return;
		}
		if (media.getStatus() != MediaStatus.UPLOADED) {
			throw new BusinessException(ErrorCode.MEDIA018);
		}
	}

	private Place resolvePlace(FeedRequestDTO.PlaceRequestDTO request) {
		if (request == null) {
			return null;
		}
		String externalPlaceId = blankToNull(request.getPlaceId());
		if (externalPlaceId != null) {
			Optional<Place> existing = placeRepository.findByExternalPlaceId(externalPlaceId);
			if (existing.isPresent()) {
				return existing.get();
			}
			placeRepository.insertIfAbsentByExternalPlaceId(
					externalPlaceId,
					request.getName(),
					request.getLatitude(),
					request.getLongitude(),
					request.getAddress()
			);
			return placeRepository.findByExternalPlaceId(externalPlaceId)
					.orElseThrow(() -> new BusinessException(ErrorCode.COMMON001));
		}
		// Provider-less places cannot be deduplicated safely without merging distinct venues with similar coordinates.
		return placeRepository.save(Place.builder()
				.externalPlaceId(externalPlaceId)
				.name(request.getName())
				.latitude(request.getLatitude())
				.longitude(request.getLongitude())
				.address(request.getAddress())
				.build());
	}

	private List<TeamMember> resolveTaggedMembers(Long teamId, List<Long> taggedTeamMemberIds) {
		List<Long> distinctIds = normalizeTaggedTeamMemberIds(taggedTeamMemberIds);
		if (distinctIds.isEmpty()) {
			return List.of();
		}

		List<TeamMember> members = teamMemberRepository.findActiveByTeamIdAndIds(
				teamId, distinctIds, TeamMemberStatus.ACTIVE, TeamStatus.ACTIVE);
		if (members.size() != distinctIds.size()) {
			throw new BusinessException(ErrorCode.FEED008);
		}
		Map<Long, TeamMember> memberById = members.stream()
				.collect(Collectors.toMap(TeamMember::getId, Function.identity()));
		return distinctIds.stream()
				.map(memberById::get)
				.toList();
	}

	private List<Long> normalizeTaggedTeamMemberIds(List<Long> taggedTeamMemberIds) {
		if (taggedTeamMemberIds == null || taggedTeamMemberIds.isEmpty()) {
			return List.of();
		}
		List<Long> distinctIds = taggedTeamMemberIds.stream()
				.filter(Objects::nonNull)
				.collect(Collectors.toCollection(LinkedHashSet::new))
				.stream()
				.toList();
		if (distinctIds.size() > MAX_TAG_COUNT) {
			throw new BusinessException(ErrorCode.FEED009);
		}
		return distinctIds;
	}

	private void validateTaggedTeamMembers(Long teamId, List<Long> taggedTeamMemberIds) {
		if (taggedTeamMemberIds.isEmpty()) {
			return;
		}
		List<TeamMember> members = teamMemberRepository.findActiveByTeamIdAndIds(
				teamId, taggedTeamMemberIds, TeamMemberStatus.ACTIVE, TeamStatus.ACTIVE);
		if (members.size() != taggedTeamMemberIds.size()) {
			throw new BusinessException(ErrorCode.FEED008);
		}
	}

	private FeedResponseDTO.FeedListItemDTO toListItem(
			Feed feed,
			long commentCount,
			FeedResponseDTO.ReactionSummaryDTO reactionSummary,
			FeedReactionType myReaction
	) {
		return FeedResponseDTO.FeedListItemDTO.builder()
				.feedId(feed.getId())
				.type(feed.getType())
				.content(feed.getContent())
				.thumbnailImageUrl(thumbnailImageUrl(feed))
				.imageCount(feed.getImages().size())
				.author(toAuthor(feed.getTeamMember()))
				.place(toPlace(feed.getPlace(), false))
				.question(toQuestion(feed))
				.commentCount(commentCount)
				.reactionSummary(reactionSummary)
				.myReaction(myReaction)
				.createdAt(feed.getCreatedAt())
				.build();
	}

	private FeedResponseDTO.FeedDetailResponseDTO toDetail(
			Feed feed,
			long commentCount,
			FeedResponseDTO.ReactionSummaryDTO reactionSummary,
			FeedReactionType myReaction,
			boolean isMine
	) {
		return FeedResponseDTO.FeedDetailResponseDTO.builder()
				.feedId(feed.getId())
				.type(feed.getType())
				.content(feed.getContent())
				.author(toAuthor(feed.getTeamMember()))
				.images(sortedImages(feed).stream()
						.map(image -> FeedResponseDTO.ImageDTO.builder()
								.feedImageId(image.getId())
								.imageUrl(image.getImgUrl())
								.orderIndex(image.getOrderIndex())
								.build())
						.toList())
				.place(toPlace(feed.getPlace(), true))
				.question(toQuestion(feed))
				.taggedMembers(feed.getTags().stream()
						.map(FeedTag::getTeamMember)
						.map(this::toTaggedMember)
						.toList())
				.commentCount(commentCount)
				.reactionSummary(reactionSummary)
				.myReaction(myReaction)
				.isMine(isMine)
				.createdAt(feed.getCreatedAt())
				.updatedAt(feed.getUpdatedAt())
				.build();
	}

	private FeedResponseDTO.FeedMutationResponseDTO toMutation(Feed feed) {
		return FeedResponseDTO.FeedMutationResponseDTO.builder()
				.feedId(feed.getId())
				.type(feed.getType())
				.content(feed.getContent())
				.thumbnailImageUrl(thumbnailImageUrl(feed))
				.imageCount(feed.getImages().size())
				.place(toPlace(feed.getPlace(), true))
				.question(toQuestion(feed))
				.taggedMembers(feed.getTags().stream()
						.map(FeedTag::getTeamMember)
						.map(this::toTaggedMember)
						.toList())
				.createdAt(feed.getCreatedAt())
				.updatedAt(feed.getUpdatedAt())
				.build();
	}

	private FeedResponseDTO.AuthorDTO toAuthor(TeamMember teamMember) {
		return FeedResponseDTO.AuthorDTO.builder()
				.teamMemberId(teamMember.getId())
				.nickname(teamMember.getNickname())
				.profileImageUrl(teamMember.getProfileImgUrl())
				.isAnonymous(false)
				.build();
	}

	private FeedResponseDTO.TaggedMemberDTO toTaggedMember(TeamMember teamMember) {
		return FeedResponseDTO.TaggedMemberDTO.builder()
				.teamMemberId(teamMember.getId())
				.nickname(teamMember.getNickname())
				.profileImageUrl(teamMember.getProfileImgUrl())
				.build();
	}

	private FeedResponseDTO.PlaceDTO toPlace(Place place, boolean includeAddress) {
		if (place == null) {
			return null;
		}
		return FeedResponseDTO.PlaceDTO.builder()
				.placeId(place.getExternalPlaceId())
				.name(place.getName())
				.latitude(place.getLatitude())
				.longitude(place.getLongitude())
				.address(includeAddress ? place.getAddress() : null)
				.build();
	}

	private FeedResponseDTO.QuestionDTO toQuestion(Feed feed) {
		if (!feed.hasQuestion()) {
			return null;
		}
		return FeedResponseDTO.QuestionDTO.builder()
				.questionId(feed.getQuestion().getId())
				.content(feed.getQuestion().getContent())
				.answerDate(feed.getQuestionAnswerDate())
				.build();
	}

	private String thumbnailImageUrl(Feed feed) {
		return sortedImages(feed).stream()
				.findFirst()
				.map(FeedImage::getImgUrl)
				.orElse(null);
	}

	private List<FeedImage> sortedImages(Feed feed) {
		return feed.getImages().stream()
				.sorted(Comparator.comparing(FeedImage::getOrderIndex))
				.toList();
	}

	private Map<Long, Long> commentCounts(List<Long> feedIds) {
		if (feedIds.isEmpty()) {
			return Map.of();
		}
		return feedCommentRepository.countByFeedIds(feedIds).stream()
				.collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
	}

	private List<Feed> findFeedPage(
			Long teamId,
			String placeId,
			FeedTypeFilter feedType,
			List<Long> taggedTeamMemberIds,
			Cursor cursor,
			int pageSize
	) {
		PageRequest pageRequest = PageRequest.of(0, pageSize + 1);
		String feedTypeName = feedType.queryValue();
		if (!taggedTeamMemberIds.isEmpty()) {
			if (cursor.createdAt() == null) {
				return feedRepository.findActiveFirstPageByTaggedTeamMemberIds(
						teamId,
						placeId,
						feedTypeName,
						taggedTeamMemberIds,
						(long) taggedTeamMemberIds.size(),
						pageRequest
				);
			}
			return feedRepository.findActiveCursorPageByTaggedTeamMemberIds(
					teamId, placeId, feedTypeName, taggedTeamMemberIds, (long) taggedTeamMemberIds.size(),
					cursor.createdAt(), cursor.feedId(), pageRequest);
		}
		if (cursor.createdAt() == null) {
			if (placeId == null) {
				return feedRepository.findActiveFirstPage(teamId, feedTypeName, pageRequest);
			}
			return feedRepository.findActiveFirstPageByPlaceId(teamId, placeId, feedTypeName, pageRequest);
		}
		if (placeId == null) {
			return feedRepository.findActiveCursorPage(teamId, feedTypeName, cursor.createdAt(), cursor.feedId(), pageRequest);
		}
		return feedRepository.findActiveCursorPageByPlaceId(
				teamId, placeId, feedTypeName, cursor.createdAt(), cursor.feedId(), pageRequest);
	}

	private FeedTypeFilter parseFeedType(String type) {
		String normalized = blankToNull(type);
		if (normalized == null || "ALL".equalsIgnoreCase(normalized)) {
			return FeedTypeFilter.ALL;
		}
		try {
			return FeedTypeFilter.valueOf(normalized.toUpperCase());
		} catch (RuntimeException e) {
			throw new BusinessException(ErrorCode.FEED001, e);
		}
	}

	private FeedResponseDTO.FeedReactionResponseDTO reactionResponse(Long feedId, FeedReactionType myReaction) {
		return FeedResponseDTO.FeedReactionResponseDTO.builder()
				.feedId(feedId)
				.myReaction(myReaction)
				.reactionSummary(reactionSummary(feedId))
				.build();
	}

	private FeedReactionType myReaction(Long feedId, Long teamMemberId) {
		return feedReactionRepository.findByFeedIdAndTeamMemberId(feedId, teamMemberId)
				.map(FeedReaction::getReactionType)
				.orElse(null);
	}

	private Map<Long, FeedReactionType> myReactions(List<Long> feedIds, Long teamMemberId) {
		if (feedIds.isEmpty()) {
			return Map.of();
		}
		return feedReactionRepository.findMyReactions(feedIds, teamMemberId).stream()
				.collect(Collectors.toMap(row -> (Long) row[0], row -> (FeedReactionType) row[1]));
	}

	private FeedResponseDTO.ReactionSummaryDTO reactionSummary(Long feedId) {
		List<Object[]> rows = feedReactionRepository.countByReactionType(feedId);
		return toReactionSummary(rows);
	}

	private Map<Long, FeedResponseDTO.ReactionSummaryDTO> reactionSummaries(List<Long> feedIds) {
		if (feedIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, List<Object[]>> rowsByFeedId = new HashMap<>();
		for (Object[] row : feedReactionRepository.countByReactionTypeByFeedIds(feedIds)) {
			rowsByFeedId.computeIfAbsent((Long) row[0], ignored -> new ArrayList<>())
					.add(new Object[] { row[1], row[2] });
		}
		Map<Long, FeedResponseDTO.ReactionSummaryDTO> result = new HashMap<>();
		for (Long feedId : feedIds) {
			result.put(feedId, toReactionSummary(rowsByFeedId.getOrDefault(feedId, List.of())));
		}
		return result;
	}

	private FeedResponseDTO.ReactionSummaryDTO toReactionSummary(List<Object[]> rows) {
		List<FeedResponseDTO.ReactionCountDTO> items = rows.stream()
				.map(row -> FeedResponseDTO.ReactionCountDTO.builder()
						.reactionType((FeedReactionType) row[0])
						.count((Long) row[1])
						.build())
				.sorted(Comparator.comparing(item -> item.getReactionType().ordinal()))
				.toList();
		long totalCount = items.stream()
				.mapToLong(FeedResponseDTO.ReactionCountDTO::getCount)
				.sum();
		return FeedResponseDTO.ReactionSummaryDTO.builder()
				.totalCount(totalCount)
				.items(items)
				.build();
	}

	private FeedResponseDTO.ReactionSummaryDTO emptyReactionSummary() {
		return FeedResponseDTO.ReactionSummaryDTO.builder()
				.totalCount(0)
				.items(List.of())
				.build();
	}

	private int normalizePageSize(Integer size) {
		if (size == null) {
			return DEFAULT_PAGE_SIZE;
		}
		if (size < 1) {
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

	private String encodeCursor(LocalDateTime createdAt, Long feedId) {
		return createdAt + "_" + feedId;
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}

	private record Cursor(LocalDateTime createdAt, Long feedId) {
	}

	private enum FeedTypeFilter {
		ALL,
		GENERAL,
		DAILY_QUESTION;

		private String queryValue() {
			return this == ALL ? null : name();
		}
	}
}
