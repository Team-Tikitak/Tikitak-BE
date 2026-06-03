package kusitms.spin.tikitak.service.team.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

public class TeamResponseDTO {

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamCreateResponseDTO {
        private String teamName;
//        private String inviteCode;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MyProfileDTO {
        private String nickname;
        private TeamMemberRole teamMemberRole;
        @Schema(description = "내 팀 프로필 이미지 URL(profile_avatar preset 적용)", example = "https://dev-media.tikitak.space/media/profile-image/profile.png?preset=profile_avatar")
        private String profileImgUrl;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamMemberDTO {
        private Long teamMemberId;
        private String nickname;
        private TeamMemberRole teamMemberRole;
        private String email;
        @Schema(description = "팀 멤버 프로필 이미지 URL(profile_avatar preset 적용)", example = "https://dev-media.tikitak.space/default-profiles/tak-builder.png?preset=profile_avatar")
        private String profileImgUrl;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamDetailResponseDTO {
        private String teamName;
        private MyProfileDTO myProfile;
        private List<TeamMemberDTO> teamMembers;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamUpdateResponseDTO {
        private String teamName;
        private String introduction;
    }
}
