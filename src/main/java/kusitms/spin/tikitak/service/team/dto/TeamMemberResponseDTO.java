package kusitms.spin.tikitak.service.team.dto;

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
        private String profileImgUrl;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamMemberUpdateResponseDTO {
        private String nickname;
        private String profileImgUrl;
    }
}
