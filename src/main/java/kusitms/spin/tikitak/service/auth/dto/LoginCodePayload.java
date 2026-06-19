package kusitms.spin.tikitak.service.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginCodePayload {

	private Long memberId;
	private boolean newMember;
	private boolean agreedRequiredTerms;
	private Long activeTeamId;
}
