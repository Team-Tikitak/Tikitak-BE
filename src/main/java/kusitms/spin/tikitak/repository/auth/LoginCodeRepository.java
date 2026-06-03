package kusitms.spin.tikitak.repository.auth;

import kusitms.spin.tikitak.domain.auth.LoginCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LoginCodeRepository extends JpaRepository<LoginCode, Long> {
	Optional<LoginCode> findByCode(String code);
}
