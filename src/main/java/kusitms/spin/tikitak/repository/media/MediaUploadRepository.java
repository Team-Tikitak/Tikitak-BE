package kusitms.spin.tikitak.repository.media;

import jakarta.persistence.LockModeType;
import kusitms.spin.tikitak.domain.media.entity.MediaUpload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MediaUploadRepository extends JpaRepository<MediaUpload, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select mu from MediaUpload mu where mu.publicId = :publicId")
    Optional<MediaUpload> findByPublicIdForUpdate(@Param("publicId") UUID publicId);
}
