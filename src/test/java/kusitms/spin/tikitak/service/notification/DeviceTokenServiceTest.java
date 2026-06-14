package kusitms.spin.tikitak.service.notification;

import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.notification.entity.MemberDeviceToken;
import kusitms.spin.tikitak.domain.notification.enums.DevicePlatform;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.repository.member.MemberRepository;
import kusitms.spin.tikitak.repository.notification.MemberDeviceTokenRepository;
import kusitms.spin.tikitak.service.notification.dto.DeviceTokenRequestDTO;
import kusitms.spin.tikitak.service.notification.dto.DeviceTokenResponseDTO;
import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.Optional;

import static kusitms.spin.tikitak.support.fixture.MemberFixture.activeMember;
import static kusitms.spin.tikitak.support.fixture.MemberFixture.inactiveMember;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceTokenServiceTest extends UnitTest {

	@Mock
	private MemberRepository memberRepository;

	@Mock
	private MemberDeviceTokenRepository deviceTokenRepository;

	@InjectMocks
	private DeviceTokenService deviceTokenService;

	@Test
	@DisplayName("등록되지 않은 FCM 토큰이면 새 디바이스 토큰을 생성하여 저장한다")
	void registerTokenCreatesNewDeviceToken() {
		Member member = activeMember(1L);
		when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
		when(deviceTokenRepository.findByFcmToken("token-1")).thenReturn(Optional.empty());

		LocalDateTime now = LocalDateTime.now();
		MemberDeviceToken saved = MemberDeviceToken.builder()
				.id(10L)
				.member(member)
				.fcmToken("token-1")
				.platform(DevicePlatform.ANDROID)
				.createdAt(now)
				.updatedAt(now)
				.build();
		when(deviceTokenRepository.save(any(MemberDeviceToken.class))).thenReturn(saved);

		DeviceTokenResponseDTO.RegisterResponseDTO response = deviceTokenService.registerToken(
				1L,
				new DeviceTokenRequestDTO.RegisterRequestDTO("token-1", DevicePlatform.ANDROID)
		);

		assertThat(response.getDeviceTokenId()).isEqualTo(10L);
		assertThat(response.getPlatform()).isEqualTo(DevicePlatform.ANDROID);

		ArgumentCaptor<MemberDeviceToken> captor = ArgumentCaptor.forClass(MemberDeviceToken.class);
		verify(deviceTokenRepository).save(captor.capture());
		assertThat(captor.getValue().getFcmToken()).isEqualTo("token-1");
		assertThat(captor.getValue().getPlatform()).isEqualTo(DevicePlatform.ANDROID);
		assertThat(captor.getValue().getMember()).isEqualTo(member);
	}

	@Test
	@DisplayName("이미 등록된 FCM 토큰이면 기존 디바이스 토큰의 소유자와 플랫폼을 갱신한다")
	void registerTokenReassignsExistingDeviceToken() {
		Member previousOwner = activeMember(2L);
		Member newOwner = activeMember(1L);
		when(memberRepository.findById(1L)).thenReturn(Optional.of(newOwner));

		LocalDateTime now = LocalDateTime.now();
		MemberDeviceToken existing = MemberDeviceToken.builder()
				.id(5L)
				.member(previousOwner)
				.fcmToken("token-1")
				.platform(DevicePlatform.IOS)
				.createdAt(now)
				.updatedAt(now)
				.build();
		when(deviceTokenRepository.findByFcmToken("token-1")).thenReturn(Optional.of(existing));

		DeviceTokenResponseDTO.RegisterResponseDTO response = deviceTokenService.registerToken(
				1L,
				new DeviceTokenRequestDTO.RegisterRequestDTO("token-1", DevicePlatform.ANDROID)
		);

		assertThat(response.getDeviceTokenId()).isEqualTo(5L);
		assertThat(response.getPlatform()).isEqualTo(DevicePlatform.ANDROID);
		assertThat(existing.getMember()).isEqualTo(newOwner);
		assertThat(existing.getPlatform()).isEqualTo(DevicePlatform.ANDROID);
		verify(deviceTokenRepository, never()).save(any());
	}

	@Test
	@DisplayName("비활성 회원이 토큰을 등록하면 ME001 예외가 발생한다")
	void registerTokenThrowsWhenMemberInactive() {
		when(memberRepository.findById(1L)).thenReturn(Optional.of(inactiveMember(1L)));

		assertThatThrownBy(() -> deviceTokenService.registerToken(
				1L,
				new DeviceTokenRequestDTO.RegisterRequestDTO("token-1", DevicePlatform.ANDROID)
		)).isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ME001));
	}

	@Test
	@DisplayName("존재하지 않는 회원이 토큰을 등록하면 ME001 예외가 발생한다")
	void registerTokenThrowsWhenMemberNotFound() {
		when(memberRepository.findById(1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> deviceTokenService.registerToken(
				1L,
				new DeviceTokenRequestDTO.RegisterRequestDTO("token-1", DevicePlatform.ANDROID)
		)).isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ME001));
	}

	@Test
	@DisplayName("디바이스 토큰을 해제하면 해당 회원의 토큰을 삭제한다")
	void unregisterTokenDeletesDeviceToken() {
		deviceTokenService.unregisterToken(1L, new DeviceTokenRequestDTO.UnregisterRequestDTO("token-1"));

		verify(deviceTokenRepository).deleteByFcmTokenAndMemberId("token-1", 1L);
	}
}
