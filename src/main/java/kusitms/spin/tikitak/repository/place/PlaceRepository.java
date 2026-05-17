package kusitms.spin.tikitak.repository.place;

import kusitms.spin.tikitak.domain.place.entity.Place;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlaceRepository extends JpaRepository<Place, Long> {

	Optional<Place> findByExternalPlaceId(String externalPlaceId);
}
