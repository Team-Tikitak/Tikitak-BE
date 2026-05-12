package kusitms.spin.tikitak.global.dto.media;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import kusitms.spin.tikitak.domain.media.enums.MediaPurpose;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MediaUploadRequest {
    @NotNull
    private MediaPurpose purpose;

    private Long teamId;

    @Valid
    @NotEmpty
    private List<@Valid @NotNull FileUploadRequest> files;
}
