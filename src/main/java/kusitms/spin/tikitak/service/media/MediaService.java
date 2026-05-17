package kusitms.spin.tikitak.service.media;

import kusitms.spin.tikitak.domain.media.entity.Media;
import kusitms.spin.tikitak.domain.media.entity.MediaUpload;
import kusitms.spin.tikitak.domain.media.enums.MediaPurpose;
import kusitms.spin.tikitak.domain.media.enums.MediaStatus;
import kusitms.spin.tikitak.domain.media.enums.MediaUploadStatus;
import kusitms.spin.tikitak.domain.team.entity.Team;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus;
import kusitms.spin.tikitak.domain.team.enums.TeamStatus;
import kusitms.spin.tikitak.global.config.R2Properties;
import kusitms.spin.tikitak.global.dto.media.FileUploadRequest;
import kusitms.spin.tikitak.global.dto.media.MediaUploadCompleteRequest;
import kusitms.spin.tikitak.global.dto.media.MediaUploadCompleteResponse;
import kusitms.spin.tikitak.global.dto.media.MediaUploadItem;
import kusitms.spin.tikitak.global.dto.media.MediaUploadRequest;
import kusitms.spin.tikitak.global.dto.media.MediaUploadResponse;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.global.security.AuthPrincipal;
import kusitms.spin.tikitak.repository.media.MediaRepository;
import kusitms.spin.tikitak.repository.media.MediaUploadRepository;
import kusitms.spin.tikitak.repository.member.MemberRepository;
import kusitms.spin.tikitak.repository.team.TeamMemberRepository;
import kusitms.spin.tikitak.repository.team.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/heic"
    );
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final int UPLOAD_RETENTION_DAYS = 7;

    private final R2Properties r2Properties;
    private final MediaRepository mediaRepository;
    private final MediaUploadRepository mediaUploadRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamRepository teamRepository;
    private final MemberRepository memberRepository;
    private final Optional<S3Presigner> s3Presigner;
    private final Optional<S3Client> s3Client;

    @Transactional
    public MediaUploadResponse createUploadUrls(MediaUploadRequest request) {
        Long memberId = getCurrentMemberId();
        validateRequest(request, memberId);

        Long teamId = resolveTeamId(request);
        MediaUpload upload = mediaUploadRepository.save(MediaUpload.builder()
                .purpose(request.getPurpose())
                .status(MediaUploadStatus.PENDING)
                .teamId(teamId)
                .memberId(memberId)
                .expiresAt(LocalDateTime.now().plusDays(UPLOAD_RETENTION_DAYS))
                .build());

        List<Media> medias = request.getFiles().stream()
                .map(file -> {
                    UUID publicId = UUID.randomUUID();
                    return Media.builder()
                            .publicId(publicId)
                            .purpose(request.getPurpose())
                            .status(MediaStatus.PENDING)
                            .fileName(file.getFileName())
                            .contentType(file.getContentType())
                            .size(file.getSize())
                            .key(buildObjectKey(request.getPurpose(), publicId, file.getContentType()))
                            .teamId(teamId)
                            .memberId(memberId)
                            .upload(upload)
                            .build();
                })
                .toList();

        List<Media> savedMedias = mediaRepository.saveAll(medias);
        List<MediaUploadItem> items = savedMedias.stream()
                .map(media -> new MediaUploadItem(
                        media.getPublicId(),
                        generatePresignedUrl(media),
                        media.getContentType(),
                        LocalDateTime.now().plusMinutes(15)
                ))
                .toList();

        return new MediaUploadResponse(upload.getPublicId(), upload.getStatus(), items);
    }

    @Transactional
    public MediaUploadCompleteResponse completeUpload(Long memberId, UUID uploadId, MediaUploadCompleteRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessException(ErrorCode.MEDIA011);
        }

        MediaUpload upload = mediaUploadRepository.findByPublicIdForUpdate(uploadId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA016));
        if (!upload.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.MEDIA015);
        }
        if (upload.getStatus() != MediaUploadStatus.PENDING) {
            throw new BusinessException(ErrorCode.MEDIA014);
        }
        if (upload.isExpired(LocalDateTime.now())) {
            upload.expire();
            throw new BusinessException(ErrorCode.MEDIA014);
        }

        List<Media> medias = mediaRepository.findByUploadId(upload.getId());
        if (medias.size() != request.getItems().size()) {
            throw new BusinessException(ErrorCode.MEDIA013);
        }

        Map<UUID, MediaUploadCompleteRequest.Item> requestItemsByPublicId = request.getItems().stream()
                .collect(Collectors.toMap(
                        MediaUploadCompleteRequest.Item::getMediaPublicId,
                        item -> item,
                        (left, right) -> {
                            throw new BusinessException(ErrorCode.MEDIA013);
                        }
                ));

        List<MediaUploadCompleteResponse.Item> responseItems = medias.stream()
                .map(media -> completeMedia(upload, media, requestItemsByPublicId))
                .toList();

        upload.complete();
        return new MediaUploadCompleteResponse(
                upload.getPublicId(),
                upload.getStatus(),
                upload.getCompletedAt(),
                responseItems
        );
    }

    @Transactional
    public void deleteUnusedMedia(Long memberId, UUID mediaPublicId) {
        Media media = mediaRepository.findByPublicIdForUpdate(mediaPublicId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA009));

        if (media.getStatus() == MediaStatus.DELETED) {
            throw new BusinessException(ErrorCode.MEDIA009);
        }
        if (!media.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.MEDIA008);
        }
        if (media.getStatus() == MediaStatus.USED) {
            throw new BusinessException(ErrorCode.MEDIA007);
        }

        deleteObject(media, media.getStatus() == MediaStatus.PENDING);
        media.updateStatus(MediaStatus.DELETED);
    }

    public List<Long> findExpiredPendingMediaIds(LocalDateTime cutoff, int limit) {
        return mediaRepository.findExpiredMediaIds(
                MediaStatus.PENDING,
                cutoff,
                PageRequest.of(0, limit)
        );
    }

    public List<Long> findExpiredUploadedMediaIds(LocalDateTime cutoff, int limit) {
        return mediaRepository.findExpiredUploadedMediaIds(
                MediaStatus.UPLOADED,
                cutoff,
                PageRequest.of(0, limit)
        );
    }

    @Transactional
    public boolean deleteExpiredPendingMedia(Long mediaId) {
        return deleteExpiredMedia(mediaId, MediaStatus.PENDING);
    }

    @Transactional
    public boolean deleteExpiredUploadedMedia(Long mediaId) {
        return deleteExpiredMedia(mediaId, MediaStatus.UPLOADED);
    }

    private MediaUploadCompleteResponse.Item completeMedia(
            MediaUpload upload,
            Media media,
            Map<UUID, MediaUploadCompleteRequest.Item> requestItemsByPublicId
    ) {
        MediaUploadCompleteRequest.Item item = requestItemsByPublicId.get(media.getPublicId());
        if (item == null) {
            throw new BusinessException(ErrorCode.MEDIA013);
        }
        validateCompletableMedia(media, item);
        HeadObjectResponse object = headObject(media);
        validateUploadedObject(media, item, object);

        String imageUrl = buildPublicUrl(media);
        media.completeUpload(imageUrl);
        return new MediaUploadCompleteResponse.Item(
                media.getPublicId(),
                media.getStatus(),
                MediaUploadStatus.COMPLETED,
                imageUrl
        );
    }

    private boolean deleteExpiredMedia(Long mediaId, MediaStatus status) {
        Media media = mediaRepository.findByIdForUpdate(mediaId)
                .orElse(null);

        if (media == null || media.getStatus() != status) {
            return false;
        }

        deleteObject(media, status == MediaStatus.PENDING);
        media.updateStatus(MediaStatus.DELETED);
        return true;
    }

    private void validateRequest(MediaUploadRequest request, Long memberId) {
        if (request.getFiles() == null || request.getFiles().isEmpty()) {
            throw new BusinessException(ErrorCode.MEDIA004);
        }
        if (request.getPurpose() == null) {
            throw new BusinessException(ErrorCode.MEDIA003);
        }

        if (request.getPurpose() == MediaPurpose.TEAM_IMAGE) {
            if (request.getTeamId() == null) {
                throw new BusinessException(ErrorCode.COMMON002);
            }
            lockActiveTeam(request.getTeamId());
            boolean isMember = teamMemberRepository.existsByTeamIdAndMemberIdAndStatusAndTeamStatus(
                    request.getTeamId(), memberId, TeamMemberStatus.ACTIVE, TeamStatus.ACTIVE);
            if (!isMember) {
                throw new BusinessException(ErrorCode.TEAM008);
            }
        } else {
            lockMember(memberId);
        }

        int maxCount = switch (request.getPurpose()) {
            case FEED_IMAGE -> 10;
            case DAILY_QUESTION_IMAGE, TEAM_IMAGE, PROFILE_IMAGE -> 1;
        };
        if (request.getFiles().size() > maxCount) {
            throw new BusinessException(ErrorCode.MEDIA005);
        }

        for (FileUploadRequest file : request.getFiles()) {
            if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
                throw new BusinessException(ErrorCode.MEDIA001);
            }
            if (file.getSize() > MAX_FILE_SIZE) {
                throw new BusinessException(ErrorCode.MEDIA002);
            }
        }
    }

    private void validateCompletableMedia(Media media, MediaUploadCompleteRequest.Item item) {
        if (media.getStatus() != MediaStatus.PENDING) {
            throw new BusinessException(ErrorCode.MEDIA014);
        }
        if (!media.getContentType().equals(item.getContentType()) || !media.getSize().equals(item.getSize())) {
            throw new BusinessException(ErrorCode.MEDIA013);
        }
    }

    private HeadObjectResponse headObject(Media media) {
        S3Client client = s3Client.orElseThrow(() -> new BusinessException(ErrorCode.MEDIA017));
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(r2Properties.getBucketName())
                    .key(media.getKey())
                    .build();
            return client.headObject(request);
        } catch (S3Exception e) {
            log.error("Failed to verify uploaded object. mediaId={}, key={}", media.getId(), media.getKey(), e);
            if (e.statusCode() == 404) {
                throw new BusinessException(ErrorCode.MEDIA012, e);
            }
            throw new BusinessException(ErrorCode.MEDIA017, e);
        } catch (Exception e) {
            log.error("Failed to verify uploaded object. mediaId={}, key={}", media.getId(), media.getKey(), e);
            throw new BusinessException(ErrorCode.MEDIA017, e);
        }
    }

    private void validateUploadedObject(Media media, MediaUploadCompleteRequest.Item item, HeadObjectResponse object) {
        if (object.contentLength() == null || !object.contentLength().equals(item.getSize())) {
            throw new BusinessException(ErrorCode.MEDIA013);
        }
        if (!media.getContentType().equals(object.contentType())) {
            throw new BusinessException(ErrorCode.MEDIA013);
        }
    }

    private Long resolveTeamId(MediaUploadRequest request) {
        return request.getPurpose() == MediaPurpose.TEAM_IMAGE ? request.getTeamId() : null;
    }

    private void lockActiveTeam(Long teamId) {
        Team team = teamRepository.findByIdForUpdate(teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM009));
        if (team.getStatus() != TeamStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.TEAM008);
        }
    }

    private void lockMember(Long memberId) {
        memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER001));
    }

    private String generatePresignedUrl(Media media) {
        S3Presigner presigner = s3Presigner.orElseThrow(() -> new BusinessException(ErrorCode.MEDIA006));
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(r2Properties.getBucketName())
                    .key(media.getKey())
                    .contentType(media.getContentType())
                    .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(15))
                    .putObjectRequest(putObjectRequest)
                    .build();

            return presigner.presignPutObject(presignRequest).url().toString();
        } catch (Exception e) {
            log.error("Failed to generate presigned URL for media {}", media.getId(), e);
            throw new BusinessException(ErrorCode.MEDIA006, e);
        }
    }

    private void deleteObject(Media media, boolean ignoreNotFound) {
        S3Client client = s3Client.orElseThrow(() -> new BusinessException(ErrorCode.MEDIA010));
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(r2Properties.getBucketName())
                    .key(media.getKey())
                    .build();
            client.deleteObject(request);
        } catch (S3Exception e) {
            if (ignoreNotFound && e.statusCode() == 404) {
                log.info(
                        "Media object already absent. mediaId={}, mediaPublicId={}, key={}",
                        media.getId(), media.getPublicId(), media.getKey()
                );
                return;
            }
            log.error(
                    "Failed to delete media object. mediaId={}, mediaPublicId={}, key={}",
                    media.getId(), media.getPublicId(), media.getKey(), e
            );
            throw new BusinessException(ErrorCode.MEDIA010, e);
        } catch (Exception e) {
            log.error(
                    "Failed to delete media object. mediaId={}, mediaPublicId={}, key={}",
                    media.getId(), media.getPublicId(), media.getKey(), e
            );
            throw new BusinessException(ErrorCode.MEDIA010, e);
        }
    }

    private String buildPublicUrl(Media media) {
        String publicBaseUrl = r2Properties.getPublicBaseUrl();
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            return media.getKey();
        }
        return publicBaseUrl.replaceAll("/+$", "") + "/" + media.getKey();
    }

    private String buildObjectKey(MediaPurpose purpose, UUID publicId, String contentType) {
        return "media/" + toKeySegment(purpose) + "/" + publicId + "." + extensionOf(contentType);
    }

    private String toKeySegment(MediaPurpose purpose) {
        return purpose.name().toLowerCase().replace("_", "-");
    }

    private String extensionOf(String contentType) {
        return switch (contentType) {
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/png" -> "png";
            case "image/heic" -> "heic";
            default -> throw new BusinessException(ErrorCode.MEDIA001);
        };
    }

    private Long getCurrentMemberId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new BusinessException(ErrorCode.AUTH009);
        }
        if (authentication.getPrincipal() instanceof AuthPrincipal principal) {
            return principal.memberId();
        }
        throw new BusinessException(ErrorCode.AUTH009);
    }
}
