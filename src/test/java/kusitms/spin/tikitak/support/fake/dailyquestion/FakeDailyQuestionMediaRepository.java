package kusitms.spin.tikitak.support.fake.dailyquestion;

import kusitms.spin.tikitak.domain.media.entity.Media;
import kusitms.spin.tikitak.repository.dailyquestion.DailyQuestionMediaRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class FakeDailyQuestionMediaRepository implements DailyQuestionMediaRepository {

	private final Map<UUID, Media> store = new HashMap<>();

	public void save(Media media) {
		store.put(media.getPublicId(), media);
	}

	@Override
	public Optional<Media> findByPublicIdForUpdate(UUID publicId) {
		return Optional.ofNullable(store.get(publicId));
	}
}
