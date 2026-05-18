package kusitms.spin.tikitak.service.map.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

public class MapResponseDTO {

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class MapPinListResponseDTO {
		private List<MapPinDTO> pins;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class MapPinDTO {
		private String placeId;
		private String name;
		private BigDecimal latitude;
		private BigDecimal longitude;
		private String address;
		private String thumbnailUrl;
		private long feedCount;
	}
}
