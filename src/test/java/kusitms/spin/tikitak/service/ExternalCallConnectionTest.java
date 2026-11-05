package kusitms.spin.tikitak.service;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.SendResponse;
import com.zaxxer.hikari.HikariDataSource;
import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.member.enums.MemberStatus;
import kusitms.spin.tikitak.domain.member.enums.SocialProvider;
import kusitms.spin.tikitak.domain.notification.dto.NotificationPayload;
import kusitms.spin.tikitak.domain.notification.entity.MemberDeviceToken;
import kusitms.spin.tikitak.domain.notification.entity.Notification;
import kusitms.spin.tikitak.domain.notification.enums.DevicePlatform;
import kusitms.spin.tikitak.domain.notification.enums.NotificationType;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.repository.auth.RefreshTokenRepository;
import kusitms.spin.tikitak.repository.member.MemberRepository;
import kusitms.spin.tikitak.repository.notification.MemberDeviceTokenRepository;
import kusitms.spin.tikitak.repository.notification.NotificationRepository;
import kusitms.spin.tikitak.service.auth.AuthService;
import kusitms.spin.tikitak.service.auth.KakaoOAuthService;
import kusitms.spin.tikitak.service.auth.LoginCodeStore;
import kusitms.spin.tikitak.service.auth.OAuthStateStore;
import kusitms.spin.tikitak.service.auth.TokenService;
import kusitms.spin.tikitak.service.auth.dto.LoginCodePayload;
import kusitms.spin.tikitak.service.auth.dto.LoginResponse;
import kusitms.spin.tikitak.service.auth.dto.OAuthUserInfo;
import kusitms.spin.tikitak.service.notification.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 외부 네트워크 호출(OAuth, FCM 등)이 DB 트랜잭션·커넥션을 잡지 않은 상태에서 실행되는지 실제 Spring 컨텍스트(H2)로 확인한다.
 * 외부 클라이언트와 Redis 저장소는 mock으로 바꾸고, mock answer 안에서 트랜잭션·커넥션 상태를 캡처해 바깥에서 단언한다
 * (answer 안에서 단언하면 서비스의 catch(Exception)에 먹혀 다른 에러 코드로 바뀔 수 있음).
 *
 * <p>클래스에 @Transactional을 붙이지 않는다(붙이면 테스트 트랜잭션이 커넥션을 잡아 검증이 무의미해짐).
 * 대신 다른 컨텍스트와 DB를 공유하지 않도록 전용 H2 DB를 쓰고, 테스트마다 {@link #cleanUp()}에서 저장된 행을 지운다.
 */
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:external-call-connection;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
		"auth.jwt.secret=external-call-connection-test-secret"
})
class ExternalCallConnectionTest {

	private static final String CODE = "code";
	private static final String STATE = "state";
	private static final String PROVIDER_ID = "kakao-connection-test-id";
	private static final String FCM_PROVIDER_ID = "kakao-fcm-connection-test-id";
	private static final String VALID_FCM_TOKEN = "fcm-connection-test-valid-token";
	private static final String UNREGISTERED_FCM_TOKEN = "fcm-connection-test-unregistered-token";

	@Autowired
	private AuthService authService;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private NotificationRepository notificationRepository;

	@Autowired
	private MemberDeviceTokenRepository deviceTokenRepository;

	@Autowired
	private DataSource dataSource;

	@MockitoBean
	private KakaoOAuthService kakaoOAuthService;

	@MockitoBean
	private OAuthStateStore oauthStateStore;

	@MockitoBean
	private LoginCodeStore loginCodeStore;

	@MockitoSpyBean
	private TokenService tokenService;

	/** 테스트 기본 설정은 Firebase credentials가 비어 있어 빈이 없으므로, mock 빈을 새로 등록해 {@code Optional<FirebaseMessaging>}에 들어가게 한다. */
	@MockitoBean
	private FirebaseMessaging firebaseMessaging;

	@AfterEach
	void cleanUp() {
		notificationRepository.deleteAllInBatch();
		deviceTokenRepository.deleteAllInBatch();
		refreshTokenRepository.deleteAllInBatch();
		memberRepository.deleteAllInBatch();
	}

	@Test
	@DisplayName("웹 OAuth 로그인: 카카오 getUserInfo는 트랜잭션·DB 커넥션 없이 실행되고, 회원과 refresh token은 저장된다")
	void loginWithOAuthCallsKakaoOutsideTransaction() {
		AtomicReference<ConnectionSnapshot> duringUserInfo = new AtomicReference<>();
		when(oauthStateStore.consume(STATE, SocialProvider.KAKAO.name())).thenReturn(true);
		when(kakaoOAuthService.getUserInfo(CODE)).thenAnswer(invocation -> {
			duringUserInfo.set(snapshot());
			return kakaoUserInfo();
		});

		LoginResponse response = authService.loginWithOAuth("kakao", CODE, STATE, STATE);

		verify(kakaoOAuthService).getUserInfo(CODE);
		assertThat(duringUserInfo.get()).isEqualTo(new ConnectionSnapshot(false, 0));
		assertThat(response.isNewMember()).isTrue();
		assertThat(response.accessToken()).isNotBlank();
		assertThat(response.refreshToken()).isNotBlank();

		Optional<Member> saved = findSavedMember();
		assertThat(saved).isPresent();
		assertThat(refreshTokenRepository.findAllByMemberIdAndRevokedFalse(saved.get().getId())).hasSize(1);
	}

	@Test
	@DisplayName("앱 OAuth 로그인: 카카오 getUserInfo는 트랜잭션 밖에서 실행되고, loginCode는 회원 저장이 커밋된 뒤 트랜잭션 밖에서 저장된다")
	void issueAppLoginCodeCallsKakaoOutsideTransactionAndSavesCodeAfterCommit() {
		AtomicReference<ConnectionSnapshot> duringUserInfo = new AtomicReference<>();
		AtomicReference<ConnectionSnapshot> duringCodeSave = new AtomicReference<>();
		AtomicReference<Boolean> memberCommittedBeforeCodeSave = new AtomicReference<>();
		when(oauthStateStore.consume(STATE, SocialProvider.KAKAO.name())).thenReturn(true);
		when(kakaoOAuthService.getUserInfo(CODE)).thenAnswer(invocation -> {
			duringUserInfo.set(snapshot());
			return kakaoUserInfo();
		});
		doAnswer(invocation -> {
			duringCodeSave.set(snapshot());
			memberCommittedBeforeCodeSave.set(findSavedMember().isPresent());
			return null;
		}).when(loginCodeStore).save(anyString(), any(LoginCodePayload.class));

		String loginCode = authService.issueAppLoginCode("kakao", CODE, STATE, STATE);

		assertThat(loginCode).hasSize(64);
		verify(kakaoOAuthService).getUserInfo(CODE);
		verify(loginCodeStore).save(eq(loginCode), any(LoginCodePayload.class));
		assertThat(duringUserInfo.get()).isEqualTo(new ConnectionSnapshot(false, 0));
		assertThat(duringCodeSave.get()).isEqualTo(new ConnectionSnapshot(false, 0));
		assertThat(memberCommittedBeforeCodeSave.get()).isTrue();
	}

	@Test
	@DisplayName("회원 생성 후 토큰 발급이 실패하면 회원 생성도 함께 롤백된다")
	void memberCreationRollsBackWhenTokenIssuanceFails() {
		when(oauthStateStore.consume(STATE, SocialProvider.KAKAO.name())).thenReturn(true);
		when(kakaoOAuthService.getUserInfo(CODE)).thenReturn(kakaoUserInfo());
		doThrow(new RuntimeException("token issuance failed")).when(tokenService).issueToken(anyLong());

		assertThatThrownBy(() -> authService.loginWithOAuth("kakao", CODE, STATE, STATE))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH105));

		verify(tokenService).issueToken(anyLong());
		assertThat(findSavedMember()).isEmpty();
		assertThat(refreshTokenRepository.count()).isZero();
	}

	@Test
	@DisplayName("알림 전송: FCM sendEach는 트랜잭션·DB 커넥션 없이 실행되고, 알림은 저장되며 UNREGISTERED 토큰만 삭제된다")
	void sendCallsFcmOutsideTransaction() throws Exception {
		Member member = memberRepository.save(Member.createSocialMember(
				"fcm-connection-test@example.com",
				"FCM Connection Test",
				"FCM Connection Test",
				null,
				SocialProvider.KAKAO,
				FCM_PROVIDER_ID
		));
		MemberDeviceToken validToken = deviceTokenRepository.save(
				MemberDeviceToken.register(member, VALID_FCM_TOKEN, DevicePlatform.ANDROID));
		MemberDeviceToken unregisteredToken = deviceTokenRepository.save(
				MemberDeviceToken.register(member, UNREGISTERED_FCM_TOKEN, DevicePlatform.IOS));

		AtomicReference<ConnectionSnapshot> duringSendEach = new AtomicReference<>();
		AtomicReference<Long> notificationCountDuringSendEach = new AtomicReference<>();
		BatchResponse batchResponse = batchResponse(List.of(successResponse(), unregisteredResponse()));
		when(firebaseMessaging.sendEach(anyList())).thenAnswer(invocation -> {
			duringSendEach.set(snapshot());
			notificationCountDuringSendEach.set(notificationRepository.count());
			return batchResponse;
		});

		notificationService.send(member.getId(), notificationPayload());

		verify(firebaseMessaging).sendEach(anyList());
		assertThat(duringSendEach.get()).isEqualTo(new ConnectionSnapshot(false, 0));
		assertThat(notificationCountDuringSendEach.get()).isEqualTo(1L);

		List<Notification> notifications = notificationRepository.findAll();
		assertThat(notifications).hasSize(1);
		assertThat(notifications.get(0).getMember().getId()).isEqualTo(member.getId());
		assertThat(notifications.get(0).getType()).isEqualTo(NotificationType.FEED_COMMENT);

		assertThat(deviceTokenRepository.findAllByMemberId(member.getId()))
				.extracting(MemberDeviceToken::getId)
				.containsExactly(validToken.getId());
		assertThat(deviceTokenRepository.findById(unregisteredToken.getId())).isEmpty();
	}

	private static NotificationPayload notificationPayload() {
		return NotificationPayload.builder()
				.type(NotificationType.FEED_COMMENT)
				.title("title")
				.body("body")
				.build();
	}

	private static BatchResponse batchResponse(List<SendResponse> responses) {
		BatchResponse batchResponse = mock(BatchResponse.class);
		when(batchResponse.getResponses()).thenReturn(responses);
		return batchResponse;
	}

	private static SendResponse successResponse() {
		SendResponse response = mock(SendResponse.class);
		when(response.isSuccessful()).thenReturn(true);
		return response;
	}

	private static SendResponse unregisteredResponse() {
		FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
		when(exception.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
		SendResponse response = mock(SendResponse.class);
		when(response.isSuccessful()).thenReturn(false);
		when(response.getException()).thenReturn(exception);
		return response;
	}

	private Optional<Member> findSavedMember() {
		return memberRepository.findBySocialProviderAndProviderIdAndStatus(
				SocialProvider.KAKAO,
				PROVIDER_ID,
				MemberStatus.ACTIVE
		);
	}

	private ConnectionSnapshot snapshot() throws SQLException {
		return new ConnectionSnapshot(
				TransactionSynchronizationManager.isActualTransactionActive(),
				dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean().getActiveConnections()
		);
	}

	private static OAuthUserInfo kakaoUserInfo() {
		return new OAuthUserInfo(
				SocialProvider.KAKAO,
				PROVIDER_ID,
				"connection-test@example.com",
				"Connection Test",
				"https://example.com/profile.png"
		);
	}

	private record ConnectionSnapshot(boolean transactionActive, int activeConnections) {
	}
}
