package kusitms.spin.tikitak.service.feed;

import kusitms.spin.tikitak.domain.feed.entity.Feed;
import kusitms.spin.tikitak.domain.feed.entity.FeedImage;
import kusitms.spin.tikitak.domain.media.entity.Media;
import kusitms.spin.tikitak.global.config.R2Properties;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.repository.feed.FeedRepository;
import kusitms.spin.tikitak.repository.media.MediaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
		if (feed == null || feed.getDeletedAt() == null || !feed.getDeletedAt().isBefore(cutoff)) {
			return false;
		}

		List<Media> medias = feed.getImages().stream()
				.map(FeedImage::getMedia)
				.filter(Objects::nonNull)
				.distinct()
				.toList();

		medias.forEach(this::deleteObject);
		feedRepository.delete(feed);
		feedRepository.flush();
		mediaRepository.deleteAll(medias);
		return true;
	}

	private void deleteObject(Media media) {
		if (media.getKey() == null || media.getKey().isBlank()) {
			throw new BusinessException(ErrorCode.FEED016);
		}
		S3Client client = s3Client.orElseThrow(() -> new BusinessException(ErrorCode.FEED016));
		try {
			DeleteObjectRequest request = DeleteObjectRequest.builder()
					.bucket(r2Properties.getBucketName())
					.key(media.getKey())
					.build();
			client.deleteObject(request);
		} catch (S3Exception e) {
			if (e.statusCode() == 404) {
				log.info("Feed media object already absent. mediaId={}, key={}", media.getId(), media.getKey());
				return;
			}
			throw new BusinessException(ErrorCode.FEED016, e);
		} catch (Exception e) {
			throw new BusinessException(ErrorCode.FEED016, e);
		}
	}
}
