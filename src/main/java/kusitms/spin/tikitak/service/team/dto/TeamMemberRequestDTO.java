package kusitms.spin.tikitak.service.team.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class TeamMemberRequestDTO {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamMemberUpdateRequestDTO {
        @NotBlank
        @Size(max = 30)
        private String nickname;
        private String profileImgUrl;
    }
}
