package kusitms.spin.tikitak.domain.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kusitms.spin.tikitak.domain.common.BaseTimeEntity;
import kusitms.spin.tikitak.domain.member.enums.MemberStatus;
import kusitms.spin.tikitak.domain.member.enums.ProfileCharacterType;
import kusitms.spin.tikitak.domain.member.enums.SocialProvider;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "member")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {

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
	@Column(length = 50)
	private ProfileCharacterType profileCharacterType;

	@Column(nullable = false)
	private boolean onboardingCompleted;

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

	private Long activeTeamId;

	private LocalDateTime deletedAt;

	public static Member createSocialMember(
			String email,
			String name,
			String nickname,
			String profileImgUrl,
			SocialProvider socialProvider,
			String providerId
	) {
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
				.onboardingCompleted(false)
				.build();
	}

	public void updateSocialProfile(String email, String name, String profileImgUrl) {
		this.email = email;
		this.name = name;
		this.profileImgUrl = profileImgUrl;
	}

	public void withdraw() {
		LocalDateTime now = LocalDateTime.now();
		this.status = MemberStatus.INACTIVE;
		this.activeTeamId = null;
		this.providerId = "WITHDRAWN:" + this.id + ":" + UUID.randomUUID();
		this.deletedAt = now;
	}

	public void clearProfileImage() {
		this.profileImgUrl = null;
	}

	public void changeActiveTeam(Long teamId) {
		this.activeTeamId = teamId;
	}

	public void clearActiveTeam() {
		this.activeTeamId = null;
	}

	public void agreeRequiredTerms() {
		this.termsAgreed = true;
		this.privacyAgreed = true;
		if (this.termsAgreedAt == null) {
			this.termsAgreedAt = LocalDateTime.now();
		}
	}

	public void completeOnboarding(ProfileCharacterType profileCharacterType) {
		this.profileCharacterType = Objects.requireNonNull(profileCharacterType, "profileCharacterType must not be null");
		this.onboardingCompleted = true;
	}
}
