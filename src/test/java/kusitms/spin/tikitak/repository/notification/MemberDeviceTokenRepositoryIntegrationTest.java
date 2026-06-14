package kusitms.spin.tikitak.repository.notification;

import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.notification.entity.MemberDeviceToken;
import kusitms.spin.tikitak.domain.notification.enums.DevicePlatform;
import kusitms.spin.tikitak.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MemberDeviceTokenRepositoryIntegrationTest extends IntegrationTest {

	@Autowired
	private MemberDeviceTokenRepository deviceTokenRepository;

	@Test
	@DisplayName("fcmToken으로 디바이스 토큰을 조회한다")
	void findByFcmTokenReturnsMatchingToken() {
		Member member = persist(member("owner"));
		persist(MemberDeviceToken.register(member, "token-1", DevicePlatform.ANDROID));
		flushAndClear();

		assertThat(deviceTokenRepository.findByFcmToken("token-1")).get()
				.extracting(MemberDeviceToken::getPlatform)
				.isEqualTo(DevicePlatform.ANDROID);
		assertThat(deviceTokenRepository.findByFcmToken("token-unknown")).isEmpty();
	}

	@Test
	@DisplayName("회원에 등록된 모든 디바이스 토큰을 조회한다")
	void findAllByMemberIdReturnsAllTokensForMember() {
		Member member = persist(member("multi"));
		Member otherMember = persist(member("other"));
		persist(MemberDeviceToken.register(member, "token-1", DevicePlatform.ANDROID));
		persist(MemberDeviceToken.register(member, "token-2", DevicePlatform.IOS));
		persist(MemberDeviceToken.register(otherMember, "token-3", DevicePlatform.ANDROID));
		flushAndClear();

		List<MemberDeviceToken> tokens = deviceTokenRepository.findAllByMemberId(member.getId());

		assertThat(tokens)
				.extracting(MemberDeviceToken::getFcmToken)
				.containsExactlyInAnyOrder("token-1", "token-2");
	}

	@Test
	@DisplayName("회원의 모든 디바이스 토큰을 삭제한다")
	void deleteAllByMemberIdRemovesAllTokensForMember() {
		Member member = persist(member("withdraw"));
		Member otherMember = persist(member("stay"));
		persist(MemberDeviceToken.register(member, "token-1", DevicePlatform.ANDROID));
		persist(MemberDeviceToken.register(member, "token-2", DevicePlatform.IOS));
		persist(MemberDeviceToken.register(otherMember, "token-3", DevicePlatform.ANDROID));
		flushAndClear();

		deviceTokenRepository.deleteAllByMemberId(member.getId());
		flushAndClear();

		assertThat(deviceTokenRepository.findAllByMemberId(member.getId())).isEmpty();
		assertThat(deviceTokenRepository.findAllByMemberId(otherMember.getId())).hasSize(1);
	}

	@Test
	@DisplayName("토큰과 회원이 일치할 때만 디바이스 토큰을 삭제한다")
	void deleteByFcmTokenAndMemberIdRemovesOnlyMatchingToken() {
		Member member = persist(member("owner"));
		Member otherMember = persist(member("other"));
		persist(MemberDeviceToken.register(member, "token-1", DevicePlatform.ANDROID));
		persist(MemberDeviceToken.register(otherMember, "token-2", DevicePlatform.ANDROID));
		flushAndClear();

		deviceTokenRepository.deleteByFcmTokenAndMemberId("token-2", member.getId());
		deviceTokenRepository.deleteByFcmTokenAndMemberId("token-1", member.getId());
		flushAndClear();

		assertThat(deviceTokenRepository.findByFcmToken("token-1")).isEmpty();
		assertThat(deviceTokenRepository.findByFcmToken("token-2")).isPresent();
	}
}
