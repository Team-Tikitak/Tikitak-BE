package kusitms.spin.tikitak.service.member;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class WithdrawnMemberProfileImageCleanupBatchService {

	private static final int RETENTION_DAYS = 7;
	private static final int BATCH_SIZE = 100;

	private final WithdrawnMemberProfileImageCleanupService cleanupService;

	@Scheduled(cron = "0 15 4 * * *")
	public void cleanUpWithdrawnMemberProfileImages() {
		LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);
		List<Long> targetIds = cleanupService.findCleanupTargetIds(cutoff, BATCH_SIZE);

		int cleanedCount = 0;
		for (Long targetId : targetIds) {
			try {
				if (cleanupService.cleanUpProfileImages(targetId, cutoff)) {
					cleanedCount++;
				}
			} catch (Exception e) {
				log.error("Failed to clean up withdrawn member profile images. memberId={}", targetId, e);
			}
		}

		if (!targetIds.isEmpty()) {
			log.info("Withdrawn member profile image cleanup completed. targetCount={}, cleanedCount={}",
					targetIds.size(), cleanedCount);
		}
	}
}
