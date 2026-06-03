package kusitms.spin.tikitak.service.map.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
		@Schema(
				description = "지도 핀 대표 썸네일 이미지 URL. feed_thumb preset이 적용됩니다.",
				example = "https://media.tikitak.space/media/feed-image/8b2e58f0-8e24-4e34-91b0-87dc86d1892a.jpg?preset=feed_thumb"
		)
		private String thumbnailUrl;
		@Schema(
				description = "지도 핀 상세 전환 애니메이션용 저용량 이미지 URL. 이미지 최적화가 활성화된 R2 이미지인 경우 feed_hero_preview preset이 적용됩니다.",
				example = "https://media.tikitak.space/media/feed-image/8b2e58f0-8e24-4e34-91b0-87dc86d1892a.jpg?preset=feed_hero_preview"
		)
		private String heroPreviewUrl;
		private long feedCount;
	}
}
