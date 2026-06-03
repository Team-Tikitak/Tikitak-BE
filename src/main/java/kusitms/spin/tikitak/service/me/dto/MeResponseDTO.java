package kusitms.spin.tikitak.service.me.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import kusitms.spin.tikitak.domain.member.enums.MemberStatus;
import kusitms.spin.tikitak.domain.member.enums.ProfileCharacterType;
import kusitms.spin.tikitak.domain.member.enums.SocialProvider;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

public class MeResponseDTO {

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class MeProfileResponseDTO {
		private Long memberId;
		private String email;
		private String name;
		private SocialProvider socialProvider;
		private MemberStatus status;
		private boolean hasAgreedRequiredTerms;
		private boolean onboardingCompleted;
		private ProfileCharacterType profileCharacterType;
		private Long activeTeamId;
		private boolean hasTeam;
		private LocalDateTime createdAt;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class TeamListResponseDTO {
		private List<TeamItemDTO> teams;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class TeamItemDTO {
		private Long teamId;
		private Long teamMemberId;
		private String teamName;
		private String description;
		private TeamMemberRole role;
		private String nickname;
		@Schema(description = "팀 내 프로필 이미지 URL(profile_avatar preset 적용)", example = "https://dev-media.tikitak.space/default-profiles/tak-builder.png?preset=profile_avatar")
		private String profileImgUrl;
		private long memberCount;
		private long newActivityCount;
		@JsonProperty("isActive")
		private boolean isActive;
		private LocalDateTime joinedAt;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class ActiveTeamUpdateResponseDTO {
		private Long activeTeamId;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class AgreementResponseDTO {
		private boolean termsAgreed;
		private boolean privacyAgreed;
		private LocalDateTime termsAgreedAt;
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class OnboardingUpdateResponseDTO {
		private boolean onboardingCompleted;
		private ProfileCharacterType profileCharacterType;
	}
}
