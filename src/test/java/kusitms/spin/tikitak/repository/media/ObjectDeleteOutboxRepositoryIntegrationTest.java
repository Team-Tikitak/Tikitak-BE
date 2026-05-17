package kusitms.spin.tikitak.repository.media;

import kusitms.spin.tikitak.domain.media.entity.ObjectDeleteOutbox;
import kusitms.spin.tikitak.domain.media.enums.ObjectDeleteStatus;
import kusitms.spin.tikitak.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ObjectDeleteOutboxRepositoryIntegrationTest extends IntegrationTest {

	@Autowired
	private ObjectDeleteOutboxRepository objectDeleteOutboxRepository;

	@Test
	@DisplayName("retry targets include pending rows and failed rows below max retry count")
	void findRetryTargetIds() {
		ObjectDeleteOutbox pending = persist(deleteRequest("pending", ObjectDeleteStatus.PENDING, 0));
		ObjectDeleteOutbox failedBelowLimit = persist(deleteRequest("failed-below", ObjectDeleteStatus.FAILED, 4));
		ObjectDeleteOutbox failedAtLimit = persist(deleteRequest("failed-limit", ObjectDeleteStatus.FAILED, 5));
		ObjectDeleteOutbox exhausted = persist(deleteRequest("exhausted", ObjectDeleteStatus.EXHAUSTED, 5));
		flushAndClear();

		List<Long> targetIds = objectDeleteOutboxRepository.findRetryTargetIds(
				ObjectDeleteStatus.PENDING,
				ObjectDeleteStatus.FAILED,
				ObjectDeleteOutbox.MAX_RETRY_COUNT,
				PageRequest.of(0, 50)
		);

		assertThat(targetIds)
				.contains(pending.getId(), failedBelowLimit.getId())
				.doesNotContain(failedAtLimit.getId(), exhausted.getId());
	}

	private ObjectDeleteOutbox deleteRequest(String suffix, ObjectDeleteStatus status, int retryCount) {
		return ObjectDeleteOutbox.builder()
				.bucket("test-bucket")
				.objectKey("feed/" + suffix + ".jpg")
				.mediaId((long) suffix.hashCode())
				.status(status)
				.retryCount(retryCount)
				.build();
	}
}
