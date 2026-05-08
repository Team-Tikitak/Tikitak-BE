package kusitms.spin.tikitak.domain.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kusitms.spin.tikitak.domain.member.enums.MemberStatus;
import kusitms.spin.tikitak.domain.member.enums.SocialProvider;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "member")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(columnDefinition = "text")
	private String email;

	@Column(length = 20)
	private String name;

	@Column(length = 30)
	private String nickname;

	@Column(columnDefinition = "text")
	private String profileImgUrl;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50)
	private SocialProvider socialProvider;

	@Column(columnDefinition = "text")
	private String providerId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50)
	private MemberStatus status;

	@Column(nullable = false)
	private boolean termsAgreed;

	@Column(nullable = false)
	private boolean privacyAgreed;

	private LocalDateTime termsAgreedAt;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	private LocalDateTime deletedAt;

	public static Member createSocialMember(
			String email,
			String name,
			String nickname,
			String profileImgUrl,
			SocialProvider socialProvider,
			String providerId
	) {
		LocalDateTime now = LocalDateTime.now();
		return Member.builder()
				.email(email)
				.name(name)
				.nickname(nickname)
				.profileImgUrl(profileImgUrl)
				.socialProvider(socialProvider)
				.providerId(providerId)
				.status(MemberStatus.ACTIVE)
				.termsAgreed(false)
				.privacyAgreed(false)
				.createdAt(now)
				.updatedAt(now)
				.build();
	}

	public void updateSocialProfile(String email, String name, String profileImgUrl) {
		this.email = email;
		this.name = name;
		this.profileImgUrl = profileImgUrl;
		this.updatedAt = LocalDateTime.now();
	}

	public void withdraw() {
		LocalDateTime now = LocalDateTime.now();
		this.status = MemberStatus.INACTIVE;
		this.deletedAt = now;
		this.updatedAt = now;
	}
}
