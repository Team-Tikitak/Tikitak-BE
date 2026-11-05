package kusitms.spin.tikitak.service.auth;

import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.member.enums.MemberStatus;
import kusitms.spin.tikitak.repository.member.MemberRepository;
import kusitms.spin.tikitak.service.auth.dto.LoginCodePayload;
import kusitms.spin.tikitak.service.auth.dto.LoginResponse;
import kusitms.spin.tikitak.service.auth.dto.OAuthUserInfo;
import kusitms.spin.tikitak.service.auth.dto.TokenResponse;
import kusitms.spin.tikitak.service.me.ActiveTeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OAuth 외부 호출이 끝난 뒤의 DB 작업(회원 조회/생성, 프로필 갱신, 활성 팀 확인, 토큰 발급)을 한 트랜잭션으로 묶는다.
 * 외부 호출은 {@link AuthService}에서 트랜잭션 밖에서 실행한다.
 * 반환값은 엔티티가 아니라 필요한 값만 담은 DTO다(트랜잭션 밖 LAZY 접근 방지).
 */
@Service
@RequiredArgsConstructor
public class OAuthMemberLoginService {

	private final MemberRepository memberRepository;
	private final TokenService tokenService;
	private final ActiveTeamService activeTeamService;

	@Transactional
	public LoginResponse loginWithToken(OAuthUserInfo userInfo) {
		MemberLoginResult result = findOrCreateMember(userInfo);
		Member member = result.member();
		TokenResponse token = tokenService.issueToken(member.getId());
		return new LoginResponse(
				token.accessToken(),
				token.refreshToken(),
				result.newMember(),
				member.isTermsAgreed() && member.isPrivacyAgreed(),
				activeTeamService.resolveActiveTeamId(member)
		);
	}

	@Transactional
	public LoginCodePayload prepareLoginCode(OAuthUserInfo userInfo) {
		MemberLoginResult result = findOrCreateMember(userInfo);
		Member member = result.member();
		return LoginCodePayload.builder()
				.memberId(member.getId())
				.newMember(result.newMember())
				.agreedRequiredTerms(member.isTermsAgreed() && member.isPrivacyAgreed())
				.activeTeamId(activeTeamService.resolveActiveTeamId(member))
				.build();
	}

	private MemberLoginResult findOrCreateMember(OAuthUserInfo userInfo) {
		boolean[] created = {false};
		Member member = memberRepository
				.findBySocialProviderAndProviderIdAndStatus(
						userInfo.provider(),
						userInfo.providerId(),
						MemberStatus.ACTIVE
				)
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

		return new MemberLoginResult(member, created[0]);
	}

	private String defaultNickname(OAuthUserInfo userInfo) {
		if (userInfo.name() != null && !userInfo.name().isBlank()) {
			return truncate(userInfo.name());
		}
		String email = userInfo.email();
		if (email != null && email.contains("@")) {
			return truncate(email.substring(0, email.indexOf('@')));
		}
		return "Tikitak User";
	}

	private String truncate(String value) {
		return value.length() > 30 ? value.substring(0, 30) : value;
	}

	private record MemberLoginResult(Member member, boolean newMember) {
	}
}
