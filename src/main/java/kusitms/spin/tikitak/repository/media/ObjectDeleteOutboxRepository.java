package kusitms.spin.tikitak.repository.media;

import kusitms.spin.tikitak.domain.media.entity.ObjectDeleteOutbox;
import kusitms.spin.tikitak.domain.media.enums.ObjectDeleteStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ObjectDeleteOutboxRepository extends JpaRepository<ObjectDeleteOutbox, Long> {

	@Query("""
			select o.id
			from ObjectDeleteOutbox o
			where o.status in :statuses
			order by o.updatedAt, o.id
			""")
	List<Long> findRetryTargetIds(@Param("statuses") Collection<ObjectDeleteStatus> statuses, Pageable pageable);
}
