package kusitms.spin.tikitak.global.dto.media;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

import kusitms.spin.tikitak.domain.media.enums.MediaPurpose;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MediaUploadRequest {
    private MediaPurpose purpose;
    private Long teamId;
    private List<FileUploadRequest> files;
}