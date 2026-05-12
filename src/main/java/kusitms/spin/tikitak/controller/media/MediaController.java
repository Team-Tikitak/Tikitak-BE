package kusitms.spin.tikitak.controller.media;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import kusitms.spin.tikitak.global.dto.CommonResponse;
import kusitms.spin.tikitak.global.dto.media.MediaUploadRequest;
import kusitms.spin.tikitak.global.dto.media.MediaUploadResponse;
import kusitms.spin.tikitak.service.media.MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequiredArgsConstructor
@Tag(name = "Media", description = "미디어 업로드 API")
public class MediaController {

    private final MediaService mediaService;

    @Operation(
            summary = "미디어 업로드 URL 발급",
            description = "이미지를 R2에 업로드하기 위한 presigned URL을 발급합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/api/v1/media/uploads")
    public ResponseEntity<CommonResponse<MediaUploadResponse>> createUploadUrls(
            @Valid @RequestBody MediaUploadRequest request
    ) {
        MediaUploadResponse response = mediaService.createUploadUrls(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.successWithStatus(response, HttpStatus.CREATED.value()));
    }
}
