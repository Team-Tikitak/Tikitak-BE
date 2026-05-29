package kusitms.spin.tikitak.service.me;

import kusitms.spin.tikitak.domain.member.enums.ProfileCharacterType;
import kusitms.spin.tikitak.global.config.R2Properties;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultProfileImageResolver {

	private final R2Properties r2Properties;

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

		return publicBaseUrl.replaceAll("/+$", "") + "/" + effective.getImagePath();
	}
}
