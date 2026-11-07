package kusitms.spin.tikitak.service.auth;

import com.fasterxml.jackson.core.type.TypeReference;
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

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
	@DisplayName("정상 refresh 토큰이면 조건부 revoke 1회 후 재발급에 성공하고 새 refresh 토큰을 저장한다")
	void reissueTokenSucceedsWithValidRefreshToken() {
		TokenService tokenService = tokenService(SECRET, 3600L, 604800L);
		stubActiveMember();
		TokenResponse issued = tokenService.issueToken(MEMBER_ID);
		RefreshToken saved = capturedSavedToken();
		clearInvocations(refreshTokenRepository);
		when(refreshTokenRepository.findByTokenHash(saved.getTokenHash())).thenReturn(Optional.of(saved));
		when(refreshTokenRepository.revokeIfActive(eq(saved.getTokenHash()), any(LocalDateTime.class))).thenReturn(1);

		TokenResponse reissued = tokenService.reissueToken(issued.refreshToken());

		assertThat(reissued.accessToken()).isNotBlank();
		assertThat(reissued.refreshToken()).isNotBlank().isNotEqualTo(issued.refreshToken());
		assertThat(tokenService.getMemberIdFromAccessToken(reissued.accessToken())).isEqualTo(MEMBER_ID);
		verify(refreshTokenRepository, times(1)).revokeIfActive(eq(saved.getTokenHash()), any(LocalDateTime.class));
		RefreshToken newlySaved = capturedSavedToken();
		assertThat(newlySaved.getTokenHash()).isNotEqualTo(saved.getTokenHash());
		assertThat(newlySaved.isRevoked()).isFalse();
		verify(refreshTokenRepository, never()).findAllByMemberIdAndRevokedFalse(anyLong());
	}

	@Test
	@DisplayName("revoke된 refresh 토큰이 다시 오면 AUTH007이고 그 회원의 유효한 refresh 토큰을 모두 revoke한다")
	void reissueTokenRevokesAllWhenRevokedTokenReused() {
		TokenService tokenService = tokenService(SECRET, 3600L, 604800L);
		stubActiveMember();
		TokenResponse issued = tokenService.issueToken(MEMBER_ID);
		RefreshToken saved = capturedSavedToken();
		saved.revoke();
		clearInvocations(refreshTokenRepository);
		when(refreshTokenRepository.findByTokenHash(saved.getTokenHash())).thenReturn(Optional.of(saved));
		List<RefreshToken> activeTokens = activeTokens();
		when(refreshTokenRepository.findAllByMemberIdAndRevokedFalse(MEMBER_ID)).thenReturn(activeTokens);

		assertThatThrownBy(() -> tokenService.reissueToken(issued.refreshToken()))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.AUTH007);
		assertThat(activeTokens).allMatch(RefreshToken::isRevoked);
		verify(refreshTokenRepository).findAllByMemberIdAndRevokedFalse(MEMBER_ID);
		verify(refreshTokenRepository, never()).revokeIfActive(anyString(), any(LocalDateTime.class));
		verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
	}

	@Test
	@DisplayName("조건부 revoke가 0행이면(동시 재발급에서 짐) AUTH007이고 그 회원의 refresh 토큰을 모두 revoke한다")
	void reissueTokenRevokesAllWhenConditionalRevokeAffectsNoRow() {
		TokenService tokenService = tokenService(SECRET, 3600L, 604800L);
		stubActiveMember();
		TokenResponse issued = tokenService.issueToken(MEMBER_ID);
		RefreshToken saved = capturedSavedToken();
		clearInvocations(refreshTokenRepository);
		when(refreshTokenRepository.findByTokenHash(saved.getTokenHash())).thenReturn(Optional.of(saved));
		when(refreshTokenRepository.revokeIfActive(eq(saved.getTokenHash()), any(LocalDateTime.class))).thenReturn(0);
		List<RefreshToken> activeTokens = activeTokens();
		when(refreshTokenRepository.findAllByMemberIdAndRevokedFalse(MEMBER_ID)).thenReturn(activeTokens);

		assertThatThrownBy(() -> tokenService.reissueToken(issued.refreshToken()))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.AUTH007);
		assertThat(activeTokens).allMatch(RefreshToken::isRevoked);
		verify(refreshTokenRepository).findAllByMemberIdAndRevokedFalse(MEMBER_ID);
		verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
	}

	@Test
	@DisplayName("DB에서 만료됐지만 revoke되지 않은 refresh 토큰이면 AUTH007이고 revoke-all은 하지 않는다")
	void reissueTokenThrowsAuth007WithoutRevokeAllWhenStoredTokenExpired() {
		TokenService tokenService = tokenService(SECRET, 3600L, 604800L);
		stubActiveMember();
		TokenResponse issued = tokenService.issueToken(MEMBER_ID);
		RefreshToken saved = capturedSavedToken();
		clearInvocations(refreshTokenRepository);
		RefreshToken expiredInDb = RefreshToken.issue(
				mock(Member.class),
				saved.getTokenHash(),
				LocalDateTime.now().minusSeconds(1)
		);
		when(refreshTokenRepository.findByTokenHash(saved.getTokenHash())).thenReturn(Optional.of(expiredInDb));

		assertThatThrownBy(() -> tokenService.reissueToken(issued.refreshToken()))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.AUTH007);
		verify(refreshTokenRepository).findByTokenHash(saved.getTokenHash());
		verify(refreshTokenRepository, never()).findAllByMemberIdAndRevokedFalse(anyLong());
		verify(refreshTokenRepository, never()).revokeIfActive(anyString(), any(LocalDateTime.class));
		verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
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

	@Test
	@DisplayName("같은 회원으로 연속 두 번 발급하면 refresh 토큰이 서로 다르고 payload에 jti가 있다")
	void issueTokenTwiceProducesDistinctRefreshTokensWithJti() throws Exception {
		TokenService tokenService = tokenService(SECRET, 3600L, 604800L);
		stubActiveMember();

		TokenResponse first = tokenService.issueToken(MEMBER_ID);
		TokenResponse second = tokenService.issueToken(MEMBER_ID);

		assertThat(first.refreshToken()).isNotEqualTo(second.refreshToken());
		Object firstJti = payload(first.refreshToken()).get("jti");
		Object secondJti = payload(second.refreshToken()).get("jti");
		assertThat(firstJti).isInstanceOf(String.class);
		assertThat((String) firstJti).isNotBlank();
		assertThat(secondJti).isInstanceOf(String.class).isNotEqualTo(firstJti);
		assertThat(payload(first.accessToken())).containsKey("jti");
	}

	private RefreshToken capturedSavedToken() {
		ArgumentCaptor<RefreshToken> savedCaptor = ArgumentCaptor.forClass(RefreshToken.class);
		verify(refreshTokenRepository).save(savedCaptor.capture());
		return savedCaptor.getValue();
	}

	private List<RefreshToken> activeTokens() {
		LocalDateTime expiresAt = LocalDateTime.now().plusDays(1);
		return List.of(
				RefreshToken.issue(mock(Member.class), "a".repeat(64), expiresAt),
				RefreshToken.issue(mock(Member.class), "b".repeat(64), expiresAt)
		);
	}

	private Map<String, Object> payload(String token) throws Exception {
		String[] parts = token.split("\\.");
		return new ObjectMapper().readValue(Base64.getUrlDecoder().decode(parts[1]), new TypeReference<>() {
		});
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
