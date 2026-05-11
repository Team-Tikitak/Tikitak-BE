package kusitms.spin.tikitak.global.dto.media;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadRequest {
    @NotBlank
    private String fileName;

    @NotBlank
    private String contentType;

    @NotNull
    @Positive
    private Long size;
}
