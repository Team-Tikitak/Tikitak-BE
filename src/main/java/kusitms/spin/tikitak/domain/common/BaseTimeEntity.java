package kusitms.spin.tikitak.domain.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;

@MappedSuperclass
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class BaseTimeEntity extends BaseCreatedTimeEntity {

	@LastModifiedDate
	@Column(nullable = false)
	private LocalDateTime updatedAt;

	/**
	 * 도메인 수정 메서드에서 updatedAt을 즉시 갱신한다.
	 * flush 전에 응답으로 updatedAt을 내보내거나, 연관 컬렉션만 바뀌어 엔티티 자체는 dirty가 아닌 경우에도 UPDATE가 나가게 하기 위해 쓴다.
	 */
	protected void touch() {
		this.updatedAt = LocalDateTime.now();
	}
}
