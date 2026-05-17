package kusitms.spin.tikitak.global.dto.media;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MediaUploadItem {
    private UUID mediaPublicId;
    private String uploadUrl;
    private String contentType;
    private LocalDateTime expiresAt;
}
