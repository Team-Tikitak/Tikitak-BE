package kusitms.spin.tikitak.global.dto.media;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadRequest {
    private String fileName;
    private String contentType;
    private Long size;
}