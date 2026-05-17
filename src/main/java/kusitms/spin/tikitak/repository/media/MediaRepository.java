package kusitms.spin.tikitak.repository.media;

import jakarta.persistence.LockModeType;
import kusitms.spin.tikitak.domain.media.entity.Media;
import kusitms.spin.tikitak.domain.media.enums.MediaPurpose;
import kusitms.spin.tikitak.domain.media.enums.MediaStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MediaRepository extends JpaRepository<Media, Long> {

    Optional<Media> findByPublicId(UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Media m where m.publicId = :publicId")
    Optional<Media> findByPublicIdForUpdate(@Param("publicId") UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Media m where m.id = :mediaId")
    Optional<Media> findByIdForUpdate(@Param("mediaId") Long mediaId);

    @Query("""
            select m.id
            from Media m
            where m.status = :status
              and m.createdAt < :cutoff
            order by m.createdAt asc, m.id asc
            """)
    List<Long> findExpiredMediaIds(
            @Param("status") MediaStatus status,
            @Param("cutoff") LocalDateTime cutoff,
            Pageable pageable
    );

    List<Media> findByMemberIdAndPurpose(Long memberId, MediaPurpose purpose);

    long countByTeamIdAndPurposeAndStatus(Long teamId, MediaPurpose purpose, MediaStatus status);
}
