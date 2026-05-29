package kusitms.spin.tikitak.global.dto.media;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import kusitms.spin.tikitak.domain.media.enums.MediaPurpose;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "미디어 업로드 URL 발급 요청")
public class MediaUploadRequest {
    @Schema(description = "업로드 목적", example = "FEED_IMAGE")
    @NotNull
    private MediaPurpose purpose;

    @Schema(description = "팀 이미지 업로드 시 팀 ID", example = "10", nullable = true)
    private Long teamId;

    @Schema(description = "업로드할 파일 목록")
    @Valid
    @NotEmpty
    private List<@Valid @NotNull FileUploadRequest> files;
}
