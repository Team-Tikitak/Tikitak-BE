package kusitms.spin.tikitak.service.media;

import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaCleanupBatchServiceTest extends UnitTest {

    @Mock
    private MediaService mediaService;

    private MediaCleanupBatchService mediaCleanupBatchService;

    @BeforeEach
    void setUp() {
        mediaCleanupBatchService = new MediaCleanupBatchService(mediaService);
    }

    @Test
    @DisplayName("7일 지난 PENDING 미디어를 최대 100건 조회해 정리한다")
    void deleteExpiredPendingMedia() {
        when(mediaService.findExpiredPendingMediaIds(any(LocalDateTime.class), eq(100)))
                .thenReturn(List.of(1L, 2L, 3L));
        when(mediaService.findExpiredUploadedMediaIds(any(LocalDateTime.class), eq(100)))
                .thenReturn(List.of());
        when(mediaService.findExpiredDeletedMediaIds(any(LocalDateTime.class), eq(100)))
                .thenReturn(List.of(4L));
        when(mediaService.deleteExpiredPendingMedia(1L)).thenReturn(true);
        when(mediaService.deleteExpiredPendingMedia(2L)).thenThrow(new RuntimeException("R2 failed"));
        when(mediaService.deleteExpiredPendingMedia(3L)).thenReturn(true);
        when(mediaService.deleteExpiredDeletedMedia(4L)).thenReturn(true);

        mediaCleanupBatchService.deleteExpiredPendingMedia();

        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mediaService).findExpiredPendingMediaIds(cutoffCaptor.capture(), eq(100));
        assertThat(cutoffCaptor.getValue()).isBefore(LocalDateTime.now().minusDays(6));
        ArgumentCaptor<LocalDateTime> deletedCutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mediaService).findExpiredDeletedMediaIds(deletedCutoffCaptor.capture(), eq(100));
        assertThat(deletedCutoffCaptor.getValue()).isBefore(LocalDateTime.now().minusHours(23));
        verify(mediaService).deleteExpiredPendingMedia(1L);
        verify(mediaService).deleteExpiredPendingMedia(2L);
        verify(mediaService).deleteExpiredPendingMedia(3L);
        verify(mediaService).deleteExpiredDeletedMedia(4L);
    }
}
