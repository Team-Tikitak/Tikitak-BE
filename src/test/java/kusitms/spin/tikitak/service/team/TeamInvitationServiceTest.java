package kusitms.spin.tikitak.service.team;

import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.team.entity.Team;
import kusitms.spin.tikitak.domain.team.entity.TeamInvite;
import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus;
import kusitms.spin.tikitak.repository.member.MemberRepository;
import kusitms.spin.tikitak.repository.team.TeamInviteRepository;
import kusitms.spin.tikitak.repository.team.TeamMemberRepository;
import kusitms.spin.tikitak.repository.team.TeamRepository;
import kusitms.spin.tikitak.service.team.dto.TeamInvitationRequestDTO;
import kusitms.spin.tikitak.service.team.dto.TeamInvitationResponseDTO;
import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.Optional;

import static kusitms.spin.tikitak.support.fixture.MemberFixture.activeMemberWithActiveTeam;
import static kusitms.spin.tikitak.support.fixture.TeamFixture.activeTeam;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class TeamInvitationServiceTest extends UnitTest {

	private static final Long MEMBER_ID = 1L;
	private static final Long TEAM_ID = 10L;
	private static final String TOKEN = "invite-token";

	@Mock
	private TeamRepository teamRepository;

	@Mock
	private MemberRepository memberRepository;

	@Mock
	private TeamMemberRepository teamMemberRepository;

	@Mock
	private TeamInviteRepository teamInviteRepository;

	@InjectMocks
	private TeamInvitationService teamInvitationService;

	@Test
	@DisplayName("초대 링크 참여 후 참여한 팀을 활성 팀으로 선택한다")
	void acceptInviteLinkChangesActiveTeamToJoinedTeam() {
		Member member = activeMemberWithActiveTeam(MEMBER_ID, 99L);
		Team team = activeTeam(TEAM_ID);
		TeamInvite invite = TeamInvite.builder()
				.team(team)
				.inviteToken(TOKEN)
				.expiresAt(LocalDateTime.now().plusDays(1))
				.active(true)
				.build();

		when(teamInviteRepository.findByInviteToken(TOKEN)).thenReturn(Optional.of(invite));
		when(teamRepository.findByIdForUpdate(TEAM_ID)).thenReturn(Optional.of(team));
		when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
		when(teamMemberRepository.countByTeamIdAndStatus(TEAM_ID, TeamMemberStatus.ACTIVE)).thenReturn(1L);
		when(teamMemberRepository.findByMemberIdAndTeamId(MEMBER_ID, TEAM_ID)).thenReturn(Optional.empty());
		when(teamMemberRepository.save(any(TeamMember.class))).thenAnswer(inv -> inv.getArgument(0));

		TeamInvitationResponseDTO.JoinTeamResponseDTO response = teamInvitationService.acceptInviteLink(
				MEMBER_ID,
				TOKEN,
				new TeamInvitationRequestDTO.JoinTeamRequestDTO("닉네임", null)
		);

		assertThat(response.getTeamId()).isEqualTo(TEAM_ID);
		assertThat(member.getActiveTeamId()).isEqualTo(TEAM_ID);
	}
}
