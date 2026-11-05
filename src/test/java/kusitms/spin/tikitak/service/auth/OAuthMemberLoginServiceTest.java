package kusitms.spin.tikitak.service.auth;

import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.member.enums.MemberStatus;
import kusitms.spin.tikitak.domain.member.enums.SocialProvider;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.repository.member.MemberRepository;
import kusitms.spin.tikitak.service.auth.dto.LoginCodePayload;
import kusitms.spin.tikitak.service.auth.dto.LoginResponse;
import kusitms.spin.tikitak.service.auth.dto.OAuthUserInfo;
import kusitms.spin.tikitak.service.me.ActiveTeamService;
import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Optional;

import static kusitms.spin.tikitak.support.fixture.AuthFixture.kakaoUserInfo;
import static kusitms.spin.tikitak.support.fixture.AuthFixture.tokenResponse;
import static kusitms.spin.tikitak.support.fixture.MemberFixture.activeMemberWithActiveTeam;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuthMemberLoginServiceTest extends UnitTest {

	@Mock
	private MemberRepository memberRepository;

	@Mock
	private TokenService tokenService;

	@Mock
	private ActiveTeamService activeTeamService;

	@InjectMocks
	private OAuthMemberLoginService oauthMemberLoginService;

	@Test
	@DisplayName("기존 회원은 소셜 프로필을 갱신하고 토큰과 실제 활성 팀 ID를 반환한다")
	void loginWithTokenReturnsActiveTeamIdForExistingMember() {
		OAuthUserInfo userInfo = kakaoUserInfo();
		Member member = activeMemberWithActiveTeam(1L, 10L);
		when(memberRepository.findBySocialProviderAndProviderIdAndStatus(
				SocialProvider.KAKAO,
				"kakao-provider-id",
				MemberStatus.ACTIVE
		)).thenReturn(Optional.of(member));
		when(tokenService.issueToken(1L)).thenReturn(tokenResponse());
		when(activeTeamService.resolveActiveTeamId(member)).thenReturn(10L);

		LoginResponse response = oauthMemberLoginService.loginWithToken(userInfo);

		assertThat(response.accessToken()).isEqualTo("access-token");
		assertThat(response.refreshToken()).isEqualTo("refresh-token");
		assertThat(response.isNewMember()).isFalse();
		assertThat(response.activeTeamId()).isEqualTo(10L);
		assertThat(member.getEmail()).isEqualTo("user@example.com");
		assertThat(member.getName()).isEqualTo("Kakao User");
		assertThat(member.getProfileImgUrl()).isEqualTo("https://example.com/profile.png");
		verify(memberRepository, never()).save(any(Member.class));
	}

	@Test
	@DisplayName("신규 OAuth 회원은 기본 정보로 저장되고 신규 회원으로 응답한다")
	void loginWithTokenCreatesNewMember() {
		OAuthUserInfo userInfo = kakaoUserInfo();
		when(memberRepository.findBySocialProviderAndProviderIdAndStatus(
				SocialProvider.KAKAO,
				"kakao-provider-id",
				MemberStatus.ACTIVE
		)).thenReturn(Optional.empty());
		when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 1L));
		when(tokenService.issueToken(1L)).thenReturn(tokenResponse());
		when(activeTeamService.resolveActiveTeamId(any(Member.class))).thenReturn(null);

		LoginResponse response = oauthMemberLoginService.loginWithToken(userInfo);

		assertThat(response.isNewMember()).isTrue();
		assertThat(response.hasAgreedRequiredTerms()).isFalse();
		assertThat(response.activeTeamId()).isNull();
		verify(memberRepository).save(any(Member.class));
	}

	@Test
	@DisplayName("회원 생성 후 토큰 발급이 실패하면 예외를 그대로 전파한다(같은 트랜잭션이 롤백되도록)")
	void loginWithTokenPropagatesTokenIssuanceFailureAfterMemberCreation() {
		OAuthUserInfo userInfo = kakaoUserInfo();
		BusinessException failure = new BusinessException(ErrorCode.AUTH010);
		when(memberRepository.findBySocialProviderAndProviderIdAndStatus(
				SocialProvider.KAKAO,
				"kakao-provider-id",
				MemberStatus.ACTIVE
		)).thenReturn(Optional.empty());
		when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 1L));
		when(tokenService.issueToken(1L)).thenThrow(failure);

		assertThatThrownBy(() -> oauthMemberLoginService.loginWithToken(userInfo)).isSameAs(failure);

		verify(memberRepository).save(any(Member.class));
		verify(activeTeamService, never()).resolveActiveTeamId(any());
	}

	@Test
	@DisplayName("앱 로그인용 payload는 토큰을 발급하지 않고 회원 정보와 활성 팀 ID만 담는다")
	void prepareLoginCodeReturnsPayloadWithoutIssuingToken() {
		OAuthUserInfo userInfo = kakaoUserInfo();
		Member member = activeMemberWithActiveTeam(1L, 10L);
		when(memberRepository.findBySocialProviderAndProviderIdAndStatus(
				SocialProvider.KAKAO,
				"kakao-provider-id",
				MemberStatus.ACTIVE
		)).thenReturn(Optional.of(member));
		when(activeTeamService.resolveActiveTeamId(member)).thenReturn(10L);

		LoginCodePayload payload = oauthMemberLoginService.prepareLoginCode(userInfo);

		assertThat(payload.getMemberId()).isEqualTo(1L);
		assertThat(payload.isNewMember()).isFalse();
		assertThat(payload.isAgreedRequiredTerms()).isEqualTo(member.isTermsAgreed() && member.isPrivacyAgreed());
		assertThat(payload.getActiveTeamId()).isEqualTo(10L);
		assertThat(member.getEmail()).isEqualTo("user@example.com");
		verify(tokenService, never()).issueToken(any());
	}

	private static Member withId(Member saved, Long id) {
		return Member.builder()
				.id(id)
				.email(saved.getEmail())
				.name(saved.getName())
				.nickname(saved.getNickname())
				.profileImgUrl(saved.getProfileImgUrl())
				.socialProvider(saved.getSocialProvider())
				.providerId(saved.getProviderId())
				.status(saved.getStatus())
				.termsAgreed(saved.isTermsAgreed())
				.privacyAgreed(saved.isPrivacyAgreed())
				.termsAgreedAt(saved.getTermsAgreedAt())
				.activeTeamId(saved.getActiveTeamId())
				.createdAt(saved.getCreatedAt())
				.updatedAt(saved.getUpdatedAt())
				.build();
	}
}
