package kusitms.spin.tikitak.service.media;

import kusitms.spin.tikitak.domain.media.entity.ObjectDeleteOutbox;
import kusitms.spin.tikitak.repository.media.ObjectDeleteOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ObjectDeleteOutboxService {

	private final ObjectDeleteOutboxRepository objectDeleteOutboxRepository;

	@Transactional
	public List<ObjectDeleteOutbox> saveAll(List<ObjectDeleteOutbox> deleteRequests) {
		return objectDeleteOutboxRepository.saveAllAndFlush(deleteRequests);
	}
}
