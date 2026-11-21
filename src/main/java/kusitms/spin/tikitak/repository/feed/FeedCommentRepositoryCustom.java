package kusitms.spin.tikitak.repository.feed;

import kusitms.spin.tikitak.domain.feed.entity.FeedComment;

import java.util.List;

/**
 * 피드 댓글 목록 동적 쿼리(QueryDSL). 구현은 {@link FeedCommentRepositoryCustomImpl}.
 * docs/decisions/2026-11-19-querydsl-feed-list.md
 */
public interface FeedCommentRepositoryCustom {

	/**
	 * 조건에 맞는 삭제되지 않은 댓글을 createdAt asc, id asc 순으로 최대 limit개 반환한다.
	 * 커서가 있으면 커서 댓글 다음(createdAt이 더 늦거나, 같으면 id가 더 큰) 댓글만 반환한다.
	 * teamMember, feedImage를 함께 가져오고 teamMember.member는 가져오지 않는다.
	 */
	List<FeedComment> findActivePage(FeedCommentListCondition condition, int limit);
}
