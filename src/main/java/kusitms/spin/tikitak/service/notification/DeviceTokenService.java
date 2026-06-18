package kusitms.spin.tikitak.service.notification;

import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.member.enums.MemberStatus;
import kusitms.spin.tikitak.domain.notification.entity.MemberDeviceToken;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.repository.member.MemberRepository;
import kusitms.spin.tikitak.repository.notification.MemberDeviceTokenRepository;
import kusitms.spin.tikitak.service.notification.dto.DeviceTokenRequestDTO;
import kusitms.spin.tikitak.service.notification.dto.DeviceTokenResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeviceTokenService {

	private final MemberRepository memberRepository;
	private final MemberDeviceTokenRepository deviceTokenRepository;

	@Transactional
	public DeviceTokenResponseDTO.RegisterResponseDTO registerToken(
			Long memberId,
			DeviceTokenRequestDTO.RegisterRequestDTO request
	) {
		try {
			Member member = getActiveMember(memberId);

			MemberDeviceToken deviceToken = deviceTokenRepository.findByFcmToken(request.getFcmToken())
					.map(existing -> {
						existing.reassign(member, request.getPlatform());
						return existing;
					})
					.orElseGet(() -> deviceTokenRepository.save(
							MemberDeviceToken.register(member, request.getFcmToken(), request.getPlatform())
					));

			return DeviceTokenResponseDTO.RegisterResponseDTO.builder()
					.deviceTokenId(deviceToken.getId())
					.platform(deviceToken.getPlatform())
					.build();
		} catch (BusinessException e) {
			throw e;
		} catch (Exception e) {
			throw new BusinessException(ErrorCode.DEVICE_TOKEN001, e);
		}
	}

	@Transactional
	public void unregisterToken(Long memberId, DeviceTokenRequestDTO.UnregisterRequestDTO request) {
		deviceTokenRepository.deleteByFcmTokenAndMemberId(request.getFcmToken(), memberId);
	}

	private Member getActiveMember(Long memberId) {
		Member member = memberRepository.findById(memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ME001));
		if (member.getStatus() != MemberStatus.ACTIVE) {
			throw new BusinessException(ErrorCode.ME001);
		}
		return member;
	}
}
