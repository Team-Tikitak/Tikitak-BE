package kusitms.spin.tikitak.repository.auth;

import jakarta.persistence.LockModeType;
import kusitms.spin.tikitak.domain.auth.LoginCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LoginCodeRepository extends JpaRepository<LoginCode, Long> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT l FROM LoginCode l WHERE l.code = :code")
	Optional<LoginCode> findByCodeForUpdate(@Param("code") String code);
}
