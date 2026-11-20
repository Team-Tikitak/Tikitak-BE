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
import kusitms.spin.tikitak.service.media.MediaUploadCompletionService.CompletionPlan;
import kusitms.spin.tikitak.service.media.MediaUploadCompletionService.UploadTarget;
import kusitms.spin.tikitak.service.media.MediaUploadCompletionService.UploadedObject;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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
	private final MediaUploadCompletionService mediaUploadCompletionService;
	private final MediaDeletionService mediaDeletionService;
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
				.<Media>map(file -> {
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

	/**
	 * 업로드 완료. R2 {@code headObject}가 DB 트랜잭션·락을 잡지 않도록 트랜잭션을 두지 않고 세 단계로 나눈다:
	 * 락 없는 사전 검증({@link MediaUploadCompletionService#prepare}) → {@code headObject}(트랜잭션 없음)
	 * → 락을 잡고 전체 재검증 후 상태 전환({@link MediaUploadCompletionService#complete}).
	 * 이 메서드 안에서 리포지토리를 직접 쓰지 않는다(트랜잭션 없는 구간의 EntityManager가 커넥션을 잡을 수 있음).
	 */
	public MediaUploadCompleteResponse completeUpload(Long memberId, UUID uploadId, MediaUploadCompleteRequest request) {
		if (request.getItems() == null || request.getItems().isEmpty()) {
			throw new BusinessException(ErrorCode.MEDIA011);
		}

		CompletionPlan plan = mediaUploadCompletionService.prepare(memberId, uploadId, request.getItems());
		Map<Long, UploadedObject> uploadedObjects = new HashMap<>();
		for (UploadTarget target : plan.targets()) {
			HeadObjectResponse object = headObject(target);
			UploadedObject uploadedObject = new UploadedObject(object.contentType(), object.contentLength());
			if (!uploadedObject.matches(target.contentType(), target.size())) {
				throw new BusinessException(ErrorCode.MEDIA013);
			}
			uploadedObjects.put(target.mediaId(), uploadedObject);
		}
		return mediaUploadCompletionService.complete(memberId, uploadId, request.getItems(), uploadedObjects);
	}

	/**
	 * 사용하지 않은 미디어 삭제. 트랜잭션을 두지 않고 DB 전이 커밋({@link MediaDeletionService#markUnusedMediaDeleted})
	 * → R2 삭제(트랜잭션 밖) 순서로 실행한다. R2 삭제가 실패해도 DB 전이는 이미 커밋됐으므로 로그만 남기고,
	 * 오브젝트는 DELETED 하드 삭제 배치가 다시 지운다.
	 */
	public void deleteUnusedMedia(Long memberId, UUID mediaPublicId) {
		MediaDeleteTarget target = mediaDeletionService.markUnusedMediaDeleted(memberId, mediaPublicId);
		deleteObjectOrDefer(target);
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

	public List<Long> findExpiredDeletedMediaIds(LocalDateTime cutoff, int limit) {
		return mediaRepository.findExpiredDeletedMediaIds(
				MediaStatus.DELETED,
				cutoff,
				PageRequest.of(0, limit)
		);
	}

	public boolean deleteExpiredPendingMedia(Long mediaId) {
		return deleteExpiredMedia(mediaId, MediaStatus.PENDING);
	}

	public boolean deleteExpiredUploadedMedia(Long mediaId) {
		return deleteExpiredMedia(mediaId, MediaStatus.UPLOADED);
	}

	/**
	 * 보관 기간이 지난 DELETED 미디어 하드 삭제. 락 없이 상태를 확인하고({@code findById}는 리포지토리 자체의 짧은 읽기 트랜잭션)
	 * → R2 삭제(트랜잭션 밖, 404는 성공. 그 밖의 실패는 MEDIA010을 던지고 행을 남겨 다음 배치에서 다시 시도)
	 * → 락을 잡고 DELETED인지 다시 확인한 뒤 행을 삭제한다({@link MediaDeletionService#deleteRowIfStillDeleted}).
	 */
	public boolean deleteExpiredDeletedMedia(Long mediaId) {
		Optional<MediaDeleteTarget> target = mediaRepository.findById(mediaId)
				.filter(media -> media.getStatus() == MediaStatus.DELETED)
				.map(MediaDeleteTarget::from);
		if (target.isEmpty()) {
			return false;
		}

		deleteObject(target.get());
		return mediaDeletionService.deleteRowIfStillDeleted(mediaId);
	}

	public List<Long> findExpiredMediaUploadIds(LocalDateTime now, int limit) {
		return mediaUploadRepository.findExpiredMediaUploadIds(now, PageRequest.of(0, limit));
	}

	@Transactional
	public boolean deleteExpiredMediaUpload(Long uploadId) {
		MediaUpload upload = mediaUploadRepository.findByIdForUpdate(uploadId)
				.orElse(null);

		if (upload == null || upload.getExpiresAt() == null || !upload.getExpiresAt().isBefore(LocalDateTime.now())) {
			return false;
		}
		if (mediaRepository.existsByUploadId(uploadId)) {
			return false;
		}

		mediaUploadRepository.delete(upload);
		return true;
	}

	/**
	 * 만료 PENDING/UPLOADED 정리. DB 전이 커밋 → R2 삭제(트랜잭션 밖) 순서이며, R2 삭제가 실패해도
	 * DB 전이는 끝났으므로 true를 돌려준다. 오브젝트는 DELETED 하드 삭제 배치가 다시 지운다.
	 */
	private boolean deleteExpiredMedia(Long mediaId, MediaStatus status) {
		Optional<MediaDeleteTarget> target = mediaDeletionService.markExpiredMediaDeleted(mediaId, status);
		if (target.isEmpty()) {
			return false;
		}

		deleteObjectOrDefer(target.get());
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

	private HeadObjectResponse headObject(UploadTarget target) {
		S3Client client = s3Client.orElseThrow(() -> new BusinessException(ErrorCode.MEDIA017));
		try {
			HeadObjectRequest request = HeadObjectRequest.builder()
					.bucket(r2Properties.getBucketName())
					.key(target.key())
					.build();
			return client.headObject(request);
		} catch (S3Exception e) {
			log.error("Failed to verify uploaded object. mediaId={}, key={}", target.mediaId(), target.key(), e);
			if (e.statusCode() == 404) {
				throw new BusinessException(ErrorCode.MEDIA012, e);
			}
			throw new BusinessException(ErrorCode.MEDIA017, e);
		} catch (Exception e) {
			log.error("Failed to verify uploaded object. mediaId={}, key={}", target.mediaId(), target.key(), e);
			throw new BusinessException(ErrorCode.MEDIA017, e);
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

	/**
	 * DB 전이가 이미 커밋된 뒤의 R2 삭제. 실패(s3Client 없음 포함)해도 예외를 던지지 않고 로그만 남긴다.
	 * 남은 오브젝트는 DELETED 하드 삭제 배치({@link #deleteExpiredDeletedMedia})가 다시 지운다.
	 */
	private void deleteObjectOrDefer(MediaDeleteTarget target) {
		try {
			deleteObject(target);
		} catch (BusinessException e) {
			log.warn(
					"Media object delete deferred to hard-delete batch. mediaId={}, mediaPublicId={}, key={}",
					target.mediaId(), target.publicId(), target.key()
			);
		}
	}

	/** R2 오브젝트 삭제. 404는 이미 없는 것으로 보고 성공 처리하고, 그 밖의 실패나 s3Client 없음은 MEDIA010을 던진다. */
	private void deleteObject(MediaDeleteTarget target) {
		if (s3Client.isEmpty()) {
			log.error(
					"S3Client is not configured; cannot delete media object. mediaId={}, mediaPublicId={}, key={}",
					target.mediaId(), target.publicId(), target.key()
			);
			throw new BusinessException(ErrorCode.MEDIA010);
		}
		S3Client client = s3Client.get();
		try {
			DeleteObjectRequest request = DeleteObjectRequest.builder()
					.bucket(r2Properties.getBucketName())
					.key(target.key())
					.build();
			client.deleteObject(request);
		} catch (S3Exception e) {
			if (e.statusCode() == 404) {
				log.info(
						"Media object already absent. mediaId={}, mediaPublicId={}, key={}",
						target.mediaId(), target.publicId(), target.key()
				);
				return;
			}
			log.error(
					"Failed to delete media object. mediaId={}, mediaPublicId={}, key={}",
					target.mediaId(), target.publicId(), target.key(), e
			);
			throw new BusinessException(ErrorCode.MEDIA010, e);
		} catch (Exception e) {
			log.error(
					"Failed to delete media object. mediaId={}, mediaPublicId={}, key={}",
					target.mediaId(), target.publicId(), target.key(), e
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
