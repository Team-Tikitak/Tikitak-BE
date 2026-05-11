package kusitms.spin.tikitak.service.media;

import kusitms.spin.tikitak.domain.media.enums.MediaStatus;
import kusitms.spin.tikitak.domain.media.entity.Media;
import kusitms.spin.tikitak.domain.media.enums.MediaPurpose;
import kusitms.spin.tikitak.global.config.R2Properties;
import kusitms.spin.tikitak.global.dto.media.FileUploadRequest;
import kusitms.spin.tikitak.global.dto.media.MediaUploadItem;
import kusitms.spin.tikitak.global.dto.media.MediaUploadRequest;
import kusitms.spin.tikitak.global.dto.media.MediaUploadResponse;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.global.security.AuthPrincipal;
import kusitms.spin.tikitak.repository.media.MediaRepository;
import kusitms.spin.tikitak.repository.team.TeamMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    @Autowired(required = false)
    private S3Presigner s3Presigner;

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
                .map(file -> Media.builder()
                        .purpose(request.getPurpose())
                        .status(MediaStatus.PENDING)
                        .fileName(file.getFileName())
                        .contentType(file.getContentType())
                        .size(file.getSize())
                        .teamId(request.getTeamId())
                        .memberId(memberId)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build())
                .toList();

        List<Media> savedMedias = mediaRepository.saveAll(medias);

        // Presigned URL 생성
        List<MediaUploadItem> items = savedMedias.stream()
                .map(media -> {
                    String uploadUrl = generatePresignedUrl(media);
                    LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(15); // 15분 유효
                    return new MediaUploadItem(media.getId(), uploadUrl, media.getContentType(), expiresAt);
                })
                .toList();

        return new MediaUploadResponse(1L, items); // uploadId는 임시로 1L
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
        if (request.getPurpose() == MediaPurpose.TEAM_IMAGE && request.getTeamId() != null) {
            boolean isMember = teamMemberRepository.existsByTeamIdAndMemberId(request.getTeamId(), memberId);
            if (!isMember) {
                throw new BusinessException(ErrorCode.TEAM004); // TEAM004: 해당 팀에 접근할 수 없습니다.
            }
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
        List<Media> existing = mediaRepository.findByMemberIdAndPurpose(memberId, request.getPurpose());
        long pendingCount = existing.stream()
                .filter(m -> m.getStatus() == MediaStatus.PENDING)
                .count();
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

    private String generatePresignedUrl(Media media) {
        if (s3Presigner == null) {
            throw new BusinessException(ErrorCode.MEDIA006);
        }
        try {
            String key = "uploads/" + UUID.randomUUID() + "_" + media.getFileName();

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(r2Properties.getBucketName())
                    .key(key)
                    .contentType(media.getContentType())
                    .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(15))
                    .putObjectRequest(putObjectRequest)
                    .build();

            return s3Presigner.presignPutObject(presignRequest).url().toString();
        } catch (Exception e) {
            log.error("Failed to generate presigned URL for media {}", media.getId(), e);
            throw new BusinessException(ErrorCode.MEDIA006);
        }
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
