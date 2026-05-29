package kusitms.spin.tikitak.service.team.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class TeamRequestDTO {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamCreateRequestDTO {
        @NotBlank
        private String teamName;
        @NotBlank
        private String introduction;
        private String profileImageUrl;
        @NotBlank
        private String nickName;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamUpdateRequestDTO {
        @NotBlank
        private String teamName;
        @NotBlank
        private String introduction;
    }
}
