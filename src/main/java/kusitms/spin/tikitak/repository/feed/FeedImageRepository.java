package kusitms.spin.tikitak.repository.feed;

import kusitms.spin.tikitak.domain.feed.entity.FeedImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FeedImageRepository extends JpaRepository<FeedImage, Long> {

	Optional<FeedImage> findByIdAndFeedId(Long id, Long feedId);

	@Query("""
			select fi
			from FeedImage fi
			join fi.feed f
			where f.id in :feedIds
				and f.deletedAt is null
				and fi.orderIndex = 0
			""")
	List<FeedImage> findFirstActiveByFeedIds(@Param("feedIds") Collection<Long> feedIds);
}
