package kusitms.spin.tikitak.service.feed;

import kusitms.spin.tikitak.domain.feed.entity.Feed;
import kusitms.spin.tikitak.domain.feed.entity.FeedImage;
import kusitms.spin.tikitak.domain.media.entity.Media;
import kusitms.spin.tikitak.global.config.R2Properties;
import kusitms.spin.tikitak.repository.feed.FeedRepository;
import kusitms.spin.tikitak.repository.media.MediaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class FeedCleanupService {

	private final FeedRepository feedRepository;
	private final MediaRepository mediaRepository;
	private final R2Properties r2Properties;
	private final Optional<S3Client> s3Client;

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
		List<MediaObject> mediaObjects = medias.stream()
				.map(media -> new MediaObject(media.getId(), media.getKey()))
				.toList();

		feedRepository.delete(feed);
		feedRepository.flush();
		mediaRepository.deleteAll(medias);
		registerObjectDeletionAfterCommit(mediaObjects);
		return true;
	}

	private void registerObjectDeletionAfterCommit(List<MediaObject> mediaObjects) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			mediaObjects.forEach(this::deleteObject);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				mediaObjects.forEach(FeedCleanupService.this::deleteObject);
			}
		});
	}

	private void deleteObject(MediaObject mediaObject) {
		if (mediaObject.key() == null || mediaObject.key().isBlank()) {
			log.warn("Skipping feed media object deletion because key is blank. mediaId={}", mediaObject.mediaId());
			return;
		}
		if (s3Client.isEmpty()) {
			log.warn("Skipping feed media object deletion because S3 client is not configured. mediaId={}, key={}",
					mediaObject.mediaId(), mediaObject.key());
			return;
		}
		S3Client client = s3Client.get();
		try {
			DeleteObjectRequest request = DeleteObjectRequest.builder()
					.bucket(r2Properties.getBucketName())
					.key(mediaObject.key())
					.build();
			client.deleteObject(request);
		} catch (S3Exception e) {
			if (e.statusCode() == 404) {
				log.info("Feed media object already absent. mediaId={}, key={}", mediaObject.mediaId(), mediaObject.key());
				return;
			}
			log.error("Failed to delete feed media object after commit. mediaId={}, key={}",
					mediaObject.mediaId(), mediaObject.key(), e);
		} catch (Exception e) {
			log.error("Failed to delete feed media object after commit. mediaId={}, key={}",
					mediaObject.mediaId(), mediaObject.key(), e);
		}
	}

	private record MediaObject(Long mediaId, String key) {
	}
}
