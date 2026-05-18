package kusitms.spin.tikitak.repository.feed;

import kusitms.spin.tikitak.domain.feed.entity.FeedImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FeedImageRepository extends JpaRepository<FeedImage, Long> {

	Optional<FeedImage> findByIdAndFeedId(Long id, Long feedId);
}
