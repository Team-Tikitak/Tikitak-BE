package kusitms.spin.tikitak.repository.feed;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import kusitms.spin.tikitak.domain.feed.entity.FeedComment;
import kusitms.spin.tikitak.domain.feed.entity.QFeedComment;
import kusitms.spin.tikitak.domain.feed.entity.QFeedImage;
import kusitms.spin.tikitak.domain.team.entity.QTeamMember;

import java.time.LocalDateTime;
import java.util.List;

/**
 * {@link FeedCommentRepositoryCustom}의 QueryDSL 구현. 공용 {@link JPAQueryFactory} Bean을 주입받는다.
 */
public class FeedCommentRepositoryCustomImpl implements FeedCommentRepositoryCustom {

	private static final QFeedComment feedComment = QFeedComment.feedComment;
	private static final QTeamMember teamMember = QTeamMember.teamMember;
	private static final QFeedImage feedImage = QFeedImage.feedImage;

	private final JPAQueryFactory queryFactory;

	public FeedCommentRepositoryCustomImpl(JPAQueryFactory queryFactory) {
		this.queryFactory = queryFactory;
	}

	@Override
	public List<FeedComment> findActivePage(FeedCommentListCondition condition, int limit) {
		return queryFactory
				.selectFrom(feedComment)
				.join(feedComment.teamMember, teamMember).fetchJoin()
				.join(feedComment.feedImage, feedImage).fetchJoin()
				.where(
						feedComment.feed.id.eq(condition.feedId()),
						feedComment.deleted.isFalse(),
						feedImageId(condition.feedImageId()),
						cursor(condition))
				.orderBy(feedComment.createdAt.asc(), feedComment.id.asc())
				.limit(limit)
				.fetch();
	}

	private BooleanExpression feedImageId(Long feedImageId) {
		return feedImageId == null ? null : feedComment.feedImage.id.eq(feedImageId);
	}

	/** 오름차순 정렬이므로 커서 다음 페이지는 커서보다 뒤(gt)다. */
	private BooleanExpression cursor(FeedCommentListCondition condition) {
		if (!condition.hasCursor()) {
			return null;
		}
		LocalDateTime cursorCreatedAt = condition.cursorCreatedAt();
		return feedComment.createdAt.gt(cursorCreatedAt)
				.or(feedComment.createdAt.eq(cursorCreatedAt).and(feedComment.id.gt(condition.cursorCommentId())));
	}
}
