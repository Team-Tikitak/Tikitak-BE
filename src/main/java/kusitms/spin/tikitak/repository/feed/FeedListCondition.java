package kusitms.spin.tikitak.repository.feed;

import kusitms.spin.tikitak.domain.feed.enums.FeedType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 피드 목록 조회 조건. {@link FeedRepositoryCustom#findActivePage}와 {@link FeedRepositoryCustom#countActive}가 함께 쓴다.
 *
 * @param teamId 조회할 팀 ID (필수)
 * @param placeId 장소 외부 ID(place.externalPlaceId). null이면 필터 없음
 * @param region 장소 지역(place.region). null이면 필터 없음
 * @param feedType 피드 유형. null이면 전체
 * @param taggedTeamMemberIds 모두 태그돼 있어야 하는 팀 멤버 ID(AND). null이면 빈 리스트로 바꾸며, 빈 리스트면 태그 필터 없음
 * @param cursorCreatedAt 커서 피드의 createdAt. cursorFeedId와 둘 다 null이거나 둘 다 non-null
 * @param cursorFeedId 커서 피드의 ID. countActive는 커서를 무시한다
 */
public record FeedListCondition(
		Long teamId,
		String placeId,
		String region,
		FeedType feedType,
		List<Long> taggedTeamMemberIds,
		LocalDateTime cursorCreatedAt,
		Long cursorFeedId
) {

	public FeedListCondition {
		Objects.requireNonNull(teamId, "teamId must not be null");
		taggedTeamMemberIds = taggedTeamMemberIds == null ? List.of() : List.copyOf(taggedTeamMemberIds);
		if ((cursorCreatedAt == null) != (cursorFeedId == null)) {
			throw new IllegalArgumentException("cursorCreatedAt and cursorFeedId must be both null or both non-null");
		}
	}

	public boolean hasCursor() {
		return cursorCreatedAt != null;
	}
}
