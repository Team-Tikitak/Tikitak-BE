package kusitms.spin.tikitak.service.team.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class TeamRequestDTO {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamCreateRequestDTO {
        private String teamName;
        private String introduction;
        private String profileImageUrl;
        private String nickName;
    }
}
