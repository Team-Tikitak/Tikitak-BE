package kusitms.spin.tikitak.service.media;

import kusitms.spin.tikitak.domain.media.entity.ObjectDeleteOutbox;
import kusitms.spin.tikitak.domain.media.enums.ObjectDeleteStatus;
import kusitms.spin.tikitak.global.config.R2Properties;
import kusitms.spin.tikitak.repository.media.ObjectDeleteOutboxRepository;
import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ObjectDeleteOutboxProcessorTest extends UnitTest {

	private static final Long OUTBOX_ID = 1L;
	private static final Long MEDIA_ID = 10L;
	private static final String BUCKET_NAME = "test-bucket";
	private static final String OBJECT_KEY = "feed/image.jpg";

	@Mock
	private ObjectDeleteOutboxRepository objectDeleteOutboxRepository;

	@Mock
	private R2Properties r2Properties;

	@Mock
	private S3Client s3Client;

	private ObjectDeleteOutboxProcessor processor;

	@BeforeEach
	void setUp() {
		processor = new ObjectDeleteOutboxProcessor(
				objectDeleteOutboxRepository,
				r2Properties,
				Optional.of(s3Client)
		);
	}

	@Test
	@DisplayName("R2 객체 삭제에 성공하면 outbox row를 삭제한다")
	void processDeletesOutboxWhenObjectDeleteSucceeds() {
		ObjectDeleteOutbox deleteRequest = deleteRequest(OBJECT_KEY);
		when(objectDeleteOutboxRepository.findById(OUTBOX_ID)).thenReturn(Optional.of(deleteRequest));

		processor.process(OUTBOX_ID);

		ArgumentCaptor<DeleteObjectRequest> requestCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
		verify(s3Client).deleteObject(requestCaptor.capture());
		assertThat(requestCaptor.getValue().bucket()).isEqualTo(BUCKET_NAME);
		assertThat(requestCaptor.getValue().key()).isEqualTo(OBJECT_KEY);
		verify(objectDeleteOutboxRepository).delete(deleteRequest);
	}

	@Test
	@DisplayName("R2 client가 없으면 outbox row를 실패 상태로 남긴다")
	void processMarksFailedWhenS3ClientIsMissing() {
		processor = new ObjectDeleteOutboxProcessor(
				objectDeleteOutboxRepository,
				r2Properties,
				Optional.empty()
		);
		ObjectDeleteOutbox deleteRequest = deleteRequest(OBJECT_KEY);
		when(objectDeleteOutboxRepository.findById(OUTBOX_ID)).thenReturn(Optional.of(deleteRequest));

		processor.process(OUTBOX_ID);

		assertThat(deleteRequest.getStatus()).isEqualTo(ObjectDeleteStatus.FAILED);
		assertThat(deleteRequest.getRetryCount()).isEqualTo(1);
		assertThat(deleteRequest.getLastError()).isEqualTo("S3 client is not configured");
		verify(s3Client, never()).deleteObject(org.mockito.ArgumentMatchers.any(DeleteObjectRequest.class));
	}

	@Test
	@DisplayName("최대 재시도 횟수에 도달하면 outbox row를 소진 상태로 남긴다")
	void processMarksExhaustedWhenRetryCountReachesLimit() {
		processor = new ObjectDeleteOutboxProcessor(
				objectDeleteOutboxRepository,
				r2Properties,
				Optional.empty()
		);
		ObjectDeleteOutbox deleteRequest = ObjectDeleteOutbox.builder()
				.id(OUTBOX_ID)
				.bucket(BUCKET_NAME)
				.objectKey(OBJECT_KEY)
				.mediaId(MEDIA_ID)
				.status(ObjectDeleteStatus.FAILED)
				.retryCount(ObjectDeleteOutbox.MAX_RETRY_COUNT - 1)
				.build();
		when(objectDeleteOutboxRepository.findById(OUTBOX_ID)).thenReturn(Optional.of(deleteRequest));

		processor.process(OUTBOX_ID);

		assertThat(deleteRequest.getStatus()).isEqualTo(ObjectDeleteStatus.EXHAUSTED);
		assertThat(deleteRequest.getRetryCount()).isEqualTo(ObjectDeleteOutbox.MAX_RETRY_COUNT);
		assertThat(deleteRequest.getLastError()).isEqualTo("S3 client is not configured");
	}

	private ObjectDeleteOutbox deleteRequest(String objectKey) {
		return ObjectDeleteOutbox.builder()
				.id(OUTBOX_ID)
				.bucket(BUCKET_NAME)
				.objectKey(objectKey)
				.mediaId(MEDIA_ID)
				.status(ObjectDeleteStatus.PENDING)
				.build();
	}
}
