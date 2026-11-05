package kusitms.spin.tikitak.service.auth;

import kusitms.spin.tikitak.domain.member.enums.SocialProvider;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.service.auth.dto.LoginCodePayload;
import kusitms.spin.tikitak.service.auth.dto.LoginResponse;
import kusitms.spin.tikitak.service.auth.dto.OAuthAuthorizeUrlResponse;
import kusitms.spin.tikitak.service.auth.dto.OAuthUserInfo;
import kusitms.spin.tikitak.service.auth.dto.TokenResponse;
import kusitms.spin.tikitak.service.auth.exception.OAuthAuthenticationException;
import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.net.URI;
import java.util.Optional;

import static kusitms.spin.tikitak.support.fixture.AuthFixture.kakaoUserInfo;
import static kusitms.spin.tikitak.support.fixture.AuthFixture.tokenResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
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
	private OAuthMemberLoginService oauthMemberLoginService;

	@Mock
	private LoginCodeStore loginCodeStore;

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
	@DisplayName("OAuth 로그인은 외부 사용자 정보를 받은 뒤 회원 로그인 처리를 OAuthMemberLoginService에 위임한다")
	void loginWithOAuthDelegatesMemberLoginAfterFetchingUserInfo() {
		OAuthUserInfo userInfo = kakaoUserInfo();
		LoginResponse loginResponse = new LoginResponse("access-token", "refresh-token", false, true, 10L);
		when(oauthStateStore.consume("state", SocialProvider.KAKAO.name())).thenReturn(true);
		when(kakaoOAuthService.getUserInfo("code")).thenReturn(userInfo);
		when(oauthMemberLoginService.loginWithToken(userInfo)).thenReturn(loginResponse);

		LoginResponse response = authService.loginWithOAuth("kakao", "code", "state", "state");

		assertThat(response).isEqualTo(loginResponse);
		InOrder inOrder = inOrder(oauthStateStore, kakaoOAuthService, oauthMemberLoginService);
		inOrder.verify(oauthStateStore).consume("state", SocialProvider.KAKAO.name());
		inOrder.verify(kakaoOAuthService).getUserInfo("code");
		inOrder.verify(oauthMemberLoginService).loginWithToken(userInfo);
	}

	@Test
	@DisplayName("OAuth 외부 인증이 실패하면 AUTH104 예외가 발생하고 회원 로그인 처리를 하지 않는다")
	void loginWithOAuthThrowsAuth104WhenOAuthAuthenticationFails() {
		when(oauthStateStore.consume("state", SocialProvider.KAKAO.name())).thenReturn(true);
		when(kakaoOAuthService.getUserInfo("code")).thenThrow(new OAuthAuthenticationException("kakao failed"));

		assertThatThrownBy(() -> authService.loginWithOAuth("kakao", "code", "state", "state"))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH104));
		verify(oauthMemberLoginService, never()).loginWithToken(any());
	}

	@Test
	@DisplayName("회원 로그인 처리가 BusinessException을 던지면 그대로 전파한다")
	void loginWithOAuthRethrowsBusinessExceptionFromMemberLogin() {
		OAuthUserInfo userInfo = kakaoUserInfo();
		when(oauthStateStore.consume("state", SocialProvider.KAKAO.name())).thenReturn(true);
		when(kakaoOAuthService.getUserInfo("code")).thenReturn(userInfo);
		when(oauthMemberLoginService.loginWithToken(userInfo)).thenThrow(new BusinessException(ErrorCode.AUTH007));

		assertThatThrownBy(() -> authService.loginWithOAuth("kakao", "code", "state", "state"))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH007));
	}

	@Test
	@DisplayName("회원 로그인 처리가 그 밖의 예외를 던지면 AUTH105 예외로 바꾼다")
	void loginWithOAuthWrapsUnexpectedExceptionFromMemberLogin() {
		OAuthUserInfo userInfo = kakaoUserInfo();
		when(oauthStateStore.consume("state", SocialProvider.KAKAO.name())).thenReturn(true);
		when(kakaoOAuthService.getUserInfo("code")).thenReturn(userInfo);
		when(oauthMemberLoginService.loginWithToken(userInfo)).thenThrow(new RuntimeException("db failed"));

		assertThatThrownBy(() -> authService.loginWithOAuth("kakao", "code", "state", "state"))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH105));
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
		verify(kakaoOAuthService, never()).getUserInfo(any());
		verify(oauthMemberLoginService, never()).loginWithToken(any());
	}

	@Test
	@DisplayName("앱 OAuth 로그인은 회원 로그인 처리가 끝난 뒤 loginCode를 발급하고 저장한다")
	void issueAppLoginCodeSavesLoginCodeAndReturnsCode() {
		OAuthUserInfo userInfo = kakaoUserInfo();
		LoginCodePayload payload = LoginCodePayload.builder()
				.memberId(1L)
				.newMember(false)
				.agreedRequiredTerms(true)
				.activeTeamId(10L)
				.build();
		when(oauthStateStore.consume("state", SocialProvider.KAKAO.name())).thenReturn(true);
		when(kakaoOAuthService.getUserInfo("code")).thenReturn(userInfo);
		when(oauthMemberLoginService.prepareLoginCode(userInfo)).thenReturn(payload);

		String loginCode = authService.issueAppLoginCode("kakao", "code", "state", "state");

		assertThat(loginCode).isNotBlank().hasSize(64);
		InOrder inOrder = inOrder(kakaoOAuthService, oauthMemberLoginService, loginCodeStore);
		inOrder.verify(kakaoOAuthService).getUserInfo("code");
		inOrder.verify(oauthMemberLoginService).prepareLoginCode(userInfo);
		inOrder.verify(loginCodeStore).save(loginCode, payload);
		verify(tokenService, never()).issueToken(any());
	}

	@Test
	@DisplayName("앱 OAuth 로그인에서 loginCode 저장이 실패하면 AUTH105 예외가 발생한다")
	void issueAppLoginCodeThrowsAuth105WhenLoginCodeSaveFails() {
		OAuthUserInfo userInfo = kakaoUserInfo();
		LoginCodePayload payload = LoginCodePayload.builder().memberId(1L).build();
		when(oauthStateStore.consume("state", SocialProvider.KAKAO.name())).thenReturn(true);
		when(kakaoOAuthService.getUserInfo("code")).thenReturn(userInfo);
		when(oauthMemberLoginService.prepareLoginCode(userInfo)).thenReturn(payload);
		doThrow(new RuntimeException("redis failed")).when(loginCodeStore).save(any(), eq(payload));

		assertThatThrownBy(() -> authService.issueAppLoginCode("kakao", "code", "state", "state"))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH105));
	}

	@Test
	@DisplayName("앱 OAuth 로그인에서 회원 로그인 처리가 실패하면 loginCode를 저장하지 않는다")
	void issueAppLoginCodeDoesNotSaveLoginCodeWhenMemberLoginFails() {
		OAuthUserInfo userInfo = kakaoUserInfo();
		when(oauthStateStore.consume("state", SocialProvider.KAKAO.name())).thenReturn(true);
		when(kakaoOAuthService.getUserInfo("code")).thenReturn(userInfo);
		when(oauthMemberLoginService.prepareLoginCode(userInfo)).thenThrow(new RuntimeException("commit failed"));

		assertThatThrownBy(() -> authService.issueAppLoginCode("kakao", "code", "state", "state"))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH105));
		verify(loginCodeStore, never()).save(any(), any());
	}

	@Test
	@DisplayName("유효한 loginCode 교환은 access token과 refresh token을 반환한다")
	void exchangeLoginCodeReturnsTokensForValidCode() {
		LoginCodePayload payload = LoginCodePayload.builder()
				.memberId(1L)
				.newMember(true)
				.agreedRequiredTerms(false)
				.activeTeamId(null)
				.build();
		TokenResponse token = tokenResponse();
		when(loginCodeStore.consume("validcode")).thenReturn(Optional.of(payload));
		when(tokenService.issueToken(1L)).thenReturn(token);

		LoginResponse response = authService.exchangeLoginCode("validcode");

		assertThat(response.accessToken()).isEqualTo("access-token");
		assertThat(response.refreshToken()).isEqualTo("refresh-token");
		assertThat(response.isNewMember()).isTrue();
		assertThat(response.hasAgreedRequiredTerms()).isFalse();
		assertThat(response.activeTeamId()).isNull();
		verify(loginCodeStore).consume("validcode");
	}

	@Test
	@DisplayName("존재하지 않는 loginCode 교환은 AUTH106 예외가 발생한다")
	void exchangeLoginCodeThrowsWhenCodeNotFound() {
		when(loginCodeStore.consume("unknown")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authService.exchangeLoginCode("unknown"))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH106));
	}

	@Test
	@DisplayName("이미 소비된 loginCode 재교환은 AUTH106 예외가 발생한다")
	void exchangeLoginCodeThrowsWhenCodeAlreadyConsumed() {
		LoginCodePayload payload = LoginCodePayload.builder()
				.memberId(1L)
				.newMember(false)
				.agreedRequiredTerms(true)
				.activeTeamId(null)
				.build();
		when(loginCodeStore.consume("usedcode"))
				.thenReturn(Optional.of(payload))
				.thenReturn(Optional.empty());
		when(tokenService.issueToken(1L)).thenReturn(tokenResponse());

		authService.exchangeLoginCode("usedcode");

		assertThatThrownBy(() -> authService.exchangeLoginCode("usedcode"))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH106));
	}

	@Test
	@DisplayName("토큰 발급이 실패하면 소비했던 loginCode를 복구해 재시도할 수 있게 한다")
	void exchangeLoginCodeRestoresCodeWhenTokenIssuanceFails() {
		LoginCodePayload payload = LoginCodePayload.builder()
				.memberId(1L)
				.newMember(true)
				.agreedRequiredTerms(false)
				.activeTeamId(10L)
				.build();
		when(loginCodeStore.consume("validcode")).thenReturn(Optional.of(payload));
		when(tokenService.issueToken(1L)).thenThrow(new RuntimeException("token issuance failed"));

		assertThatThrownBy(() -> authService.exchangeLoginCode("validcode"))
				.isInstanceOf(RuntimeException.class);

		verify(loginCodeStore).save("validcode", payload);
	}
}
