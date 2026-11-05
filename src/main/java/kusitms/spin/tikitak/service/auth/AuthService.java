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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

	private final GoogleOAuthService googleOAuthService;
	private final KakaoOAuthService kakaoOAuthService;
	private final AppleOAuthService appleOAuthService;
	private final OAuthStateStore oauthStateStore;
	private final TokenService tokenService;
	private final OAuthMemberLoginService oauthMemberLoginService;
	private final LoginCodeStore loginCodeStore;
	private final SecureRandom secureRandom = new SecureRandom();

	public OAuthAuthorizeUrlResponse getAuthorizeUrl(String provider) {
		SocialProvider socialProvider = parseProvider(provider);
		try {
			String state = createState();
			oauthStateStore.save(state, socialProvider.name());
			return switch (socialProvider) {
				case GOOGLE -> googleOAuthService.getAuthorizeUrl(state);
				case KAKAO -> kakaoOAuthService.getAuthorizeUrl(state);
				case APPLE -> appleOAuthService.getAuthorizeUrl(state);
			};
		} catch (BusinessException e) {
			throw e;
		} catch (Exception e) {
			log.warn("OAuth start failed. provider={}, reason={}", provider, e.getMessage());
			throw new BusinessException(ErrorCode.AUTH102);
		}
	}

	public TokenResponse refreshToken(String refreshToken) {
		try {
			return tokenService.reissueToken(refreshToken);
		} catch (BusinessException e) {
			throw e;
		} catch (Exception e) {
			throw new BusinessException(ErrorCode.AUTH008);
		}
	}

	public void logout(String refreshToken) {
		tokenService.revokeRefreshToken(refreshToken);
	}

	public LoginResponse loginWithOAuth(String provider, String code, String state, String savedState) {
		return loginWithOAuth(provider, code, state, savedState, null);
	}

	/**
	 * 외부 OAuth 호출은 트랜잭션 밖에서 하고, DB 작업은 {@link OAuthMemberLoginService}의 트랜잭션 하나에서 한다.
	 */
	public LoginResponse loginWithOAuth(String provider, String code, String state, String savedState, String idToken) {
		try {
			OAuthUserInfo userInfo = fetchOAuthUserInfo(provider, code, state, savedState, idToken);
			return oauthMemberLoginService.loginWithToken(userInfo);
		} catch (OAuthAuthenticationException e) {
			log.warn("OAuth authentication failed. provider={}, reason={}", provider, e.getMessage());
			throw new BusinessException(ErrorCode.AUTH104);
		} catch (BusinessException e) {
			throw e;
		} catch (Exception e) {
			throw new BusinessException(ErrorCode.AUTH105);
		}
	}

	public String issueAppLoginCode(String provider, String code, String state, String savedState) {
		return issueAppLoginCode(provider, code, state, savedState, null);
	}

	/**
	 * 외부 OAuth 호출은 트랜잭션 밖에서 하고, DB 작업은 {@link OAuthMemberLoginService}의 트랜잭션 하나에서 한다.
	 * loginCode는 그 트랜잭션이 커밋된 뒤에 저장한다.
	 */
	public String issueAppLoginCode(String provider, String code, String state, String savedState, String idToken) {
		try {
			OAuthUserInfo userInfo = fetchOAuthUserInfo(provider, code, state, savedState, idToken);
			LoginCodePayload payload = oauthMemberLoginService.prepareLoginCode(userInfo);
			String loginCode = generateLoginCode();
			loginCodeStore.save(loginCode, payload);
			return loginCode;
		} catch (OAuthAuthenticationException e) {
			log.warn("OAuth authentication failed. provider={}, reason={}", provider, e.getMessage());
			throw new BusinessException(ErrorCode.AUTH104);
		} catch (BusinessException e) {
			throw e;
		} catch (Exception e) {
			throw new BusinessException(ErrorCode.AUTH105);
		}
	}

	public LoginResponse exchangeLoginCode(String code) {
		LoginCodePayload payload = loginCodeStore.consume(code)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH106));
		try {
			TokenResponse token = tokenService.issueToken(payload.getMemberId());
			return new LoginResponse(
					token.accessToken(),
					token.refreshToken(),
					payload.isNewMember(),
					payload.isAgreedRequiredTerms(),
					payload.getActiveTeamId()
			);
		} catch (Exception e) {
			loginCodeStore.save(code, payload);
			throw e;
		}
	}

	private OAuthUserInfo fetchOAuthUserInfo(String provider, String code, String state, String savedState, String idToken) {
		SocialProvider socialProvider = parseProvider(provider);
		validateCallbackRequest(code, state);
		validateOAuthState(socialProvider, state, savedState);

		return switch (socialProvider) {
			case GOOGLE -> googleOAuthService.getUserInfo(code);
			case KAKAO -> kakaoOAuthService.getUserInfo(code);
			case APPLE -> {
				if (idToken == null || idToken.isBlank()) {
					throw new BusinessException(ErrorCode.AUTH103);
				}
				yield appleOAuthService.getUserInfo(code, idToken);
			}
		};
	}

	private SocialProvider parseProvider(String provider) {
		if (provider == null || provider.isBlank()) {
			throw new BusinessException(ErrorCode.AUTH101);
		}
		try {
			return SocialProvider.valueOf(provider.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new BusinessException(ErrorCode.AUTH101);
		}
	}

	private void validateCallbackRequest(String code, String state) {
		if (code == null || code.isBlank()) {
			throw new BusinessException(ErrorCode.AUTH103);
		}
		if (state == null || state.isBlank()) {
			throw new BusinessException(ErrorCode.AUTH103);
		}
	}

	private void validateOAuthState(SocialProvider provider, String state, String savedState) {
		if (oauthStateStore.consume(state, provider.name())) {
			return;
		}
		throw new BusinessException(ErrorCode.AUTH103);
	}

	private String createState() {
		byte[] bytes = new byte[24];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private String generateLoginCode() {
		byte[] bytes = new byte[32];
		secureRandom.nextBytes(bytes);
		return HexFormat.of().formatHex(bytes);
	}
}
