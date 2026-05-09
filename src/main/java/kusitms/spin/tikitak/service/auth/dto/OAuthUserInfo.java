package kusitms.spin.tikitak.service.auth.dto;

import kusitms.spin.tikitak.domain.member.enums.SocialProvider;

public record OAuthUserInfo(
		SocialProvider provider,
		String providerId,
		String email,
		String name,
		String profileImageUrl
) {
}
