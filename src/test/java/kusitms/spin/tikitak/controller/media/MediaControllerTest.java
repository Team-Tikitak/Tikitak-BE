package kusitms.spin.tikitak.controller.media;

import kusitms.spin.tikitak.domain.media.enums.MediaStatus;
import kusitms.spin.tikitak.domain.media.enums.MediaUploadStatus;
import kusitms.spin.tikitak.global.dto.media.MediaUploadCompleteRequest;
import kusitms.spin.tikitak.global.dto.media.MediaUploadCompleteResponse;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.service.media.MediaService;
import kusitms.spin.tikitak.support.ApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MediaControllerTest extends ApiTest {

    private static final UUID MEDIA_PUBLIC_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID UPLOAD_PUBLIC_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private MediaService mediaService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = mockMvc(new MediaController(mediaService));
    }

    @Test
    @DisplayName("DELETE /api/v1/media/{mediaPublicId}는 미사용 미디어를 삭제하고 200을 반환한다")
    void deleteUnusedMedia() throws Exception {
        mockMvc.perform(delete("/api/v1/media/{mediaPublicId}", MEDIA_PUBLIC_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.timestamp").exists());

        verify(mediaService).deleteUnusedMedia(TEST_MEMBER_ID, MEDIA_PUBLIC_ID);
    }

    @Test
    @DisplayName("삭제할 수 없는 미디어이면 에러 응답을 반환한다")
    void deleteUnusedMediaReturnsErrorResponse() throws Exception {
        doThrow(new BusinessException(ErrorCode.MEDIA007))
                .when(mediaService).deleteUnusedMedia(TEST_MEMBER_ID, MEDIA_PUBLIC_ID);

        mockMvc.perform(delete("/api/v1/media/{mediaPublicId}", MEDIA_PUBLIC_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("MEDIA007"));
    }

    @Test
    @DisplayName("mediaPublicId가 UUID 형식이 아니면 400을 반환한다")
    void deleteUnusedMediaReturnsBadRequestWhenMediaPublicIdInvalid() throws Exception {
        mockMvc.perform(delete("/api/v1/media/{mediaPublicId}", "1234"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("COMMON001"));
    }

    @Test
    @DisplayName("POST /api/v1/media/uploads/{uploadId}/complete는 업로드 완료 결과를 반환한다")
    void completeUpload() throws Exception {
        MediaUploadCompleteResponse response = new MediaUploadCompleteResponse(
                UPLOAD_PUBLIC_ID,
                MediaUploadStatus.COMPLETED,
                LocalDateTime.of(2026, 3, 4, 20, 35),
                List.of(new MediaUploadCompleteResponse.Item(
                        MEDIA_PUBLIC_ID,
                        MediaStatus.UPLOADED,
                        MediaUploadStatus.COMPLETED,
                        "https://media.tikitak.xyz/media/feed-image/test.png"
                ))
        );
        when(mediaService.completeUpload(eq(TEST_MEMBER_ID), eq(UPLOAD_PUBLIC_ID), any(MediaUploadCompleteRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/media/uploads/{uploadId}/complete", UPLOAD_PUBLIC_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    {
                                      "mediaPublicId": "11111111-1111-1111-1111-111111111111",
                                      "contentType": "image/png",
                                      "size": 1000
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.uploadId").value(UPLOAD_PUBLIC_ID.toString()))
                .andExpect(jsonPath("$.data.uploadStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.items[0].mediaPublicId").value(MEDIA_PUBLIC_ID.toString()))
                .andExpect(jsonPath("$.data.items[0].mediaStatus").value("UPLOADED"));

        verify(mediaService).completeUpload(eq(TEST_MEMBER_ID), eq(UPLOAD_PUBLIC_ID), any(MediaUploadCompleteRequest.class));
    }

    @Test
    @DisplayName("업로드 완료 처리 중 비즈니스 예외가 발생하면 에러 응답을 반환한다")
    void completeUploadReturnsErrorResponse() throws Exception {
        when(mediaService.completeUpload(eq(TEST_MEMBER_ID), eq(UPLOAD_PUBLIC_ID), any(MediaUploadCompleteRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.MEDIA012));

        mockMvc.perform(post("/api/v1/media/uploads/{uploadId}/complete", UPLOAD_PUBLIC_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    {
                                      "mediaPublicId": "11111111-1111-1111-1111-111111111111",
                                      "contentType": "image/png",
                                      "size": 1000
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("MEDIA012"));
    }

    @Test
    @DisplayName("업로드 완료 요청 items가 비어 있으면 validation 에러를 반환한다")
    void completeUploadReturnsBadRequestWhenItemsEmpty() throws Exception {
        mockMvc.perform(post("/api/v1/media/uploads/{uploadId}/complete", UPLOAD_PUBLIC_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("COMMON001"));
    }
}
