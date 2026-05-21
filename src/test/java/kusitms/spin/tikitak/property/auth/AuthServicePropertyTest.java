package kusitms.spin.tikitak.property.auth;

import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.repository.member.MemberRepository;
import kusitms.spin.tikitak.service.auth.AuthService;
import kusitms.spin.tikitak.service.auth.AppleOAuthService;
import kusitms.spin.tikitak.service.auth.GoogleOAuthService;
import kusitms.spin.tikitak.service.auth.KakaoOAuthService;
import kusitms.spin.tikitak.service.auth.OAuthStateStore;
import kusitms.spin.tikitak.service.auth.TokenService;
import kusitms.spin.tikitak.service.me.ActiveTeamService;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class AuthServicePropertyTest {

	private final AuthService authService = new AuthService(
			mock(GoogleOAuthService.class),
			mock(KakaoOAuthService.class),
			mock(AppleOAuthService.class),
			mock(OAuthStateStore.class),
			mock(TokenService.class),
			mock(MemberRepository.class),
			mock(ActiveTeamService.class)
	);

	@Property
	void unsupportedProviderAlwaysThrowsAuth101(@ForAll("invalidProviders") String provider) {
		assertThatThrownBy(() -> authService.loginWithOAuth(provider, "code", "state", "state"))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH101));
	}

	@Property
	void mismatchedCallbackStateAlwaysThrowsAuth103(
			@ForAll("supportedProviders") String provider,
			@ForAll("nonBlankStrings") String code,
			@ForAll("nonBlankStrings") String state,
			@ForAll("nonBlankStrings") String savedState
	) {
		Assume.that(!state.equals(savedState));

		assertThatThrownBy(() -> authService.loginWithOAuth(provider, code, state, savedState))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH103));
	}

	@Provide
	Arbitrary<String> invalidProviders() {
		return Arbitraries.strings()
				.withCharRange('a', 'z')
				.ofMinLength(0)
				.ofMaxLength(20)
				.filter(provider -> !provider.equalsIgnoreCase("kakao"))
				.filter(provider -> !provider.equalsIgnoreCase("google"))
				.filter(provider -> !provider.equalsIgnoreCase("apple"));
	}

	@Provide
	Arbitrary<String> supportedProviders() {
		return Arbitraries.of("kakao", "KAKAO", "google", "GOOGLE", "apple", "APPLE");
	}

	@Provide
	Arbitrary<String> nonBlankStrings() {
		return Arbitraries.strings()
				.withCharRange('a', 'z')
				.ofMinLength(1)
				.ofMaxLength(20);
	}
}
