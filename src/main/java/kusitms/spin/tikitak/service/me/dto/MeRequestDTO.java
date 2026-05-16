package kusitms.spin.tikitak.service.me.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class MeRequestDTO {

	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class ActiveTeamUpdateRequestDTO {
		@NotNull
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
}
