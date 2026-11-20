package kusitms.spin.tikitak.repository.feed;

import kusitms.spin.tikitak.domain.feed.entity.Feed;

import java.util.List;

/**
 * 피드 목록 동적 쿼리(QueryDSL). 구현은 {@link FeedRepositoryCustomImpl}.
 * docs/decisions/2026-11-19-querydsl-feed-list.md
 */
public interface FeedRepositoryCustom {

	/**
	 * 조건에 맞는 활성 피드를 createdAt desc, id desc 순으로 최대 limit개 반환한다.
	 * teamMember, teamMember.member, place, question을 함께 가져오고 컬렉션(images, tags)은 가져오지 않는다.
	 */
	List<Feed> findActivePage(FeedListCondition condition, int limit);

	/**
	 * 조건에 맞는 활성 피드 수. 커서는 무시한다.
	 */
	long countActive(FeedListCondition condition);
}
