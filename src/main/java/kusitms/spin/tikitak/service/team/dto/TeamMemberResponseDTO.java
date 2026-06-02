package kusitms.spin.tikitak.service.team.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

public class TeamMemberResponseDTO {

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamMemberListResponseDTO {
        private List<TeamMemberItemDTO> members;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamMemberItemDTO {
        private Long teamMemberId;
        private String nickname;
        private TeamMemberRole role;
        @Schema(description = "팀 멤버 프로필 이미지 URL(profile_avatar preset 적용)", example = "https://dev-media.tikitak.space/media/profile-image/profile.png?preset=profile_avatar")
        private String profileImgUrl;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamMemberUpdateResponseDTO {
        private String nickname;
        @Schema(description = "수정된 팀 프로필 이미지 URL(profile_avatar preset 적용)", example = "https://dev-media.tikitak.space/media/profile-image/profile.png?preset=profile_avatar")
        private String profileImgUrl;
    }
}
