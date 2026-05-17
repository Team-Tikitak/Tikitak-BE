package kusitms.spin.tikitak.global.dto.media;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MediaUploadResponse {
    /**
     * Representative public id for this upload request.
     */
    private UUID uploadId;

    private List<MediaUploadItem> items;
}
