package kusitms.spin.tikitak.service.auth;

import kusitms.spin.tikitak.domain.auth.LoginCode;
import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.member.enums.SocialProvider;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.repository.auth.LoginCodeRepository;
import kusitms.spin.tikitak.repository.member.MemberRepository;
import kusitms.spin.tikitak.service.auth.dto.LoginResponse;
import kusitms.spin.tikitak.service.auth.dto.OAuthAuthorizeUrlResponse;
import kusitms.spin.tikitak.service.auth.dto.OAuthUserInfo;
import kusitms.spin.tikitak.service.auth.dto.TokenResponse;
import kusitms.spin.tikitak.service.me.ActiveTeamService;
import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.Optional;
import java.net.URI;

import static kusitms.spin.tikitak.support.fixture.AuthFixture.kakaoUserInfo;
import static kusitms.spin.tikitak.support.fixture.AuthFixture.tokenResponse;
import static kusitms.spin.tikitak.support.fixture.MemberFixture.activeMemberWithActiveTeam;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest extends UnitTest {

	@Mock
	private GoogleOAuthService googleOAuthService;

	@Mock
	private KakaoOAuthService kakaoOAuthService;

	@Mock
	private AppleOAuthService appleOAuthService;

	@Mock
	private OAuthStateStore oauthStateStore;

	@Mock
	private TokenService tokenService;

	@Mock
	private MemberRepository memberRepository;

	@Mock
	private ActiveTeamService activeTeamService;

	@Mock
	private LoginCodeRepository loginCodeRepository;

	@InjectMocks
	private AuthService authService;

	@Test
	@DisplayName("OAuth 시작 URL 조회는 provider에 맞는 OAuthService로 위임한다")
	void getAuthorizeUrlDelegatesToProviderService() {
		when(kakaoOAuthService.getAuthorizeUrl(any(String.class))).thenReturn(new OAuthAuthorizeUrlResponse(
				URI.create("https://kauth.kakao.com/oauth/authorize"),
				"state"
		));

		OAuthAuthorizeUrlResponse response = authService.getAuthorizeUrl("kakao");

		assertThat(response.authorizeUrl()).hasToString("https://kauth.kakao.com/oauth/authorize");
		assertThat(response.state()).isEqualTo("state");
		verify(kakaoOAuthService).getAuthorizeUrl(any(String.class));
	}

	@Test
	@DisplayName("refreshToken은 TokenService에 재발급을 위임한다")
	void refreshTokenDelegatesToTokenService() {
		TokenResponse token = tokenResponse();
		when(tokenService.reissueToken("refresh-token")).thenReturn(token);

		TokenResponse response = authService.refreshToken("refresh-token");

		assertThat(response).isEqualTo(token);
		verify(tokenService).reissueToken("refresh-token");
	}

	@Test
	@DisplayName("logout은 TokenService에 refresh token 폐기를 위임한다")
	void logoutDelegatesToTokenService() {
		authService.logout("refresh-token");

		verify(tokenService).revokeRefreshToken("refresh-token");
	}

	@Test
	@DisplayName("기존 회원 OAuth 로그인은 토큰과 실제 활성 팀 ID를 반환한다")
	void loginWithOAuthReturnsActiveTeamIdForExistingMember() {
		OAuthUserInfo userInfo = kakaoUserInfo();
		Member member = activeMemberWithActiveTeam(1L, 10L);
		TokenResponse token = tokenResponse();
		when(kakaoOAuthService.getUserInfo("code")).thenReturn(userInfo);
		when(memberRepository.findBySocialProviderAndProviderIdAndStatus(
				SocialProvider.KAKAO,
				"kakao-provider-id",
				kusitms.spin.tikitak.domain.member.enums.MemberStatus.ACTIVE
		))
				.thenReturn(Optional.of(member));
		when(tokenService.issueToken(1L)).thenReturn(token);
		when(activeTeamService.resolveActiveTeamId(member)).thenReturn(10L);

		LoginResponse response = authService.loginWithOAuth("kakao", "code", "state", "state");

		assertThat(response.accessToken()).isEqualTo("access-token");
		assertThat(response.refreshToken()).isEqualTo("refresh-token");
		assertThat(response.isNewMember()).isFalse();
		assertThat(response.activeTeamId()).isEqualTo(10L);
		assertThat(member.getEmail()).isEqualTo("user@example.com");
		assertThat(member.getName()).isEqualTo("Kakao User");
		assertThat(member.getProfileImgUrl()).isEqualTo("https://example.com/profile.png");
	}

	@Test
	@DisplayName("신규 OAuth 회원은 기본 정보로 저장되고 신규 회원으로 응답한다")
	void loginWithOAuthCreatesNewMember() {
		OAuthUserInfo userInfo = kakaoUserInfo();
		TokenResponse token = tokenResponse();
		when(kakaoOAuthService.getUserInfo("code")).thenReturn(userInfo);
		when(memberRepository.findBySocialProviderAndProviderIdAndStatus(
				SocialProvider.KAKAO,
				"kakao-provider-id",
				kusitms.spin.tikitak.domain.member.enums.MemberStatus.ACTIVE
		))
				.thenReturn(Optional.empty());
		when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> {
			Member saved = invocation.getArgument(0);
			return Member.builder()
					.id(1L)
					.email(saved.getEmail())
					.name(saved.getName())
					.nickname(saved.getNickname())
					.profileImgUrl(saved.getProfileImgUrl())
					.socialProvider(saved.getSocialProvider())
					.providerId(saved.getProviderId())
					.status(saved.getStatus())
					.termsAgreed(saved.isTermsAgreed())
					.privacyAgreed(saved.isPrivacyAgreed())
					.termsAgreedAt(saved.getTermsAgreedAt())
					.activeTeamId(saved.getActiveTeamId())
					.createdAt(saved.getCreatedAt())
					.updatedAt(saved.getUpdatedAt())
					.build();
		});
		when(tokenService.issueToken(1L)).thenReturn(token);
		when(activeTeamService.resolveActiveTeamId(any(Member.class))).thenReturn(null);

		LoginResponse response = authService.loginWithOAuth("kakao", "code", "state", "state");

		assertThat(response.isNewMember()).isTrue();
		assertThat(response.hasAgreedRequiredTerms()).isFalse();
		assertThat(response.activeTeamId()).isNull();
		verify(memberRepository).save(any(Member.class));
	}

	@Test
	@DisplayName("지원하지 않는 OAuth provider는 AUTH101 예외가 발생한다")
	void loginWithOAuthThrowsWhenUnsupportedProvider() {
		assertThatThrownBy(() -> authService.loginWithOAuth("naver", "code", "state", "state"))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH101));
	}

	@Test
	@DisplayName("OAuth callback state가 일치하지 않으면 AUTH103 예외가 발생한다")
	void loginWithOAuthThrowsWhenStateMismatch() {
		assertThatThrownBy(() -> authService.loginWithOAuth("kakao", "code", "state", "other-state"))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH103));
	}

	@Test
	@DisplayName("앱 OAuth 로그인은 loginCode를 발급하고 저장한다")
	void issueAppLoginCodeSavesLoginCodeAndReturnsCode() {
		OAuthUserInfo userInfo = kakaoUserInfo();
		Member member = activeMemberWithActiveTeam(1L, 10L);
		when(kakaoOAuthService.getUserInfo("code")).thenReturn(userInfo);
		when(memberRepository.findBySocialProviderAndProviderIdAndStatus(
				SocialProvider.KAKAO,
				"kakao-provider-id",
				kusitms.spin.tikitak.domain.member.enums.MemberStatus.ACTIVE
		)).thenReturn(Optional.of(member));
		when(activeTeamService.resolveActiveTeamId(member)).thenReturn(10L);
		when(loginCodeRepository.save(any(LoginCode.class))).thenAnswer(i -> i.getArgument(0));

		String loginCode = authService.issueAppLoginCode("kakao", "code", "state", "state");

		assertThat(loginCode).isNotBlank().hasSize(64);
		verify(loginCodeRepository).save(any(LoginCode.class));
	}

	@Test
	@DisplayName("유효한 loginCode 교환은 access token과 refresh token을 반환한다")
	void exchangeLoginCodeReturnsTokensForValidCode() {
		LoginCode loginCode = LoginCode.builder()
				.code("validcode")
				.memberId(1L)
				.newMember(true)
				.agreedRequiredTerms(false)
				.activeTeamId(null)
				.expiresAt(LocalDateTime.now().plusMinutes(5))
				.used(false)
				.createdAt(LocalDateTime.now())
				.build();
		TokenResponse token = tokenResponse();
		when(loginCodeRepository.findByCodeForUpdate("validcode")).thenReturn(Optional.of(loginCode));
		when(tokenService.issueToken(1L)).thenReturn(token);

		LoginResponse response = authService.exchangeLoginCode("validcode");

		assertThat(response.accessToken()).isEqualTo("access-token");
		assertThat(response.refreshToken()).isEqualTo("refresh-token");
		assertThat(response.isNewMember()).isTrue();
		assertThat(response.hasAgreedRequiredTerms()).isFalse();
		assertThat(response.activeTeamId()).isNull();
		assertThat(loginCode.isUsed()).isTrue();
	}

	@Test
	@DisplayName("존재하지 않는 loginCode 교환은 AUTH106 예외가 발생한다")
	void exchangeLoginCodeThrowsWhenCodeNotFound() {
		when(loginCodeRepository.findByCodeForUpdate("unknown")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authService.exchangeLoginCode("unknown"))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH106));
	}

	@Test
	@DisplayName("만료된 loginCode 교환은 AUTH107 예외가 발생한다")
	void exchangeLoginCodeThrowsWhenCodeExpired() {
		LoginCode expiredLoginCode = LoginCode.builder()
				.code("expiredcode")
				.memberId(1L)
				.newMember(false)
				.agreedRequiredTerms(true)
				.activeTeamId(null)
				.expiresAt(LocalDateTime.now().minusMinutes(1))
				.used(false)
				.createdAt(LocalDateTime.now().minusMinutes(10))
				.build();
		when(loginCodeRepository.findByCodeForUpdate("expiredcode")).thenReturn(Optional.of(expiredLoginCode));

		assertThatThrownBy(() -> authService.exchangeLoginCode("expiredcode"))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH107));
	}

	@Test
	@DisplayName("이미 사용된 loginCode 교환은 AUTH108 예외가 발생한다")
	void exchangeLoginCodeThrowsWhenCodeAlreadyUsed() {
		LoginCode usedLoginCode = LoginCode.builder()
				.code("usedcode")
				.memberId(1L)
				.newMember(false)
				.agreedRequiredTerms(true)
				.activeTeamId(null)
				.expiresAt(LocalDateTime.now().plusMinutes(5))
				.used(true)
				.createdAt(LocalDateTime.now())
				.build();
		when(loginCodeRepository.findByCodeForUpdate("usedcode")).thenReturn(Optional.of(usedLoginCode));

		assertThatThrownBy(() -> authService.exchangeLoginCode("usedcode"))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH108));
	}
}
