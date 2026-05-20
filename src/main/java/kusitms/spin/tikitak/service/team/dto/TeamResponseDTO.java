package kusitms.spin.tikitak.service.team.dto;

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
    public static class TeamMemberDTO {
        private String nickname;
        private TeamMemberRole teamMemberRole;
        private String email;
        private String profileImgUrl;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamDetailResponseDTO {
        private String teamName;
        private List<TeamMemberDTO> teamMemberDTOList;
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
