package kusitms.spin.tikitak.service.home.dto;

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
}
