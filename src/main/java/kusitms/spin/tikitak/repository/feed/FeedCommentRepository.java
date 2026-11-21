package kusitms.spin.tikitak.repository.feed;

import kusitms.spin.tikitak.domain.feed.entity.FeedComment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FeedCommentRepository extends JpaRepository<FeedComment, Long>, FeedCommentRepositoryCustom {

	long countByFeedIdAndDeletedFalse(Long feedId);

	@Query("""
			select fc.feed.id, count(fc)
			from FeedComment fc
			where fc.feed.id in :feedIds
				and fc.deleted = false
			group by fc.feed.id
			""")
	List<Object[]> countByFeedIds(@Param("feedIds") Collection<Long> feedIds);

	@Query("""
			select distinct fc.teamMember.member.id
			from FeedComment fc
			where fc.feedImage.id = :feedImageId
				and fc.positionX = :positionX
				and fc.positionY = :positionY
				and fc.teamMember.status = 'ACTIVE'
				and fc.deleted = false
			""")
	List<Long> findDistinctMemberIdsByPosition(
			@Param("feedImageId") Long feedImageId,
			@Param("positionX") BigDecimal positionX,
			@Param("positionY") BigDecimal positionY
	);

	@EntityGraph(attributePaths = { "teamMember", "feedImage" })
	@Query("""
			select fc
			from FeedComment fc
			where fc.id = :commentId
				and fc.feed.id = :feedId
				and fc.deleted = false
			""")
	Optional<FeedComment> findActiveByIdAndFeedId(
			@Param("commentId") Long commentId,
			@Param("feedId") Long feedId
	);
}
