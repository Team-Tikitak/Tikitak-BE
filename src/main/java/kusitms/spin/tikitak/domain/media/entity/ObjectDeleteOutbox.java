package kusitms.spin.tikitak.domain.media.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import kusitms.spin.tikitak.domain.media.enums.ObjectDeleteStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "object_delete_outbox")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class ObjectDeleteOutbox {

	public static final int MAX_RETRY_COUNT = 5;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 255)
	private String bucket;

	@Column(name = "object_key", columnDefinition = "text")
	private String objectKey;

	@Column
	private Long mediaId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ObjectDeleteStatus status;

	@Column(nullable = false)
	private int retryCount;

	@Column(columnDefinition = "text")
	private String lastError;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	public void markFailed(String lastError) {
		this.retryCount++;
		this.status = retryCount >= MAX_RETRY_COUNT
				? ObjectDeleteStatus.EXHAUSTED
				: ObjectDeleteStatus.FAILED;
		this.lastError = lastError;
	}

	@PrePersist
	protected void onCreate() {
		if (status == null) {
			status = ObjectDeleteStatus.PENDING;
		}
		createdAt = LocalDateTime.now();
		updatedAt = LocalDateTime.now();
	}

	@PreUpdate
	protected void onUpdate() {
		updatedAt = LocalDateTime.now();
	}
}
