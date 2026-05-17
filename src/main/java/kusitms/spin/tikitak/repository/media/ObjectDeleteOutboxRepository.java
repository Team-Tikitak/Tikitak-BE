package kusitms.spin.tikitak.repository.media;

import kusitms.spin.tikitak.domain.media.entity.ObjectDeleteOutbox;
import kusitms.spin.tikitak.domain.media.enums.ObjectDeleteStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ObjectDeleteOutboxRepository extends JpaRepository<ObjectDeleteOutbox, Long> {

	@Query("""
			select o.id
			from ObjectDeleteOutbox o
			where o.status = :pendingStatus
				or (o.status = :failedStatus and o.retryCount < :maxRetryCount)
			order by o.updatedAt, o.id
			""")
	List<Long> findRetryTargetIds(
			@Param("pendingStatus") ObjectDeleteStatus pendingStatus,
			@Param("failedStatus") ObjectDeleteStatus failedStatus,
			@Param("maxRetryCount") int maxRetryCount,
			Pageable pageable
	);
}
