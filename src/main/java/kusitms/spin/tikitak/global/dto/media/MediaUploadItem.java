package kusitms.spin.tikitak.global.dto.media;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "파일별 업로드 URL 정보")
public class MediaUploadItem {
    @Schema(description = "미디어 파일 public ID", example = "8b2e58f0-8e24-4e34-91b0-87dc86d1892a")
    private UUID mediaPublicId;

    @Schema(description = "R2 presigned upload URL", example = "https://example.r2.cloudflarestorage.com/tikitak-dev-media/media/feed-image/8b2e58f0-8e24-4e34-91b0-87dc86d1892a.png?X-Amz-Algorithm=AWS4-HMAC-SHA256")
    private String uploadUrl;

    @Schema(description = "MIME 타입", example = "image/png")
    private String contentType;

    @Schema(description = "업로드 URL 만료 시각", example = "2026-03-04T20:45:00")
    private LocalDateTime expiresAt;
}
