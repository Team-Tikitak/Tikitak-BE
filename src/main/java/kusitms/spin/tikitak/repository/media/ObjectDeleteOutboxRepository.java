package kusitms.spin.tikitak.repository.media;

import kusitms.spin.tikitak.domain.media.entity.ObjectDeleteOutbox;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ObjectDeleteOutboxRepository extends JpaRepository<ObjectDeleteOutbox, Long> {

	@Query(value = """
			select id
			from object_delete_outbox
			where status in ('PENDING', 'FAILED')
			order by updated_at, id
			""", nativeQuery = true)
	List<Long> findRetryTargetIds(Pageable pageable);
}
