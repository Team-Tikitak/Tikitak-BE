package kusitms.spin.tikitak.repository.media;

import kusitms.spin.tikitak.domain.media.entity.Media;
import kusitms.spin.tikitak.domain.media.enums.MediaPurpose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MediaRepository extends JpaRepository<Media, Long> {

    List<Media> findByMemberIdAndPurpose(Long memberId, MediaPurpose purpose);
}
