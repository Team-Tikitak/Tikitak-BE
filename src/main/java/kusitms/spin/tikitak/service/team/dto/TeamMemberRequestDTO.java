package kusitms.spin.tikitak.service.team.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class TeamMemberRequestDTO {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamMemberUpdateRequestDTO {
        @NotBlank
        private String nickname;
        private String profileImgUrl;
    }
}
