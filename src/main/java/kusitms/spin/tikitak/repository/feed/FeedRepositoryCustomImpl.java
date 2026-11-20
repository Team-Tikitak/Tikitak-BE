package kusitms.spin.tikitak.repository.feed;

import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import kusitms.spin.tikitak.domain.feed.entity.Feed;
import kusitms.spin.tikitak.domain.feed.entity.QFeed;
import kusitms.spin.tikitak.domain.feed.entity.QFeedTag;
import kusitms.spin.tikitak.domain.feed.enums.FeedType;
import kusitms.spin.tikitak.domain.member.entity.QMember;
import kusitms.spin.tikitak.domain.place.entity.QPlace;
import kusitms.spin.tikitak.domain.question.entity.QQuestion;
import kusitms.spin.tikitak.domain.team.entity.QTeamMember;

import java.time.LocalDateTime;
import java.util.List;

/**
 * {@link FeedRepositoryCustom}의 QueryDSL 구현. 공용 {@link JPAQueryFactory} Bean을 주입받는다.
 * 목록과 count는 {@link #filters(FeedListCondition)}의 where 술어를 공유한다.
 */
public class FeedRepositoryCustomImpl implements FeedRepositoryCustom {

	private static final QFeed feed = QFeed.feed;
	private static final QFeedTag feedTag = QFeedTag.feedTag;
	private static final QTeamMember teamMember = QTeamMember.teamMember;
	private static final QMember member = QMember.member;
	private static final QPlace place = QPlace.place;
	private static final QQuestion question = QQuestion.question;

	private final JPAQueryFactory queryFactory;

	public FeedRepositoryCustomImpl(JPAQueryFactory queryFactory) {
		this.queryFactory = queryFactory;
	}

	@Override
	public List<Feed> findActivePage(FeedListCondition condition, int limit) {
		return queryFactory
				.selectFrom(feed)
				.join(feed.teamMember, teamMember).fetchJoin()
				.join(teamMember.member, member).fetchJoin()
				.leftJoin(feed.place, place).fetchJoin()
				.leftJoin(feed.question, question).fetchJoin()
				.where(filters(condition))
				.where(cursor(condition))
				.orderBy(feed.createdAt.desc(), feed.id.desc())
				.limit(limit)
				.fetch();
	}

	@Override
	public long countActive(FeedListCondition condition) {
		Long count = queryFactory
				.select(feed.id.countDistinct())
				.from(feed)
				.leftJoin(feed.place, place)
				.where(filters(condition))
				.fetchOne();
		return count == null ? 0L : count;
	}

	/**
	 * 목록과 count가 공유하는 where 술어. null 항목은 QueryDSL이 무시한다.
	 * place 술어는 호출하는 쿼리가 {@code feed.place}를 {@link #place} 별칭으로 join해 둔 것을 전제로 한다.
	 */
	private Predicate[] filters(FeedListCondition condition) {
		return new Predicate[] {
				feed.team.id.eq(condition.teamId()),
				feed.deletedAt.isNull(),
				feedType(condition.feedType()),
				condition.placeId() == null ? null : place.externalPlaceId.eq(condition.placeId()),
				condition.region() == null ? null : place.region.eq(condition.region()),
				taggedWithAll(condition.taggedTeamMemberIds())
		};
	}

	private BooleanExpression feedType(FeedType feedType) {
		if (feedType == null) {
			return null;
		}
		return switch (feedType) {
			case GENERAL -> feed.question.isNull();
			case DAILY_QUESTION -> feed.question.isNotNull();
		};
	}

	/** 요청한 팀 멤버가 모두 태그된 피드(AND). */
	private BooleanExpression taggedWithAll(List<Long> taggedTeamMemberIds) {
		if (taggedTeamMemberIds.isEmpty()) {
			return null;
		}
		return feed.id.in(
				JPAExpressions
						.select(feedTag.feed.id)
						.from(feedTag)
						.where(feedTag.teamMember.id.in(taggedTeamMemberIds))
						.groupBy(feedTag.feed.id)
						.having(feedTag.teamMember.id.countDistinct().eq((long) taggedTeamMemberIds.size())));
	}

	private BooleanExpression cursor(FeedListCondition condition) {
		if (!condition.hasCursor()) {
			return null;
		}
		LocalDateTime cursorCreatedAt = condition.cursorCreatedAt();
		return feed.createdAt.lt(cursorCreatedAt)
				.or(feed.createdAt.eq(cursorCreatedAt).and(feed.id.lt(condition.cursorFeedId())));
	}
}
