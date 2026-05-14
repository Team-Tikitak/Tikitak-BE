package kusitms.spin.tikitak.service.team.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class TeamInvitationRequestDTO {

	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class JoinTeamRequestDTO {
		@NotBlank
		@Size(max = 30)
		private String nickname;
		private String profileImgUrl;
	}
}
