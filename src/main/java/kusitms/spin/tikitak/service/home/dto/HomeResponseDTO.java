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
		private List<FeedResponseDTO.FeedListItemDTO> picks;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class AllTaggedResponse {
		private List<FeedResponseDTO.FeedListItemDTO> feeds;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class CombinationResponse {
		private List<FeedResponseDTO.TaggedMemberDTO> combination;
		private List<FeedResponseDTO.FeedListItemDTO> feeds;
	}
}
