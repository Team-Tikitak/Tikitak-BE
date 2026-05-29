package kusitms.spin.tikitak.service.feed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class FeedCleanupBatchService {

	private static final int RETENTION_DAYS = 7;
	private static final int BATCH_SIZE = 100;
	private static final int MAX_BATCH_PAGES = 100;

	private final FeedCleanupService feedCleanupService;

	@Scheduled(cron = "0 30 4 * * *")
	public void hardDeleteExpiredFeeds() {
		LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);
		int targetCount = 0;
		int deletedCount = 0;

		for (int page = 0; page < MAX_BATCH_PAGES; page++) {
			List<Long> feedIds = feedCleanupService.findExpiredDeletedFeedIds(cutoff, BATCH_SIZE);
			if (feedIds.isEmpty()) {
				break;
			}

			targetCount += feedIds.size();
			int deletedInBatch = 0;
			for (Long feedId : feedIds) {
				try {
					if (feedCleanupService.hardDeleteExpiredFeed(feedId, cutoff)) {
						deletedCount++;
						deletedInBatch++;
					}
				} catch (Exception e) {
					log.error("Failed to hard delete expired feed. feedId={}", feedId, e);
				}
			}

			if (feedIds.size() < BATCH_SIZE || deletedInBatch == 0) {
				break;
			}
		}

		log.info("Expired feed cleanup completed. targetCount={}, deletedCount={}", targetCount, deletedCount);
	}
}
