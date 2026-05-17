package kusitms.spin.tikitak.service.media;

import kusitms.spin.tikitak.domain.media.entity.Media;
import kusitms.spin.tikitak.domain.media.enums.MediaPurpose;
import kusitms.spin.tikitak.domain.media.enums.MediaStatus;
import kusitms.spin.tikitak.global.config.R2Properties;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.repository.media.MediaRepository;
import kusitms.spin.tikitak.repository.member.MemberRepository;
import kusitms.spin.tikitak.repository.team.TeamMemberRepository;
import kusitms.spin.tikitak.repository.team.TeamRepository;
import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.data.domain.Pageable;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaServiceTest extends UnitTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long OTHER_MEMBER_ID = 2L;
    private static final Long MEDIA_ID = 10L;
    private static final UUID MEDIA_PUBLIC_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String BUCKET_NAME = "test-bucket";
    private static final String OBJECT_KEY = "media/feed-image/11111111-1111-1111-1111-111111111111.png";

    @Mock
    private R2Properties r2Properties;

    @Mock
    private MediaRepository mediaRepository;

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private S3Client s3Client;

    private MediaService mediaService;

    @BeforeEach
    void setUp() {
        mediaService = new MediaService(
                r2Properties,
                mediaRepository,
                teamMemberRepository,
                teamRepository,
                memberRepository,
                Optional.of(s3Presigner),
                Optional.of(s3Client)
        );
    }

    @Test
    @DisplayName("PENDING 상태의 본인 미디어를 R2에서 삭제하고 DB 상태를 DELETED로 변경한다")
    void deleteUnusedMediaDeletesObjectAndMarksDeleted() {
        Media media = media(MediaStatus.PENDING, MEMBER_ID);
        when(mediaRepository.findByPublicIdForUpdate(MEDIA_PUBLIC_ID)).thenReturn(Optional.of(media));
        when(r2Properties.getBucketName()).thenReturn(BUCKET_NAME);

        mediaService.deleteUnusedMedia(MEMBER_ID, MEDIA_PUBLIC_ID);

        ArgumentCaptor<DeleteObjectRequest> requestCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(requestCaptor.capture());
        assertThat(requestCaptor.getValue().bucket()).isEqualTo(BUCKET_NAME);
        assertThat(requestCaptor.getValue().key()).isEqualTo(OBJECT_KEY);
        assertThat(media.getStatus()).isEqualTo(MediaStatus.DELETED);
    }

    @Test
    @DisplayName("미디어가 없으면 MEDIA009 예외가 발생한다")
    void deleteUnusedMediaThrowsWhenMediaNotFound() {
        when(mediaRepository.findByPublicIdForUpdate(MEDIA_PUBLIC_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mediaService.deleteUnusedMedia(MEMBER_ID, MEDIA_PUBLIC_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEDIA009));

        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    @DisplayName("이미 DELETED 상태인 미디어는 MEDIA009 예외가 발생한다")
    void deleteUnusedMediaThrowsWhenAlreadyDeleted() {
        when(mediaRepository.findByPublicIdForUpdate(MEDIA_PUBLIC_ID)).thenReturn(Optional.of(media(MediaStatus.DELETED, MEMBER_ID)));

        assertThatThrownBy(() -> mediaService.deleteUnusedMedia(MEMBER_ID, MEDIA_PUBLIC_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEDIA009));

        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    @DisplayName("업로드한 사용자가 아니면 MEDIA008 예외가 발생한다")
    void deleteUnusedMediaThrowsWhenNotOwner() {
        when(mediaRepository.findByPublicIdForUpdate(MEDIA_PUBLIC_ID)).thenReturn(Optional.of(media(MediaStatus.PENDING, OTHER_MEMBER_ID)));

        assertThatThrownBy(() -> mediaService.deleteUnusedMedia(MEMBER_ID, MEDIA_PUBLIC_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEDIA008));

        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    @DisplayName("PENDING 상태가 아니면 MEDIA007 예외가 발생한다")
    void deleteUnusedMediaThrowsWhenMediaIsUsed() {
        when(mediaRepository.findByPublicIdForUpdate(MEDIA_PUBLIC_ID)).thenReturn(Optional.of(media(MediaStatus.UPLOADED, MEMBER_ID)));

        assertThatThrownBy(() -> mediaService.deleteUnusedMedia(MEMBER_ID, MEDIA_PUBLIC_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEDIA007));

        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    @DisplayName("R2 삭제 실패 시 MEDIA010 예외가 발생한다")
    void deleteUnusedMediaThrowsWhenObjectDeleteFails() {
        Media media = media(MediaStatus.PENDING, MEMBER_ID);
        when(mediaRepository.findByPublicIdForUpdate(MEDIA_PUBLIC_ID)).thenReturn(Optional.of(media));
        when(r2Properties.getBucketName()).thenReturn(BUCKET_NAME);
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(500).message("server error").build());

        assertThatThrownBy(() -> mediaService.deleteUnusedMedia(MEMBER_ID, MEDIA_PUBLIC_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEDIA010));

        assertThat(media.getStatus()).isEqualTo(MediaStatus.PENDING);
    }

    @Test
    @DisplayName("R2 404 예외가 발생하면 MEDIA010 예외가 발생한다")
    void deleteUnusedMediaThrowsWhenObjectDeleteReturnsNotFoundError() {
        Media media = media(MediaStatus.PENDING, MEMBER_ID);
        when(mediaRepository.findByPublicIdForUpdate(MEDIA_PUBLIC_ID)).thenReturn(Optional.of(media));
        when(r2Properties.getBucketName()).thenReturn(BUCKET_NAME);
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).message("not found").build());

        assertThatThrownBy(() -> mediaService.deleteUnusedMedia(MEMBER_ID, MEDIA_PUBLIC_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEDIA010));

        assertThat(media.getStatus()).isEqualTo(MediaStatus.PENDING);
    }

    @Test
    @DisplayName("만료된 PENDING 미디어 id 목록을 최대 처리 개수만큼 조회한다")
    void findExpiredPendingMediaIds() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 3, 4, 20, 30);
        when(mediaRepository.findExpiredMediaIds(any(MediaStatus.class), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(1L, 2L));

        List<Long> mediaIds = mediaService.findExpiredPendingMediaIds(cutoff, 100);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(mediaRepository).findExpiredMediaIds(
                org.mockito.ArgumentMatchers.eq(MediaStatus.PENDING),
                org.mockito.ArgumentMatchers.eq(cutoff),
                pageableCaptor.capture()
        );
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
        assertThat(mediaIds).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("만료된 PENDING 미디어를 R2에서 삭제하고 DB 상태를 DELETED로 변경한다")
    void deleteExpiredPendingMedia() {
        Media media = media(MediaStatus.PENDING, MEMBER_ID);
        when(mediaRepository.findByIdForUpdate(MEDIA_ID)).thenReturn(Optional.of(media));
        when(r2Properties.getBucketName()).thenReturn(BUCKET_NAME);

        boolean deleted = mediaService.deleteExpiredPendingMedia(MEDIA_ID);

        assertThat(deleted).isTrue();
        assertThat(media.getStatus()).isEqualTo(MediaStatus.DELETED);
        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    @DisplayName("만료 미디어가 없거나 PENDING 상태가 아니면 삭제하지 않는다")
    void deleteExpiredPendingMediaSkipsWhenNotPending() {
        when(mediaRepository.findByIdForUpdate(MEDIA_ID)).thenReturn(Optional.of(media(MediaStatus.UPLOADED, MEMBER_ID)));

        boolean deleted = mediaService.deleteExpiredPendingMedia(MEDIA_ID);

        assertThat(deleted).isFalse();
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    private Media media(MediaStatus status, Long memberId) {
        return Media.builder()
                .id(MEDIA_ID)
                .publicId(MEDIA_PUBLIC_ID)
                .purpose(MediaPurpose.FEED_IMAGE)
                .status(status)
                .fileName("test.png")
                .contentType("image/png")
                .size(1000L)
                .key(OBJECT_KEY)
                .memberId(memberId)
                .build();
    }
}
