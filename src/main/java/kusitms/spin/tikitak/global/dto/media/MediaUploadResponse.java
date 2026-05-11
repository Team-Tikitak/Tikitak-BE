package kusitms.spin.tikitak.global.dto.media;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MediaUploadResponse {
    private Long uploadId;
    private List<MediaUploadItem> items;
}