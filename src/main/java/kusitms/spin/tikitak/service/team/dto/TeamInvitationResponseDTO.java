package kusitms.spin.tikitak.service.team.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class TeamInvitationResponseDTO {

	@Getter
	@Builder
	@NoArgsConstructor(access = AccessLevel.PROTECTED)
	@AllArgsConstructor(access = AccessLevel.PROTECTED)
	public static class GenerateInviteLinkResponseDTO {
		private String inviteToken;
		private LocalDateTime expiresAt;
	}

	@Getter
	@Builder
	@NoArgsConstructor(access = AccessLevel.PROTECTED)
	@AllArgsConstructor(access = AccessLevel.PROTECTED)
	public static class InviteLinkPreviewResponseDTO {
		private Long teamId;
		private String teamName;
		private String teamDescription;
		private String teamImgUrl;
		private int memberCount;
	}

	@Getter
	@Builder
	@NoArgsConstructor(access = AccessLevel.PROTECTED)
	@AllArgsConstructor(access = AccessLevel.PROTECTED)
	public static class JoinTeamResponseDTO {
		private Long teamId;
		private String teamName;
	}
}
