package kusitms.spin.tikitak.domain.media.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import kusitms.spin.tikitak.domain.common.BaseTimeEntity;
import kusitms.spin.tikitak.domain.media.enums.MediaPurpose;
import kusitms.spin.tikitak.domain.media.enums.MediaUploadStatus;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "media_upload")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MediaUpload extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Builder.Default
	@Column(name = "public_id", nullable = false, unique = true, updatable = false)
	private UUID publicId = UUID.randomUUID();

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50)
	private MediaPurpose purpose;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private MediaUploadStatus status;

	@Column
	private Long teamId;

	@Column(nullable = false)
	private Long memberId;

	private LocalDateTime completedAt;

	private LocalDateTime expiresAt;

	public void complete() {
		this.status = MediaUploadStatus.COMPLETED;
		this.completedAt = LocalDateTime.now();
	}

	public void expire() {
		this.status = MediaUploadStatus.EXPIRED;
	}

	public boolean isExpired(LocalDateTime now) {
		return expiresAt != null && expiresAt.isBefore(now);
	}

	@PrePersist
	protected void onCreate() {
		if (publicId == null) {
			publicId = UUID.randomUUID();
		}
	}
}
