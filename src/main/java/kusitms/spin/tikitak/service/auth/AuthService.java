package kusitms.spin.tikitak.service.auth;

import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.member.enums.SocialProvider;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.repository.member.MemberRepository;
import kusitms.spin.tikitak.service.auth.dto.GoogleAuthorizeUrlResponse;
import kusitms.spin.tikitak.service.auth.dto.LoginResponse;
import kusitms.spin.tikitak.service.auth.dto.OAuthUserInfo;
import kusitms.spin.tikitak.service.auth.dto.TokenResponse;
import kusitms.spin.tikitak.service.auth.exception.OAuthAuthenticationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

	private final GoogleOAuthService googleOAuthService;
	private final TokenService tokenService;
	private final MemberRepository memberRepository;
	private final SecureRandom secureRandom = new SecureRandom();

	public GoogleAuthorizeUrlResponse getGoogleAuthorizeUrl(String provider) {
		validateGoogleProvider(provider);
		try {
			return googleOAuthService.getAuthorizeUrl(createState());
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

	@Transactional
	public LoginResponse loginWithGoogle(String provider, String code, String state, String savedState) {
		validateGoogleProvider(provider);
		validateCallbackRequest(code, state, savedState);

		try {
			OAuthUserInfo userInfo = googleOAuthService.getUserInfo(code);
			boolean[] created = {false};
			Member member = memberRepository
					.findBySocialProviderAndProviderId(userInfo.provider(), userInfo.providerId())
					.map(existingMember -> {
						existingMember.updateSocialProfile(userInfo.email(), userInfo.name(), userInfo.profileImageUrl());
						return existingMember;
					})
					.orElseGet(() -> {
						created[0] = true;
						return memberRepository.save(Member.createSocialMember(
								userInfo.email(),
								userInfo.name(),
								defaultNickname(userInfo),
								userInfo.profileImageUrl(),
								userInfo.provider(),
								userInfo.providerId()
						));
					});

			TokenResponse token = tokenService.issueToken(member.getId());
			return new LoginResponse(
					token.accessToken(),
					token.refreshToken(),
					created[0],
					member.isTermsAgreed() && member.isPrivacyAgreed(),
					null
			);
		} catch (OAuthAuthenticationException e) {
			log.warn("OAuth authentication failed. provider={}, reason={}", provider, e.getMessage());
			throw new BusinessException(ErrorCode.AUTH104);
		} catch (BusinessException e) {
			throw e;
		} catch (Exception e) {
			throw new BusinessException(ErrorCode.AUTH105);
		}
	}

	private void validateGoogleProvider(String provider) {
		if (provider == null || !provider.toLowerCase(Locale.ROOT).equals(SocialProvider.GOOGLE.name().toLowerCase(Locale.ROOT))) {
			throw new BusinessException(ErrorCode.AUTH101);
		}
	}

	private void validateCallbackRequest(String code, String state, String savedState) {
		if (code == null || code.isBlank()) {
			throw new BusinessException(ErrorCode.AUTH103);
		}
		if (state == null || state.isBlank() || savedState == null || savedState.isBlank() || !state.equals(savedState)) {
			throw new BusinessException(ErrorCode.AUTH103);
		}
	}

	private String defaultNickname(OAuthUserInfo userInfo) {
		if (userInfo.name() != null && !userInfo.name().isBlank()) {
			return truncate(userInfo.name());
		}
		String email = userInfo.email();
		if (email != null && email.contains("@")) {
			return truncate(email.substring(0, email.indexOf('@')));
		}
		return "Google User";
	}

	private String truncate(String value) {
		return value.length() > 30 ? value.substring(0, 30) : value;
	}

	private String createState() {
		byte[] bytes = new byte[24];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
