package kusitms.spin.tikitak.controller.media;

import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.service.media.MediaService;
import kusitms.spin.tikitak.support.ApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MediaControllerTest extends ApiTest {

    private static final UUID MEDIA_PUBLIC_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

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
}
