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
        List<Long> pendingMediaIds = mediaService.findExpiredPendingMediaIds(cutoff, BATCH_SIZE);
        List<Long> uploadedMediaIds = mediaService.findExpiredUploadedMediaIds(cutoff, BATCH_SIZE);

        int deletedPendingCount = deleteMedia(pendingMediaIds, true);
        int deletedUploadedCount = deleteMedia(uploadedMediaIds, false);

        log.info("Expired media cleanup completed. pendingTargetCount={}, pendingDeletedCount={}, uploadedTargetCount={}, uploadedDeletedCount={}",
                pendingMediaIds.size(), deletedPendingCount, uploadedMediaIds.size(), deletedUploadedCount);
    }

    private int deleteMedia(List<Long> mediaIds, boolean pending) {
        int deletedCount = 0;
        for (Long mediaId : mediaIds) {
            try {
                boolean deleted = pending
                        ? mediaService.deleteExpiredPendingMedia(mediaId)
                        : mediaService.deleteExpiredUploadedMedia(mediaId);
                if (deleted) {
                    deletedCount++;
                }
            } catch (Exception e) {
                log.error("Failed to clean up expired media. mediaId={}", mediaId, e);
            }
        }
        return deletedCount;
    }
}
