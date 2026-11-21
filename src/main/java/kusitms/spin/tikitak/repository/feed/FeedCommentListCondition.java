package kusitms.spin.tikitak.repository.feed;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 피드 댓글 목록 조회 조건. {@link FeedCommentRepositoryCustom#findActivePage}가 쓴다.
 *
 * @param feedId 조회할 피드 ID (필수)
 * @param feedImageId 피드 이미지 ID. null이면 이미지 필터 없음(피드 전체 댓글)
 * @param cursorCreatedAt 커서 댓글의 createdAt. cursorCommentId와 둘 다 null이거나 둘 다 non-null
 * @param cursorCommentId 커서 댓글의 ID
 */
public record FeedCommentListCondition(
		Long feedId,
		Long feedImageId,
		LocalDateTime cursorCreatedAt,
		Long cursorCommentId
) {

	public FeedCommentListCondition {
		Objects.requireNonNull(feedId, "feedId must not be null");
		if ((cursorCreatedAt == null) != (cursorCommentId == null)) {
			throw new IllegalArgumentException("cursorCreatedAt and cursorCommentId must be both null or both non-null");
		}
	}

	public boolean hasCursor() {
		return cursorCreatedAt != null;
	}
}
