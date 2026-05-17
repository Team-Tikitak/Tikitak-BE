package kusitms.spin.tikitak.service.media;

import kusitms.spin.tikitak.domain.media.enums.ObjectDeleteStatus;
import kusitms.spin.tikitak.repository.media.ObjectDeleteOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ObjectDeleteOutboxRetryBatchService {

	private static final int RETRY_BATCH_SIZE = 50;

	private final ObjectDeleteOutboxRepository objectDeleteOutboxRepository;
	private final ObjectDeleteOutboxProcessor objectDeleteOutboxProcessor;

	@Scheduled(cron = "0 */30 * * * *")
	public void retryObjectDeletes() {
		List<Long> targetIds = objectDeleteOutboxRepository.findRetryTargetIds(
				List.of(ObjectDeleteStatus.PENDING, ObjectDeleteStatus.FAILED),
				PageRequest.of(0, RETRY_BATCH_SIZE)
		);
		int processedCount = 0;
		for (Long targetId : targetIds) {
			try {
				objectDeleteOutboxProcessor.process(targetId);
				processedCount++;
			} catch (Exception e) {
				log.error("Failed to process object delete outbox. outboxId={}", targetId, e);
			}
		}
		if (!targetIds.isEmpty()) {
			log.info("Object delete outbox retry completed. targetCount={}, processedCount={}",
					targetIds.size(), processedCount);
		}
	}
}
