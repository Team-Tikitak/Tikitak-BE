package kusitms.spin.tikitak.global.dto.media;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "미디어 업로드 완료 처리 요청")
public class MediaUploadCompleteRequest {

    @Schema(description = "업로드 완료 처리할 미디어 목록")
    @Valid
    @NotEmpty
    private List<@Valid @NotNull Item> items;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "업로드 완료 미디어 정보")
    public static class Item {
        @Schema(description = "미디어 파일 public ID", example = "8b2e58f0-8e24-4e34-91b0-87dc86d1892a")
        @NotNull
        private UUID mediaPublicId;

        @Schema(description = "MIME 타입", example = "image/png")
        @NotNull
        private String contentType;

        @Schema(description = "파일 크기(byte)", example = "345000")
        @NotNull
        @Positive
        private Long size;
    }
}
