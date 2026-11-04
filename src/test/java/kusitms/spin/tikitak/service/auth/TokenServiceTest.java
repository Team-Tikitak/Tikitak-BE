package kusitms.spin.tikitak.service.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import kusitms.spin.tikitak.global.config.AuthProperties;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.repository.auth.RefreshTokenRepository;
import kusitms.spin.tikitak.repository.member.MemberRepository;
import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

class TokenServiceTest extends UnitTest {

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

	private TokenService tokenService(String secret) {
		AuthProperties authProperties = new AuthProperties(
				null,
				new AuthProperties.Jwt(secret, 3600L, 604800L, false, null)
		);
		return new TokenService(authProperties, new ObjectMapper(), memberRepository, refreshTokenRepository);
	}
}
