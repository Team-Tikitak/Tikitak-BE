package kusitms.spin.tikitak.service.media;

import kusitms.spin.tikitak.domain.media.enums.ObjectDeleteStatus;
import kusitms.spin.tikitak.repository.media.ObjectDeleteOutboxRepository;
import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ObjectDeleteOutboxRetryBatchServiceTest extends UnitTest {

	@Mock
	private ObjectDeleteOutboxRepository objectDeleteOutboxRepository;

	@Mock
	private ObjectDeleteOutboxProcessor objectDeleteOutboxProcessor;

	private ObjectDeleteOutboxRetryBatchService batchService;

	@BeforeEach
	void setUp() {
		batchService = new ObjectDeleteOutboxRetryBatchService(
				objectDeleteOutboxRepository,
				objectDeleteOutboxProcessor
		);
	}

	@Test
	@DisplayName("삭제 outbox를 30분 배치 기준 최대 50건까지 재시도한다")
	void retryObjectDeletes() {
		when(objectDeleteOutboxRepository.findRetryTargetIds(
				eq(List.of(ObjectDeleteStatus.PENDING, ObjectDeleteStatus.FAILED)),
				eq(PageRequest.of(0, 50))
		)).thenReturn(List.of(1L, 2L));

		batchService.retryObjectDeletes();

		verify(objectDeleteOutboxProcessor).process(1L);
		verify(objectDeleteOutboxProcessor).process(2L);
	}
}
