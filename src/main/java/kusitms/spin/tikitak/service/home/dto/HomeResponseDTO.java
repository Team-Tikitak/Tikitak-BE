package kusitms.spin.tikitak.service.home.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kusitms.spin.tikitak.service.feed.dto.FeedResponseDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

public class HomeResponseDTO {

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class BestAttendanceResponse {
		private int month;
		private List<BestAttendanceMemberDTO> members;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class BestAttendanceMemberDTO {
		private int rank;
		private Long teamMemberId;
		private String nickname;
		@Schema(
				description = "멤버 프로필 이미지 URL. profile_avatar preset이 적용됩니다.",
				example = "https://media.tikitak.space/media/profile-image/8b2e58f0-8e24-4e34-91b0-87dc86d1892a.jpg?preset=profile_avatar"
		)
		private String profileImgUrl;
		private long tagCount;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class EveryonePickResponse {
		private int month;
		private List<FeedResponseDTO.FeedListItemDTO> picks;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class EveryonePickThumbnailResponse {
		private int month;
		private Long feedId;
		@Schema(
				description = "모두의 PICK 1위 피드 썸네일 이미지 URL. 피드가 3개 미만이면 null입니다. feed_thumb preset이 적용됩니다.",
				example = "https://media.tikitak.space/media/feed-image/8b2e58f0-8e24-4e34-91b0-87dc86d1892a.jpg?preset=feed_thumb"
		)
		private String thumbnailImageUrl;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class AllTaggedResponse {
		private int month;
		private List<FeedResponseDTO.FeedListItemDTO> feeds;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class CombinationResponse {
		private int month;
		private List<FeedResponseDTO.TaggedMemberDTO> combination;
		private List<FeedResponseDTO.FeedListItemDTO> feeds;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class RegionResponse {
		private int month;
		private List<RegionItemDTO> regions;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class RegionItemDTO {
		private String region;
		private long feedCount;
		@Schema(description = "지역 대표 썸네일의 피드 ID")
		private Long feedId;
		@Schema(
				description = "지역 대표 썸네일 이미지 URL. feed_thumb preset이 적용됩니다.",
				example = "https://media.tikitak.space/media/feed-image/8b2e58f0-8e24-4e34-91b0-87dc86d1892a.jpg?preset=feed_thumb"
		)
		private String thumbnailImageUrl;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class RecommendedPlacesResponse {
		private int month;
		private List<RecommendedPlaceItemDTO> places;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class RecommendedPlaceItemDTO {
		private String name;
		private String curation;
		@Schema(
				description = "추천 장소 카드 이미지 URL. place_card preset이 적용됩니다.",
				example = "https://media.tikitak.space/recommend_place/sample.jpg?preset=place_card"
		)
		private String imageUrl;
		private String kakaoMapUrl;
	}
}
