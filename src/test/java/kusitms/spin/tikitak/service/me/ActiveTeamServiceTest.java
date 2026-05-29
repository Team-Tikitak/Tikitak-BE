package kusitms.spin.tikitak.service.me;

import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.team.entity.Team;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus;
import kusitms.spin.tikitak.domain.team.enums.TeamStatus;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.repository.team.TeamMemberRepository;
import kusitms.spin.tikitak.repository.team.TeamRepository;
import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Optional;

import static kusitms.spin.tikitak.support.fixture.MemberFixture.activeMemberWithActiveTeam;
import static kusitms.spin.tikitak.support.fixture.TeamFixture.activeTeam;
import static kusitms.spin.tikitak.support.fixture.TeamFixture.inactiveTeam;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class ActiveTeamServiceTest extends UnitTest {

	@Mock
	private TeamRepository teamRepository;

	@Mock
	private TeamMemberRepository teamMemberRepository;

	@InjectMocks
	private ActiveTeamService activeTeamService;

	@Test
	@DisplayName("저장된 활성 팀이 유효하면 activeTeamId를 반환한다")
	void resolveActiveTeamIdReturnsStoredIdWhenValid() {
		Member member = activeMemberWithActiveTeam(1L, 10L);
		when(teamMemberRepository.existsByTeamIdAndMemberIdAndStatusAndTeamStatus(
				10L, 1L, TeamMemberStatus.ACTIVE, TeamStatus.ACTIVE
		)).thenReturn(true);

		Long activeTeamId = activeTeamService.resolveActiveTeamId(member);

		assertThat(activeTeamId).isEqualTo(10L);
		assertThat(member.getActiveTeamId()).isEqualTo(10L);
	}

	@Test
	@DisplayName("저장된 활성 팀이 유효하지 않으면 activeTeamId를 비우고 null을 반환한다")
	void resolveActiveTeamIdClearsInvalidStoredId() {
		Member member = activeMemberWithActiveTeam(1L, 10L);
		when(teamMemberRepository.existsByTeamIdAndMemberIdAndStatusAndTeamStatus(
				10L, 1L, TeamMemberStatus.ACTIVE, TeamStatus.ACTIVE
		)).thenReturn(false);

		Long activeTeamId = activeTeamService.resolveActiveTeamId(member);

		assertThat(activeTeamId).isNull();
		assertThat(member.getActiveTeamId()).isNull();
	}

	@Test
	@DisplayName("활성 팀 변경 대상 팀이 존재하지 않으면 TEAM001 예외가 발생한다")
	void validateChangeableTeamThrowsWhenTeamNotFound() {
		when(teamRepository.findById(10L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> activeTeamService.validateChangeableTeam(1L, 10L))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TEAM001));
	}

	@Test
	@DisplayName("INACTIVE 팀은 활성 팀으로 변경할 수 없다")
	void validateChangeableTeamThrowsWhenTeamInactive() {
		Team team = inactiveTeam(10L);
		when(teamRepository.findById(10L)).thenReturn(Optional.of(team));

		assertThatThrownBy(() -> activeTeamService.validateChangeableTeam(1L, 10L))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ME007));
	}

	@Test
	@DisplayName("ACTIVE 팀이고 현재 사용자의 ACTIVE 팀멤버십이 있으면 활성 팀 변경 대상이다")
	void validateChangeableTeamPassesWhenTeamAndMembershipActive() {
		Team team = activeTeam(10L);
		when(teamRepository.findById(10L)).thenReturn(Optional.of(team));
		when(teamMemberRepository.existsByTeamIdAndMemberIdAndStatusAndTeamStatus(
				10L, 1L, TeamMemberStatus.ACTIVE, TeamStatus.ACTIVE
		)).thenReturn(true);

		activeTeamService.validateChangeableTeam(1L, 10L);
	}
}
