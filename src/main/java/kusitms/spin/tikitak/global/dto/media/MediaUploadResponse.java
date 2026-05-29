package kusitms.spin.tikitak.global.dto.media;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import kusitms.spin.tikitak.domain.media.enums.MediaUploadStatus;

import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "미디어 업로드 URL 발급 응답")
public class MediaUploadResponse {
    @Schema(description = "업로드 요청 묶음 ID", example = "96380c8e-a2e3-4bc0-893d-d00eed885f82")
    private UUID uploadId;

    @Schema(description = "업로드 요청 묶음 상태", example = "PENDING")
    private MediaUploadStatus uploadStatus;

    @Schema(description = "파일별 업로드 정보")
    private List<MediaUploadItem> items;
}
