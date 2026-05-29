package kusitms.spin.tikitak.service.home.dto;

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
		private String imageUrl;
		private String kakaoMapUrl;
	}
}
