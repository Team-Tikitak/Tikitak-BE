package kusitms.spin.tikitak.repository.place;

import kusitms.spin.tikitak.domain.place.entity.Place;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface PlaceRepository extends JpaRepository<Place, Long> {

	Optional<Place> findByExternalPlaceId(String externalPlaceId);

	@Modifying(flushAutomatically = true)
	@Query(value = """
			insert into place (external_place_id, name, latitude, longitude, address, created_at)
			values (:externalPlaceId, :name, :latitude, :longitude, :address, current_timestamp)
			on conflict (external_place_id) do nothing
			""", nativeQuery = true)
	void insertIfAbsentByExternalPlaceId(
			@Param("externalPlaceId") String externalPlaceId,
			@Param("name") String name,
			@Param("latitude") BigDecimal latitude,
			@Param("longitude") BigDecimal longitude,
			@Param("address") String address
	);
}
