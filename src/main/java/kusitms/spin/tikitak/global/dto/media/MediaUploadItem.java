package kusitms.spin.tikitak.global.dto.media;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MediaUploadItem {
    private Long mediaId;
    private String uploadUrl;
    private String contentType;
    private LocalDateTime expiresAt;
}