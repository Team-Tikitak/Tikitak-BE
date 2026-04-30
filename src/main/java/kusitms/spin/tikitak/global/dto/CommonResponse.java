package kusitms.spin.tikitak.global.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommonResponse<T> {

    private boolean success;

    @JsonProperty("status")
    private int status;

    private T data;

    private String timestamp;

    public static <T> CommonResponse<T> success(T data) {
        return CommonResponse.<T>builder()
                .success(true)
                .status(200)
                .data(data)
                .timestamp(java.time.Instant.now().toString())
                .build();
    }

    public static <T> CommonResponse<T> successWithStatus(T data, int statusCode) {
        return CommonResponse.<T>builder()
                .success(true)
                .status(statusCode)
                .data(data)
                .timestamp(java.time.Instant.now().toString())
                .build();
    }
}