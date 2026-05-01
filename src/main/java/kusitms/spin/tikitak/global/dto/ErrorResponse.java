package kusitms.spin.tikitak.global.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

    private boolean success;

    @JsonProperty("status")
    private int status;

    private String code;

    private String message;

    private String path;

    private String timestamp;

    @JsonProperty("errors")
    private List<FieldError> errors;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldError {
        private String field;
        private String message;
    }

    public static ErrorResponse of(String code, String message, int status, String path) {
        return ErrorResponse.builder()
                .success(false)
                .status(status)
                .code(code)
                .message(message)
                .path(path)
                .timestamp(java.time.Instant.now().toString())
                .build();
    }

    public static ErrorResponse of(String code, String message, int status, String path, List<FieldError> errors) {
        return ErrorResponse.builder()
                .success(false)
                .status(status)
                .code(code)
                .message(message)
                .path(path)
                .timestamp(java.time.Instant.now().toString())
                .errors(errors)
                .build();
    }
}