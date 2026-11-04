package kusitms.spin.tikitak.service.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import kusitms.spin.tikitak.domain.auth.entity.RefreshToken;
import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.member.enums.MemberStatus;
import kusitms.spin.tikitak.global.config.AuthProperties;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.repository.auth.RefreshTokenRepository;
import kusitms.spin.tikitak.repository.member.MemberRepository;
import kusitms.spin.tikitak.service.auth.dto.TokenResponse;
import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TokenServiceTest extends UnitTest {

	private static final String SECRET = "test-jwt-secret";
	private static final Long MEMBER_ID = 1L;

	@Mock
	private MemberRepository memberRepository;

	@Mock
	private RefreshTokenRepository refreshTokenRepository;

	@Test
	@DisplayName("JWT secret이 비어 있으면 토큰 발급 시 AUTH010 BusinessException이 발생한다")
	void issueTokenThrowsAuth010WhenSecretIsEmpty() {
		TokenService tokenService = tokenService("");

		assertThatThrownBy(() -> tokenService.issueToken(1L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.AUTH010);
		verifyNoInteractions(memberRepository, refreshTokenRepository);
	}

	@Test
	@DisplayName("JWT secret이 없으면(null) 토큰 발급 시 AUTH010 BusinessException이 발생한다")
	void issueTokenThrowsAuth010WhenSecretIsNull() {
		TokenService tokenService = tokenService(null);

		assertThatThrownBy(() -> tokenService.issueToken(1L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.AUTH010);
	}

	@Test
	@DisplayName("JWT secret이 비어 있으면 refresh 토큰 검증 중 서명 실패가 AUTH007이 아니라 AUTH010으로 나간다")
	void reissueTokenThrowsAuth010WhenSecretIsEmpty() {
		TokenService tokenService = tokenService("");

		assertThatThrownBy(() -> tokenService.reissueToken("header.payload.signature"))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.AUTH010);
		verifyNoInteractions(memberRepository, refreshTokenRepository);
	}

	@Test
	@DisplayName("정상 refresh 토큰이면 재발급에 성공하고 기존 refresh 토큰은 폐기된다")
	void reissueTokenSucceedsWithValidRefreshToken() {
		TokenService tokenService = tokenService(SECRET, 3600L, 604800L);
		stubActiveMember();
		TokenResponse issued = tokenService.issueToken(MEMBER_ID);
		ArgumentCaptor<RefreshToken> savedCaptor = ArgumentCaptor.forClass(RefreshToken.class);
		verify(refreshTokenRepository).save(savedCaptor.capture());
		RefreshToken saved = savedCaptor.getValue();
		when(refreshTokenRepository.findByTokenHash(saved.getTokenHash())).thenReturn(Optional.of(saved));

		TokenResponse reissued = tokenService.reissueToken(issued.refreshToken());

		assertThat(reissued.accessToken()).isNotBlank();
		assertThat(reissued.refreshToken()).isNotBlank();
		assertThat(tokenService.getMemberIdFromAccessToken(reissued.accessToken())).isEqualTo(MEMBER_ID);
		assertThat(saved.isRevoked()).isTrue();
	}

	@Test
	@DisplayName("만료된 refresh 토큰이면 재발급 시 AUTH007이 발생한다")
	void reissueTokenThrowsAuth007WhenRefreshTokenExpired() {
		TokenService tokenService = tokenService(SECRET, 3600L, -60L);
		stubActiveMember();
		String expiredRefreshToken = tokenService.issueToken(MEMBER_ID).refreshToken();

		assertThatThrownBy(() -> tokenService.reissueToken(expiredRefreshToken))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.AUTH007);
		// 만료 분기에서 끝나야 하므로 저장소 조회까지 가지 않는다
		verify(refreshTokenRepository, never()).findByTokenHash(anyString());
	}

	@Test
	@DisplayName("서명이 위조된 refresh 토큰이면 재발급 시 AUTH007이 발생한다")
	void reissueTokenThrowsAuth007WhenRefreshTokenSignatureForged() {
		TokenService tokenService = tokenService(SECRET, 3600L, 604800L);
		TokenService forger = tokenService("other-secret", 3600L, 604800L);
		stubActiveMember();
		String forgedRefreshToken = forger.issueToken(MEMBER_ID).refreshToken();

		assertThatThrownBy(() -> tokenService.reissueToken(forgedRefreshToken))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.AUTH007);
		// 서명 불일치 분기에서 끝나야 하므로 저장소 조회까지 가지 않는다
		verify(refreshTokenRepository, never()).findByTokenHash(anyString());
	}

	@Test
	@DisplayName("정상 access 토큰이면 memberId를 반환한다")
	void getMemberIdFromAccessTokenReturnsMemberIdWithValidToken() {
		TokenService tokenService = tokenService(SECRET, 3600L, 604800L);
		stubActiveMember();
		String accessToken = tokenService.issueToken(MEMBER_ID).accessToken();

		assertThat(tokenService.getMemberIdFromAccessToken(accessToken)).isEqualTo(MEMBER_ID);
	}

	@Test
	@DisplayName("서명이 위조된 access 토큰이면 AUTH009가 발생한다")
	void getMemberIdFromAccessTokenThrowsAuth009WhenSignatureForged() {
		TokenService tokenService = tokenService(SECRET, 3600L, 604800L);
		TokenService forger = tokenService("other-secret", 3600L, 604800L);
		stubActiveMember();
		String forgedAccessToken = forger.issueToken(MEMBER_ID).accessToken();

		assertThatThrownBy(() -> tokenService.getMemberIdFromAccessToken(forgedAccessToken))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.AUTH009);
	}

	private void stubActiveMember() {
		when(memberRepository.findByIdAndStatus(MEMBER_ID, MemberStatus.ACTIVE))
				.thenReturn(Optional.of(mock(Member.class)));
	}

	private TokenService tokenService(String secret, long accessTokenExpiresIn, long refreshTokenExpiresIn) {
		AuthProperties authProperties = new AuthProperties(
				null,
				new AuthProperties.Jwt(secret, accessTokenExpiresIn, refreshTokenExpiresIn, false, null)
		);
		return new TokenService(authProperties, new ObjectMapper(), memberRepository, refreshTokenRepository);
	}

	private TokenService tokenService(String secret) {
		AuthProperties authProperties = new AuthProperties(
				null,
				new AuthProperties.Jwt(secret, 3600L, 604800L, false, null)
		);
		return new TokenService(authProperties, new ObjectMapper(), memberRepository, refreshTokenRepository);
	}
}
