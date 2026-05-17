package kusitms.spin.tikitak.service.media;

import kusitms.spin.tikitak.domain.media.enums.MediaStatus;
import kusitms.spin.tikitak.domain.media.entity.Media;
import kusitms.spin.tikitak.domain.media.enums.MediaPurpose;
import kusitms.spin.tikitak.domain.team.entity.Team;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus;
import kusitms.spin.tikitak.domain.team.enums.TeamStatus;
import kusitms.spin.tikitak.global.config.R2Properties;
import kusitms.spin.tikitak.global.dto.media.FileUploadRequest;
import kusitms.spin.tikitak.global.dto.media.MediaUploadItem;
import kusitms.spin.tikitak.global.dto.media.MediaUploadRequest;
import kusitms.spin.tikitak.global.dto.media.MediaUploadResponse;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.global.security.AuthPrincipal;
import kusitms.spin.tikitak.repository.member.MemberRepository;
import kusitms.spin.tikitak.repository.media.MediaRepository;
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
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaService {

    private final R2Properties r2Properties;
    private final MediaRepository mediaRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamRepository teamRepository;
    private final MemberRepository memberRepository;
    private final Optional<S3Presigner> s3Presigner;
    private final Optional<S3Client> s3Client;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/heic"
    );
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    @Transactional
    public MediaUploadResponse createUploadUrls(MediaUploadRequest request) {
        Long memberId = getCurrentMemberId();

        // 검증
        validateRequest(request, memberId);

        // Media 엔티티 생성 및 저장
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
                            .teamId(resolveTeamId(request))
                            .memberId(memberId)
                            .build();
                })
                .toList();

        List<Media> savedMedias = mediaRepository.saveAll(medias);

        // Presigned URL 생성
        List<MediaUploadItem> items = savedMedias.stream()
                .map(media -> {
                    String uploadUrl = generatePresignedUrl(media);
                    LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(15); // 15분 유효
                    return new MediaUploadItem(media.getPublicId(), uploadUrl, media.getContentType(), expiresAt);
                })
                .toList();

        return new MediaUploadResponse(savedMedias.get(0).getPublicId(), items);
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
        if (media.getStatus() != MediaStatus.PENDING) {
            throw new BusinessException(ErrorCode.MEDIA007);
        }

        deleteObject(media);
        media.updateStatus(MediaStatus.DELETED);
    }

    public List<Long> findExpiredPendingMediaIds(LocalDateTime cutoff, int limit) {
        return mediaRepository.findExpiredMediaIds(
                MediaStatus.PENDING,
                cutoff,
                PageRequest.of(0, limit)
        );
    }

    @Transactional
    public boolean deleteExpiredPendingMedia(Long mediaId) {
        Media media = mediaRepository.findByIdForUpdate(mediaId)
                .orElse(null);

        if (media == null || media.getStatus() != MediaStatus.PENDING) {
            return false;
        }

        deleteObject(media);
        media.updateStatus(MediaStatus.DELETED);
        return true;
    }

    private void validateRequest(MediaUploadRequest request, Long memberId) {
        if (request.getFiles() == null || request.getFiles().isEmpty()) {
            throw new BusinessException(ErrorCode.MEDIA004);
        }

        // 목적 검증
        if (request.getPurpose() == null) {
            throw new BusinessException(ErrorCode.MEDIA003);
        }

        // 팀 이미지일 경우 팀 멤버십 검증
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

        // 파일 개수 검증
        int maxCount = switch (request.getPurpose()) {
            case FEED_IMAGE -> 10;
            case DAILY_QUESTION_IMAGE, TEAM_IMAGE, PROFILE_IMAGE -> 1;
        };
        if (request.getFiles().size() > maxCount) {
            throw new BusinessException(ErrorCode.MEDIA005);
        }

        // 기존 업로드 개수 검증 (PENDING 포함)
        long pendingCount;
        if (request.getPurpose() == MediaPurpose.TEAM_IMAGE) {
            pendingCount = mediaRepository.countByTeamIdAndPurposeAndStatus(
                    request.getTeamId(), MediaPurpose.TEAM_IMAGE, MediaStatus.PENDING);
        } else {
            List<Media> existing = mediaRepository.findByMemberIdAndPurpose(memberId, request.getPurpose());
            pendingCount = existing.stream()
                    .filter(m -> m.getStatus() == MediaStatus.PENDING)
                    .count();
        }
        if (pendingCount + request.getFiles().size() > maxCount) {
            throw new BusinessException(ErrorCode.MEDIA005);
        }

        // 각 파일 검증
        for (FileUploadRequest file : request.getFiles()) {
            if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
                throw new BusinessException(ErrorCode.MEDIA001);
            }
            if (file.getSize() > MAX_FILE_SIZE) {
                throw new BusinessException(ErrorCode.MEDIA002);
            }
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

    private void deleteObject(Media media) {
        S3Client client = s3Client.orElseThrow(() -> new BusinessException(ErrorCode.MEDIA010));
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(r2Properties.getBucketName())
                    .key(media.getKey())
                    .build();
            client.deleteObject(request);
        } catch (Exception e) {
            log.error(
                    "Failed to delete media object. mediaId={}, mediaPublicId={}, key={}",
                    media.getId(), media.getPublicId(), media.getKey(), e
            );
            throw new BusinessException(ErrorCode.MEDIA010, e);
        }
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
