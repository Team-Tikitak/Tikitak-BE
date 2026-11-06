package kusitms.spin.tikitak.domain.place.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kusitms.spin.tikitak.domain.common.BaseCreatedTimeEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Entity
@Table(name = "place")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Place extends BaseCreatedTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "external_place_id", length = 100, unique = true)
	private String externalPlaceId;

	@Column(columnDefinition = "text")
	private String name;

	@Column(precision = 18, scale = 10)
	private BigDecimal latitude;

	@Column(precision = 18, scale = 10)
	private BigDecimal longitude;

	@Column(columnDefinition = "text")
	private String address;

	@Column(columnDefinition = "text")
	private String region;

	public void updateRegion(String region) {
		this.region = region;
	}
}
