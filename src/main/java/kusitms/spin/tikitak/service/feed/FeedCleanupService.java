package kusitms.spin.tikitak.service.feed;

import kusitms.spin.tikitak.domain.feed.entity.Feed;
import kusitms.spin.tikitak.domain.feed.entity.FeedImage;
import kusitms.spin.tikitak.domain.media.entity.Media;
import kusitms.spin.tikitak.domain.media.entity.ObjectDeleteOutbox;
import kusitms.spin.tikitak.global.config.R2Properties;
import kusitms.spin.tikitak.repository.feed.FeedRepository;
import kusitms.spin.tikitak.repository.media.MediaRepository;
import kusitms.spin.tikitak.service.media.ObjectDeleteOutboxProcessor;
import kusitms.spin.tikitak.service.media.ObjectDeleteOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class FeedCleanupService {

	private final FeedRepository feedRepository;
	private final MediaRepository mediaRepository;
	private final ObjectDeleteOutboxService objectDeleteOutboxService;
	private final ObjectDeleteOutboxProcessor objectDeleteOutboxProcessor;
	private final R2Properties r2Properties;

	public List<Long> findExpiredDeletedFeedIds(LocalDateTime cutoff, int limit) {
		return feedRepository.findExpiredDeletedFeedIds(cutoff, PageRequest.of(0, limit));
	}

	@Transactional
	public boolean hardDeleteExpiredFeed(Long feedId, LocalDateTime cutoff) {
		Feed feed = feedRepository.findDeletedForHardDelete(feedId)
				.orElse(null);
		if (feed == null || feed.getDeletedAt() == null || feed.getDeletedAt().isAfter(cutoff)) {
			return false;
		}

		List<Media> medias = feed.getImages().stream()
				.map(FeedImage::getMedia)
				.filter(Objects::nonNull)
				.distinct()
				.toList();
		List<ObjectDeleteOutbox> deleteRequests = objectDeleteOutboxService.saveAll(medias.stream()
				.map(media -> ObjectDeleteOutbox.builder()
						.bucket(r2Properties.getBucketName())
						.objectKey(media.getKey())
						.mediaId(media.getId())
						.build())
				.toList());

		feedRepository.delete(feed);
		feedRepository.flush();
		mediaRepository.deleteAll(medias);
		registerObjectDeletionAfterCommit(deleteRequests);
		return true;
	}

	private void registerObjectDeletionAfterCommit(List<ObjectDeleteOutbox> deleteRequests) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			deleteRequests.forEach(this::processDeleteRequest);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				deleteRequests.forEach(FeedCleanupService.this::processDeleteRequest);
			}
		});
	}

	private void processDeleteRequest(ObjectDeleteOutbox deleteRequest) {
		try {
			objectDeleteOutboxProcessor.process(deleteRequest.getId());
		} catch (Exception e) {
			log.error("Failed to process feed media delete request. outboxId={}, mediaId={}",
					deleteRequest.getId(), deleteRequest.getMediaId(), e);
		}
	}
}
