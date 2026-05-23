package kusitms.spin.tikitak.service.me;

import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.member.enums.ProfileCharacterType;
import kusitms.spin.tikitak.domain.team.entity.Team;
import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus;
import kusitms.spin.tikitak.domain.team.enums.TeamStatus;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.repository.member.MemberRepository;
import kusitms.spin.tikitak.repository.team.TeamMemberRepository;
import kusitms.spin.tikitak.service.auth.TokenService;
import kusitms.spin.tikitak.service.me.dto.MeRequestDTO;
import kusitms.spin.tikitak.service.me.dto.MeResponseDTO;
import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;
import java.util.Optional;

import static kusitms.spin.tikitak.support.fixture.MemberFixture.activeMember;
import static kusitms.spin.tikitak.support.fixture.MemberFixture.activeMemberWithActiveTeam;
import static kusitms.spin.tikitak.support.fixture.MemberFixture.inactiveMember;
import static kusitms.spin.tikitak.support.fixture.TeamFixture.activeTeam;
import static kusitms.spin.tikitak.support.fixture.TeamMemberFixture.activeOwner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MeServiceTest extends UnitTest {

	@Mock
	private MemberRepository memberRepository;

	@Mock
	private TeamMemberRepository teamMemberRepository;

	@Mock
	private TokenService tokenService;

	@Mock
	private ActiveTeamService activeTeamService;

	@InjectMocks
	private MeService meService;

	@Test
	@DisplayName("내 계정 정보는 활성 팀과 참여 팀 보유 여부를 분리해서 반환한다")
	void getMyProfileReturnsActiveTeamAndHasTeamSeparately() {
		Member member = activeMemberWithActiveTeam(1L, 10L);
		when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
		when(activeTeamService.resolveActiveTeamId(member)).thenReturn(10L);
		when(teamMemberRepository.existsActiveMembership(1L, TeamMemberStatus.ACTIVE, TeamStatus.ACTIVE))
				.thenReturn(true);

		MeResponseDTO.MeProfileResponseDTO response = meService.getMyProfile(1L);

		assertThat(response.getMemberId()).isEqualTo(1L);
		assertThat(response.getName()).isEqualTo("User 1");
		assertThat(response.getActiveTeamId()).isEqualTo(10L);
		assertThat(response.isHasTeam()).isTrue();
		assertThat(response.isOnboardingCompleted()).isFalse();
		assertThat(response.getProfileCharacterType()).isNull();
	}

	@Test
	@DisplayName("비활성 회원은 내 계정 정보 조회 시 ME001 예외가 발생한다")
	void getMyProfileThrowsWhenMemberInactive() {
		Member member = inactiveMember(1L);
		when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

		assertThatThrownBy(() -> meService.getMyProfile(1L))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ME001));
	}

	@Test
	@DisplayName("내 참여 팀 목록은 활성 팀을 먼저, 이후 최근 합류순으로 반환한다")
	void getMyTeamsSortsActiveTeamFirstThenJoinedAtDesc() {
		Member member = activeMemberWithActiveTeam(1L, 20L);
		Team oldTeam = activeTeam(10L);
		Team activeTeam = activeTeam(20L);
		Team newTeam = activeTeam(30L);
		TeamMember oldMembership = kusitms.spin.tikitak.support.fixture.TeamMemberFixture.activeMember(1L, member, oldTeam);
		TeamMember activeMembership = activeOwner(2L, member, activeTeam);
		TeamMember newMembership = kusitms.spin.tikitak.support.fixture.TeamMemberFixture.activeMember(3L, member, newTeam);

		when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
		when(activeTeamService.resolveActiveTeamId(member)).thenReturn(20L);
		when(teamMemberRepository.findActiveTeamMemberships(1L, TeamMemberStatus.ACTIVE, TeamStatus.ACTIVE))
				.thenReturn(List.of(oldMembership, activeMembership, newMembership));
		when(teamMemberRepository.countActiveMembersByTeamIds(List.of(10L, 20L, 30L), TeamMemberStatus.ACTIVE))
				.thenReturn(List.of(new Object[]{10L, 2L}, new Object[]{20L, 5L}, new Object[]{30L, 1L}));

		MeResponseDTO.TeamListResponseDTO response = meService.getMyTeams(1L);

		assertThat(response.getTeams())
				.extracting(MeResponseDTO.TeamItemDTO::getTeamId)
				.containsExactly(20L, 30L, 10L);
		assertThat(response.getTeams().getFirst().isActive()).isTrue();
		assertThat(response.getTeams().getFirst().getMemberCount()).isEqualTo(5L);
	}

	@Test
	@DisplayName("활성 팀 변경은 변경 가능한 팀 검증 후 member에 위임한다")
	void updateActiveTeamChangesMemberActiveTeam() {
		Member member = activeMember(1L);
		when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

		MeResponseDTO.ActiveTeamUpdateResponseDTO response = meService.updateActiveTeam(
				1L,
				new MeRequestDTO.ActiveTeamUpdateRequestDTO(10L)
		);

		verify(activeTeamService).validateChangeableTeam(1L, 10L);
		assertThat(response.getActiveTeamId()).isEqualTo(10L);
		assertThat(member.getActiveTeamId()).isEqualTo(10L);
	}

	@Test
	@DisplayName("필수 약관은 모두 true일 때 저장하고 최초 동의 시각을 기록한다")
	void updateAgreementsStoresRequiredTerms() {
		Member member = activeMember(1L);
		when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

		MeResponseDTO.AgreementResponseDTO response = meService.updateAgreements(
				1L,
				new MeRequestDTO.AgreementUpdateRequestDTO(true, true)
		);

		assertThat(response.isTermsAgreed()).isTrue();
		assertThat(response.isPrivacyAgreed()).isTrue();
		assertThat(response.getTermsAgreedAt()).isNotNull();
	}

	@Test
	@DisplayName("필수 약관 중 하나라도 false면 AGREEMENT002 예외가 발생한다")
	void updateAgreementsThrowsWhenRequiredTermIsFalse() {
		assertThatThrownBy(() -> meService.updateAgreements(
				1L,
				new MeRequestDTO.AgreementUpdateRequestDTO(true, false)
		)).isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AGREEMENT002));
	}

	@Test
	@DisplayName("온보딩 프로필 캐릭터를 저장하고 완료 상태로 변경한다")
	void updateOnboardingStoresProfileCharacter() {
		Member member = activeMember(1L);
		when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

		MeResponseDTO.OnboardingUpdateResponseDTO response = meService.updateOnboarding(
				1L,
				new MeRequestDTO.OnboardingUpdateRequestDTO(ProfileCharacterType.TAK_SPARK)
		);

		assertThat(response.isOnboardingCompleted()).isTrue();
		assertThat(response.getProfileCharacterType()).isEqualTo(ProfileCharacterType.TAK_SPARK);
		assertThat(member.isOnboardingCompleted()).isTrue();
		assertThat(member.getProfileCharacterType()).isEqualTo(ProfileCharacterType.TAK_SPARK);
	}

	@Test
	@DisplayName("탈퇴 시 비활성화하고 providerId를 탈퇴 마커로 치환한 뒤 refresh token을 폐기한다")
	void withdrawMarksMemberInactiveAndReplacesProviderId() {
		Member member = activeMember(1L);
		when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
		when(teamMemberRepository.existsActiveOwnerTeam(
				1L,
				kusitms.spin.tikitak.domain.team.enums.TeamMemberRole.OWNER,
				TeamMemberStatus.ACTIVE,
				TeamStatus.ACTIVE
		)).thenReturn(false);

		meService.withdraw(1L);

		assertThat(member.getStatus()).isEqualTo(kusitms.spin.tikitak.domain.member.enums.MemberStatus.INACTIVE);
		assertThat(member.getDeletedAt()).isNotNull();
		assertThat(member.getProviderId()).startsWith("WITHDRAWN:1:");
		assertThat(member.getProviderId()).isNotEqualTo("provider-1");
		verify(tokenService).revokeAllRefreshTokens(1L);
	}
}
