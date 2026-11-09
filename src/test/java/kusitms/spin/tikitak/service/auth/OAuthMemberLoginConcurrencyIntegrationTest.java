package kusitms.spin.tikitak.service.auth;

import kusitms.spin.tikitak.domain.member.enums.SocialProvider;
import kusitms.spin.tikitak.global.config.JacksonConfig;
import kusitms.spin.tikitak.global.config.PropertiesConfig;
import kusitms.spin.tikitak.repository.member.MemberRepository;
import kusitms.spin.tikitak.service.auth.dto.LoginResponse;
import kusitms.spin.tikitak.service.auth.dto.OAuthUserInfo;
import kusitms.spin.tikitak.service.me.ActiveTeamService;
import kusitms.spin.tikitak.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 같은 소셜 계정의 첫 로그인이 겹칠 때, 프록시가 붙은 실제 {@link AuthService}·{@link OAuthMemberLoginService}가
 * 진 쪽 요청을 기존 회원 로그인으로 처리하는지 Postgres(V25 부분 unique 인덱스)로 확인한다.
 * 외부 의존(OAuth 서비스, state store, loginCode store)은 mock이다.
 * 테스트 트랜잭션을 끄므로 만든 데이터는 {@link #cleanUp()}에서 지운다.
 */
@Import({
		AuthService.class,
		OAuthMemberLoginService.class,
		TokenService.class,
		ActiveTeamService.class,
		JacksonConfig.class,
		PropertiesConfig.class
})
@TestPropertySource(properties = {
		"auth.jwt.access-token-expires-in=3600",
		"auth.jwt.refresh-token-expires-in=604800"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OAuthMemberLoginConcurrencyIntegrationTest extends IntegrationTest {

	private static final String MEMBER_SOCIAL_ACTIVE_UNIQUE = "uk_member_social_provider_active";
	private static final long TIMEOUT_SECONDS = 30;
	private static final String CODE = "code";
	private static final String STATE = "state";

	@Autowired
	private AuthService authService;

	@Autowired
	private OAuthMemberLoginService oauthMemberLoginService;

	@Autowired
	private TokenService tokenService;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@MockitoBean
	private GoogleOAuthService googleOAuthService;

	@MockitoBean
	private KakaoOAuthService kakaoOAuthService;

	@MockitoBean
	private AppleOAuthService appleOAuthService;

	@MockitoBean
	private OAuthStateStore oauthStateStore;

	@MockitoBean
	private LoginCodeStore loginCodeStore;

	private TransactionTemplate transactionTemplate;
	private String suffix;
	private String providerId;
	private OAuthUserInfo userInfo;

	@BeforeEach
	void setUp() {
		transactionTemplate = new TransactionTemplate(transactionManager);
		suffix = UUID.randomUUID().toString();
		// IntegrationTest#member(suffix)와 같은 (KAKAO, provider-<suffix>) 계정
		providerId = "provider-" + suffix;
		userInfo = new OAuthUserInfo(SocialProvider.KAKAO, providerId, "race@example.com", "Race User", null);
		when(oauthStateStore.consume(STATE, SocialProvider.KAKAO.name())).thenReturn(true);
		when(kakaoOAuthService.getUserInfo(CODE)).thenReturn(userInfo);
	}

	@AfterEach
	void cleanUp() {
		transactionTemplate.executeWithoutResult(status -> {
			entityManager().createNativeQuery("""
					DELETE FROM refresh_token
					WHERE member_id IN (SELECT id FROM member WHERE social_provider = 'KAKAO' AND provider_id = :providerId)
					""")
					.setParameter("providerId", providerId)
					.executeUpdate();
			entityManager().createNativeQuery(
					"DELETE FROM member WHERE social_provider = 'KAKAO' AND provider_id = :providerId")
					.setParameter("providerId", providerId)
					.executeUpdate();
		});
	}

	@Test
	@DisplayName("다른 트랜잭션이 같은 계정의 회원을 INSERT하고 커밋 전이면 로그인은 막혔다가, 커밋 뒤 재시도로 그 회원에 기존 회원으로 로그인한다")
	void loginWithOAuthRetriesAndLogsInAsExistingMemberWhenLosingFirstLoginRace() throws Exception {
		assertThat(AopUtils.isAopProxy(oauthMemberLoginService)).isTrue();

		RaceResult<LoginResponse> race = raceAgainstUncommittedInsert(
				() -> authService.loginWithOAuth("kakao", CODE, STATE, STATE));

		LoginResponse response = race.loser().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		assertThat(response.isNewMember()).isFalse();
		assertThat(tokenService.getMemberIdFromAccessToken(response.accessToken())).isEqualTo(race.winnerMemberId());
		assertThat(activeMemberCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("재시도 없이 OAuthMemberLoginService를 직접 부르면 같은 경쟁에서 소셜 계정 unique 제약의 DataIntegrityViolationException이 난다")
	void directMemberLoginThrowsMemberSocialDuplicateWhenLosingFirstLoginRace() throws Exception {
		RaceResult<LoginResponse> race = raceAgainstUncommittedInsert(
				() -> oauthMemberLoginService.loginWithToken(userInfo));

		assertThatThrownBy(() -> race.loser().get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
				.isInstanceOf(ExecutionException.class)
				.cause()
				.isInstanceOf(DataIntegrityViolationException.class)
				// 커밋 시점이 아니라 회원 INSERT(findOrCreateMember 안) 시점에 난 예외다
				.hasStackTraceContaining("findOrCreateMember")
				.satisfies(cause -> assertThat(messageChainContains(cause, MEMBER_SOCIAL_ACTIVE_UNIQUE)).isTrue());
		assertThat(activeMemberCount()).isEqualTo(1);
	}

	/**
	 * 스레드 A가 같은 계정의 ACTIVE 회원을 INSERT하고 커밋하지 않은 채 기다리는 동안 스레드 B가 {@code loserCall}을 실행한다.
	 * B의 INSERT가 A의 unique 인덱스 항목을 기다리며 막힌 것(Postgres 락 대기)과 B가 아직 끝나지 않은 것을 확인한 뒤 A를 커밋한다.
	 */
	private <T> RaceResult<T> raceAgainstUncommittedInsert(Callable<T> loserCall) throws Exception {
		CountDownLatch inserted = new CountDownLatch(1);
		CountDownLatch commit = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<Long> winner = executor.submit(() -> transactionTemplate.execute(status -> {
				Long memberId = memberRepository.saveAndFlush(member(suffix)).getId();
				inserted.countDown();
				awaitOrFail(commit, "커밋 신호를 받지 못함");
				return memberId;
			}));
			assertThat(inserted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

			Future<T> loser = executor.submit(loserCall);
			awaitMemberInsertBlockedOnLock();
			assertThat(loser.isDone()).as("A가 커밋하기 전에는 B가 끝나지 않아야 한다").isFalse();

			commit.countDown();
			Long winnerMemberId = winner.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
			// B의 결과를 기다린 뒤에 executor를 닫도록 여기서 끝까지 기다린다
			try {
				loser.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
			} catch (ExecutionException ignored) {
				// 결과 단언은 호출한 테스트가 한다
			}
			return new RaceResult<>(winnerMemberId, loser);
		} finally {
			commit.countDown();
			executor.shutdownNow();
		}
	}

	private void awaitMemberInsertBlockedOnLock() throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
		while (System.nanoTime() < deadline) {
			if (blockedMemberInsertCount() > 0) {
				return;
			}
			Thread.sleep(20);
		}
		throw new AssertionError("member INSERT가 락 대기 상태가 되지 않음");
	}

	private long blockedMemberInsertCount() {
		return transactionTemplate.execute(status -> ((Number) entityManager().createNativeQuery("""
				SELECT count(*) FROM pg_stat_activity
				WHERE wait_event_type = 'Lock' AND query ILIKE 'insert into member%'
				""").getSingleResult()).longValue());
	}

	private long activeMemberCount() {
		return transactionTemplate.execute(status -> ((Number) entityManager().createNativeQuery("""
				SELECT count(*) FROM member
				WHERE social_provider = 'KAKAO' AND provider_id = :providerId AND status = 'ACTIVE'
				""")
				.setParameter("providerId", providerId)
				.getSingleResult()).longValue());
	}

	private static void awaitOrFail(CountDownLatch latch, String message) {
		try {
			if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
				throw new IllegalStateException(message);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(message, e);
		}
	}

	private static boolean messageChainContains(Throwable exception, String text) {
		Throwable current = exception;
		while (current != null) {
			if (current.getMessage() != null && current.getMessage().contains(text)) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

	private record RaceResult<T>(Long winnerMemberId, Future<T> loser) {
	}
}
