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
import kusitms.spin.tikitak.domain.common.BaseTimeEntity;
import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.notification.enums.DevicePlatform;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "member_device_token")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberDeviceToken extends BaseTimeEntity {

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

	public static MemberDeviceToken register(Member member, String fcmToken, DevicePlatform platform) {
		return MemberDeviceToken.builder()
				.member(member)
				.fcmToken(fcmToken)
				.platform(platform)
				.build();
	}

	public void reassign(Member member, DevicePlatform platform) {
		this.member = member;
		this.platform = platform;
	}
}
