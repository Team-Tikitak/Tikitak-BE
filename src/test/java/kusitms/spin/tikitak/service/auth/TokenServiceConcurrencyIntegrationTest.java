package kusitms.spin.tikitak.service.auth;

import kusitms.spin.tikitak.domain.auth.entity.RefreshToken;
import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.global.config.JacksonConfig;
import kusitms.spin.tikitak.global.config.PropertiesConfig;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.repository.auth.RefreshTokenRepository;
import kusitms.spin.tikitak.service.auth.dto.TokenResponse;
import kusitms.spin.tikitak.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 프록시가 붙은 실제 {@link TokenService}로 재사용 탐지 결과가 커밋된 DB 상태에 남는지 확인한다.
 * 테스트 트랜잭션을 끄므로 만든 데이터는 {@link #cleanUp()}에서 지운다.
 */
@Import({TokenService.class, JacksonConfig.class, PropertiesConfig.class})
@TestPropertySource(properties = {
		"auth.jwt.access-token-expires-in=3600",
		"auth.jwt.refresh-token-expires-in=604800"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TokenServiceConcurrencyIntegrationTest extends IntegrationTest {

	private static final long TIMEOUT_SECONDS = 30;

	@Autowired
	private TokenService tokenService;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private TransactionTemplate transactionTemplate;
	private Long memberId;

	@BeforeEach
	void setUp() {
		transactionTemplate = new TransactionTemplate(transactionManager);
		String suffix = UUID.randomUUID().toString();
		Member member = transactionTemplate.execute(status -> persist(member(suffix)));
		memberId = member.getId();
	}

	@AfterEach
	void cleanUp() {
		transactionTemplate.executeWithoutResult(status -> {
			entityManager().createNativeQuery("DELETE FROM refresh_token WHERE member_id = :memberId")
					.setParameter("memberId", memberId)
					.executeUpdate();
			entityManager().createNativeQuery("DELETE FROM member WHERE id = :memberId")
					.setParameter("memberId", memberId)
					.executeUpdate();
		});
	}

	@Test
	@DisplayName("같은 refresh 토큰으로 동시에 재발급하면 1건만 성공하고, 진 쪽의 revoke-all이 커밋되어 유효한 토큰이 남지 않는다")
	void concurrentReissueSucceedsOnceAndRevokesAll() throws Exception {
		assertThat(AopUtils.isAopProxy(tokenService)).isTrue();
		String refreshToken = tokenService.issueToken(memberId).refreshToken();

		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		Callable<Object> reissue = () -> {
			ready.countDown();
			if (!start.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
				throw new IllegalStateException("시작 신호를 받지 못함");
			}
			try {
				return tokenService.reissueToken(refreshToken);
			} catch (BusinessException e) {
				return e.getErrorCode();
			}
		};

		ExecutorService executor = Executors.newFixedThreadPool(2);
		List<Object> results = new ArrayList<>();
		try {
			List<Future<Object>> futures = List.of(executor.submit(reissue), executor.submit(reissue));
			assertThat(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			for (Future<Object> future : futures) {
				results.add(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
			}
		} finally {
			executor.shutdownNow();
		}

		assertThat(results).filteredOn(TokenResponse.class::isInstance).hasSize(1);
		assertThat(results).filteredOn(ErrorCode.class::isInstance).containsExactly(ErrorCode.AUTH007);
		// 원래 토큰 + 이긴 쪽이 새로 발급한 토큰. 진 쪽의 revoke-all로 둘 다 revoke되어 커밋돼 있어야 한다.
		assertThat(refreshTokenRepository.findAllByMemberIdAndRevokedFalse(memberId)).isEmpty();
		assertThat(memberTokens()).hasSize(2).allMatch(RefreshToken::isRevoked);
	}

	@Test
	@DisplayName("revoke된 refresh 토큰을 순차로 다시 보내면 AUTH007이고 revoke-all이 커밋된다")
	void sequentialReuseOfRevokedTokenCommitsRevokeAll() {
		String original = tokenService.issueToken(memberId).refreshToken();
		TokenResponse reissued = tokenService.reissueToken(original);
		assertThat(refreshTokenRepository.findAllByMemberIdAndRevokedFalse(memberId)).hasSize(1);

		assertThatThrownBy(() -> tokenService.reissueToken(original))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.AUTH007);

		assertThat(refreshTokenRepository.findAllByMemberIdAndRevokedFalse(memberId)).isEmpty();
		assertThat(memberTokens()).hasSize(2).allMatch(RefreshToken::isRevoked);
		// 이긴 쪽이 받았던 새 토큰도 더는 쓸 수 없다
		assertThatThrownBy(() -> tokenService.reissueToken(reissued.refreshToken()))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.AUTH007);
	}

	private List<RefreshToken> memberTokens() {
		return transactionTemplate.execute(status -> entityManager()
				.createQuery("select r from RefreshToken r where r.member.id = :memberId", RefreshToken.class)
				.setParameter("memberId", memberId)
				.getResultList());
	}
}
