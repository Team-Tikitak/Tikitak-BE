package kusitms.spin.tikitak.service.media;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class MediaCleanupBatchService {

    private static final int RETENTION_DAYS = 7;
    private static final int BATCH_SIZE = 100;

    private final MediaService mediaService;

    @Scheduled(cron = "0 0 4 * * *")
    public void deleteExpiredPendingMedia() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);
        List<Long> mediaIds = mediaService.findExpiredPendingMediaIds(cutoff, BATCH_SIZE);

        int deletedCount = 0;
        for (Long mediaId : mediaIds) {
            try {
                if (mediaService.deleteExpiredPendingMedia(mediaId)) {
                    deletedCount++;
                }
            } catch (Exception e) {
                log.error("Failed to clean up expired pending media. mediaId={}", mediaId, e);
            }
        }

        log.info("Expired pending media cleanup completed. targetCount={}, deletedCount={}",
                mediaIds.size(), deletedCount);
    }
}
