package kusitms.spin.tikitak.domain.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.notification.enums.DevicePlatform;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "member_device_token")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberDeviceToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@Column(nullable = false, unique = true, length = 512)
	private String fcmToken;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private DevicePlatform platform;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	public static MemberDeviceToken register(Member member, String fcmToken, DevicePlatform platform) {
		LocalDateTime now = LocalDateTime.now();
		return MemberDeviceToken.builder()
				.member(member)
				.fcmToken(fcmToken)
				.platform(platform)
				.createdAt(now)
				.updatedAt(now)
				.build();
	}

	public void reassign(Member member, DevicePlatform platform) {
		this.member = member;
		this.platform = platform;
		this.updatedAt = LocalDateTime.now();
	}
}
