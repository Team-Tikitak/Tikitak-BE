package kusitms.spin.tikitak.domain.dto.system;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealthResponse {

    @JsonProperty("status")
    private String status;

    @JsonProperty("serverTime")
    private String serverTime;

    public static HealthResponse up() {
        return HealthResponse.builder()
                .status("UP")
                .serverTime(Instant.now().toString())
                .build();
    }
}