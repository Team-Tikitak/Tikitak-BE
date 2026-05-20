package kusitms.spin.tikitak.service.me.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import kusitms.spin.tikitak.domain.member.enums.ProfileCharacterType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class MeRequestDTO {

	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class ActiveTeamUpdateRequestDTO {
		@NotNull
		@Positive
		private Long teamId;
	}

	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class AgreementUpdateRequestDTO {
		@NotNull
		private Boolean termsAgreed;

		@NotNull
		private Boolean privacyAgreed;
	}

	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class OnboardingUpdateRequestDTO {
		@NotNull
		private ProfileCharacterType profileCharacterType;
	}
}
