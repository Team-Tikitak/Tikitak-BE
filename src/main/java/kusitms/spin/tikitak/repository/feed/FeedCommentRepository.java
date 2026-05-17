package kusitms.spin.tikitak.repository.feed;

import kusitms.spin.tikitak.domain.feed.entity.FeedComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface FeedCommentRepository extends JpaRepository<FeedComment, Long> {

	long countByFeedIdAndDeletedFalse(Long feedId);

	@Query("""
			select fc.feed.id, count(fc)
			from FeedComment fc
			where fc.feed.id in :feedIds
				and fc.deleted = false
			group by fc.feed.id
			""")
	List<Object[]> countByFeedIds(@Param("feedIds") Collection<Long> feedIds);
}
