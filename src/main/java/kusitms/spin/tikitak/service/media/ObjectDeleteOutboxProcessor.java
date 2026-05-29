package kusitms.spin.tikitak.service.media;

import kusitms.spin.tikitak.domain.media.entity.ObjectDeleteOutbox;
import kusitms.spin.tikitak.global.config.R2Properties;
import kusitms.spin.tikitak.repository.media.ObjectDeleteOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ObjectDeleteOutboxProcessor {

	private final ObjectDeleteOutboxRepository objectDeleteOutboxRepository;
	private final R2Properties r2Properties;
	private final Optional<S3Client> s3Client;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void process(Long deleteRequestId) {
		if (deleteRequestId == null) {
			return;
		}
		objectDeleteOutboxRepository.findById(deleteRequestId)
				.ifPresent(this::deleteObject);
	}

	private void deleteObject(ObjectDeleteOutbox deleteRequest) {
		if (deleteRequest.getObjectKey() == null || deleteRequest.getObjectKey().isBlank()) {
			failDeleteRequest(deleteRequest, "Object key is blank");
			log.warn("Skipping media object deletion because key is blank. mediaId={}", deleteRequest.getMediaId());
			return;
		}
		if (s3Client.isEmpty()) {
			failDeleteRequest(deleteRequest, "S3 client is not configured");
			log.warn("Skipping media object deletion because S3 client is not configured. mediaId={}, key={}",
					deleteRequest.getMediaId(), deleteRequest.getObjectKey());
			return;
		}

		try {
			DeleteObjectRequest request = DeleteObjectRequest.builder()
					.bucket(bucket(deleteRequest))
					.key(deleteRequest.getObjectKey())
					.build();
			s3Client.get().deleteObject(request);
			objectDeleteOutboxRepository.delete(deleteRequest);
		} catch (S3Exception e) {
			if (e.statusCode() == 404) {
				log.info("Media object already absent. mediaId={}, key={}",
						deleteRequest.getMediaId(), deleteRequest.getObjectKey());
				objectDeleteOutboxRepository.delete(deleteRequest);
				return;
			}
			failDeleteRequest(deleteRequest, e.getMessage());
			log.error("Failed to delete media object. mediaId={}, key={}",
					deleteRequest.getMediaId(), deleteRequest.getObjectKey(), e);
		} catch (Exception e) {
			failDeleteRequest(deleteRequest, e.getMessage());
			log.error("Failed to delete media object. mediaId={}, key={}",
					deleteRequest.getMediaId(), deleteRequest.getObjectKey(), e);
		}
	}

	private String bucket(ObjectDeleteOutbox deleteRequest) {
		if (deleteRequest.getBucket() != null && !deleteRequest.getBucket().isBlank()) {
			return deleteRequest.getBucket();
		}
		return r2Properties.getBucketName();
	}

	private void failDeleteRequest(ObjectDeleteOutbox deleteRequest, String lastError) {
		deleteRequest.markFailed(lastError);
	}
}
