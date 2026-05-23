package kusitms.spin.tikitak.repository.dailyquestion;

import kusitms.spin.tikitak.domain.media.entity.Media;

import java.util.Optional;
import java.util.UUID;

public interface DailyQuestionMediaRepository {

	Optional<Media> findByPublicIdForUpdate(UUID publicId);
}
