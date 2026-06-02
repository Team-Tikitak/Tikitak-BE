package kusitms.spin.tikitak.service.me;

import kusitms.spin.tikitak.domain.member.enums.ProfileCharacterType;
import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import kusitms.spin.tikitak.global.config.R2Properties;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.service.media.ImagePreset;
import kusitms.spin.tikitak.service.media.ImageUrlResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultProfileImageResolver {

	private final R2Properties r2Properties;
	private final ImageUrlResolver imageUrlResolver;

	/**
	 * Used by profile-image fallback flows to convert a fixed character type into a public R2 URL.
	 */
	public String resolve(ProfileCharacterType profileCharacterType) {
		String publicBaseUrl = r2Properties.getPublicBaseUrl();
		if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
			throw new BusinessException(ErrorCode.ME011);
		}

		ProfileCharacterType effective = profileCharacterType != null
				? profileCharacterType
				: ProfileCharacterType.TAK_LEADER;

		return imageUrlResolver.resolve(
				publicBaseUrl.replaceAll("/+$", "") + "/" + effective.getImagePath(),
				ImagePreset.PROFILE_AVATAR
		);
	}

	public String resolveForTeamMember(TeamMember teamMember) {
		if (teamMember.getProfileImgUrl() != null && !teamMember.getProfileImgUrl().isBlank()) {
			return imageUrlResolver.resolve(teamMember.getProfileImgUrl(), ImagePreset.PROFILE_AVATAR);
		}
		return resolve(teamMember.getMember().getProfileCharacterType());
	}
}
