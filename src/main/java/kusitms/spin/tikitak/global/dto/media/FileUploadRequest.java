package kusitms.spin.tikitak.global.dto.media;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "업로드 파일 정보")
public class FileUploadRequest {
    @Schema(description = "파일명", example = "tikitak.png")
    @NotBlank
    private String fileName;

    @Schema(description = "MIME 타입", example = "image/png")
    @NotBlank
    private String contentType;

    @Schema(description = "파일 크기(byte)", example = "345000")
    @NotNull
    @Positive
    private Long size;
}
