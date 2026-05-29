package kusitms.spin.tikitak.support.fixture;

import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.member.enums.MemberStatus;
import kusitms.spin.tikitak.domain.member.enums.ProfileCharacterType;
import kusitms.spin.tikitak.domain.member.enums.SocialProvider;

import java.time.LocalDateTime;

public final class MemberFixture {

	private MemberFixture() {
	}

	public static Member activeMember(Long id) {
		return member(id, MemberStatus.ACTIVE, null);
	}

	public static Member activeMemberWithCharacterType(Long id, ProfileCharacterType characterType) {
		LocalDateTime now = LocalDateTime.of(2026, 3, 4, 20, 30);
		return Member.builder()
				.id(id)
				.email("user" + id + "@example.com")
				.name("User " + id)
				.nickname("user" + id)
				.profileCharacterType(characterType)
				.socialProvider(SocialProvider.KAKAO)
				.providerId("provider-" + id)
				.status(MemberStatus.ACTIVE)
				.termsAgreed(false)
				.privacyAgreed(false)
				.createdAt(now)
				.updatedAt(now)
				.build();
	}

	public static Member activeMemberWithActiveTeam(Long id, Long activeTeamId) {
		return member(id, MemberStatus.ACTIVE, activeTeamId);
	}

	public static Member inactiveMember(Long id) {
		return member(id, MemberStatus.INACTIVE, null);
	}

	public static Member member(Long id, MemberStatus status, Long activeTeamId) {
		LocalDateTime now = LocalDateTime.of(2026, 3, 4, 20, 30);
		return Member.builder()
				.id(id)
				.email("user" + id + "@example.com")
				.name("User " + id)
				.nickname("user" + id)
				.profileImgUrl("https://example.com/profile" + id + ".png")
				.socialProvider(SocialProvider.KAKAO)
				.providerId("provider-" + id)
				.status(status)
				.termsAgreed(false)
				.privacyAgreed(false)
				.activeTeamId(activeTeamId)
				.createdAt(now)
				.updatedAt(now)
				.build();
	}
}
