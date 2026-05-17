package kusitms.spin.tikitak.global.dto.media;

import io.swagger.v3.oas.annotations.media.Schema;
import kusitms.spin.tikitak.domain.media.enums.MediaStatus;
import kusitms.spin.tikitak.domain.media.enums.MediaUploadStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "미디어 업로드 완료 처리 응답")
public class MediaUploadCompleteResponse {
    @Schema(description = "업로드 요청 묶음 ID", example = "96380c8e-a2e3-4bc0-893d-d00eed885f82")
    private UUID uploadId;

    @Schema(description = "업로드 요청 묶음 상태", example = "COMPLETED")
    private MediaUploadStatus uploadStatus;

    @Schema(description = "업로드 완료 처리 시각", example = "2026-03-04T20:35:00")
    private LocalDateTime completedAt;

    @Schema(description = "파일별 완료 처리 결과")
    private List<Item> items;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "파일별 완료 처리 결과")
    public static class Item {
        @Schema(description = "미디어 파일 public ID", example = "8b2e58f0-8e24-4e34-91b0-87dc86d1892a")
        private UUID mediaPublicId;

        @Schema(description = "미디어 상태", example = "UPLOADED")
        private MediaStatus mediaStatus;

        @Schema(description = "업로드 요청 묶음 상태", example = "COMPLETED")
        private MediaUploadStatus uploadStatus;

        @Schema(description = "공개 이미지 URL", example = "https://media.tikitak.xyz/media/feed-image/8b2e58f0-8e24-4e34-91b0-87dc86d1892a.png")
        private String imageUrl;
    }
}
