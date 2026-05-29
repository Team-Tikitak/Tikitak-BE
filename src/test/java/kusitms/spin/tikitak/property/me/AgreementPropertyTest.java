package kusitms.spin.tikitak.property.me;

import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.repository.member.MemberRepository;
import kusitms.spin.tikitak.repository.team.TeamMemberRepository;
import kusitms.spin.tikitak.service.auth.TokenService;
import kusitms.spin.tikitak.service.me.ActiveTeamService;
import kusitms.spin.tikitak.service.me.DefaultProfileImageResolver;
import kusitms.spin.tikitak.service.me.MeService;
import kusitms.spin.tikitak.service.me.dto.MeRequestDTO;
import kusitms.spin.tikitak.service.me.dto.MeResponseDTO;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

import java.util.Optional;

import static kusitms.spin.tikitak.support.fixture.MemberFixture.activeMember;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgreementPropertyTest {

	@Property
	void agreementUpdateSucceedsOnlyWhenAllRequiredTermsAreTrue(
			@ForAll boolean termsAgreed,
			@ForAll boolean privacyAgreed
	) {
		MeService meService = meServiceWithActiveMember();
		MeRequestDTO.AgreementUpdateRequestDTO request =
				new MeRequestDTO.AgreementUpdateRequestDTO(termsAgreed, privacyAgreed);

		if (termsAgreed && privacyAgreed) {
			MeResponseDTO.AgreementResponseDTO response = meService.updateAgreements(1L, request);

			assertThat(response.isTermsAgreed()).isTrue();
			assertThat(response.isPrivacyAgreed()).isTrue();
			assertThat(response.getTermsAgreedAt()).isNotNull();
			return;
		}

		assertThatThrownBy(() -> meService.updateAgreements(1L, request))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AGREEMENT002));
	}

	private MeService meServiceWithActiveMember() {
		Member member = activeMember(1L);
		MemberRepository memberRepository = mock(MemberRepository.class);
		when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

		return new MeService(
				memberRepository,
				mock(TeamMemberRepository.class),
				mock(TokenService.class),
				mock(ActiveTeamService.class),
				mock(DefaultProfileImageResolver.class)
		);
	}
}
