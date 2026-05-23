package kusitms.spin.tikitak.service.team;

import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.member.enums.ProfileCharacterType;
import kusitms.spin.tikitak.domain.team.entity.Team;
import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import kusitms.spin.tikitak.domain.team.enums.TeamStatus;
import kusitms.spin.tikitak.repository.team.TeamMemberRepository;
import kusitms.spin.tikitak.repository.team.TeamRepository;
import kusitms.spin.tikitak.service.me.DefaultProfileImageResolver;
import kusitms.spin.tikitak.service.team.dto.TeamMemberResponseDTO;
import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.List;
import java.util.Optional;

import static kusitms.spin.tikitak.support.fixture.MemberFixture.activeMemberWithCharacterType;
import static kusitms.spin.tikitak.support.fixture.TeamFixture.activeTeam;
import static kusitms.spin.tikitak.support.fixture.TeamMemberFixture.activeMember;
import static kusitms.spin.tikitak.support.fixture.TeamMemberFixture.activeMemberWithoutProfileImg;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class TeamMemberServiceTest extends UnitTest {

	private static final Long MEMBER_ID = 1L;
	private static final Long OTHER_MEMBER_ID = 2L;
	private static final Long TEAM_ID = 10L;
	private static final Long TEAM_MEMBER_ID = 100L;
	private static final Long OTHER_TEAM_MEMBER_ID = 101L;

	@Mock
	private TeamRepository teamRepository;

	@Mock
	private TeamMemberRepository teamMemberRepository;

	@Mock
	private DefaultProfileImageResolver defaultProfileImageResolver;

	private TeamMemberService teamMemberService;

	@BeforeEach
	void setUp() {
		teamMemberService = new TeamMemberService(
				teamRepository,
				teamMemberRepository,
				defaultProfileImageResolver
		);
	}

	@Test
	@DisplayName("팀원 목록 조회 시 profileImgUrl이 없으면 기본 캐릭터 이미지로 fallback된다")
	void listTeamMembersUsesDefaultProfileImageWhenProfileImgUrlIsNull() {
		Member member = activeMemberWithCharacterType(MEMBER_ID, ProfileCharacterType.TAK_LEADER);
		Member otherMember = activeMemberWithCharacterType(OTHER_MEMBER_ID, ProfileCharacterType.TAK_SPARK);
		String memberDefaultImg = "https://cdn.example.com/default-profiles/tak-leader.png";
		String otherDefaultImg = "https://cdn.example.com/default-profiles/tak-spark.png";

		Team tempTeam = activeTeam(TEAM_ID);
		TeamMember memberTm = activeMemberWithoutProfileImg(TEAM_MEMBER_ID, member, tempTeam);
		TeamMember otherTm = activeMemberWithoutProfileImg(OTHER_TEAM_MEMBER_ID, otherMember, tempTeam);
		Team teamWithMembers = Team.builder()
				.id(TEAM_ID)
				.name("팀명")
				.status(TeamStatus.ACTIVE)
				.teamMembers(List.of(memberTm, otherTm))
				.build();

		when(teamRepository.findTeamWithTeamMembersById(TEAM_ID)).thenReturn(Optional.of(teamWithMembers));
		when(defaultProfileImageResolver.resolve(ProfileCharacterType.TAK_LEADER)).thenReturn(memberDefaultImg);
		when(defaultProfileImageResolver.resolve(ProfileCharacterType.TAK_SPARK)).thenReturn(otherDefaultImg);

		TeamMemberResponseDTO.TeamMemberListResponseDTO response =
				teamMemberService.listTeamMembers(MEMBER_ID, TEAM_ID);

		assertThat(response.getMembers()).hasSize(2);
		assertThat(response.getMembers().get(0).getProfileImgUrl()).isEqualTo(memberDefaultImg);
		assertThat(response.getMembers().get(1).getProfileImgUrl()).isEqualTo(otherDefaultImg);
	}

	@Test
	@DisplayName("팀원 목록 조회 시 profileImgUrl이 있으면 저장된 이미지를 그대로 반환한다")
	void listTeamMembersReturnsStoredProfileImageWhenPresent() {
		Member member = activeMemberWithCharacterType(MEMBER_ID, ProfileCharacterType.TAK_LEADER);

		Team tempTeam = activeTeam(TEAM_ID);
		TeamMember memberTm = activeMember(TEAM_MEMBER_ID, member, tempTeam);
		Team teamWithMembers = Team.builder()
				.id(TEAM_ID)
				.name("팀명")
				.status(TeamStatus.ACTIVE)
				.teamMembers(List.of(memberTm))
				.build();

		when(teamRepository.findTeamWithTeamMembersById(TEAM_ID)).thenReturn(Optional.of(teamWithMembers));

		TeamMemberResponseDTO.TeamMemberListResponseDTO response =
				teamMemberService.listTeamMembers(MEMBER_ID, TEAM_ID);

		assertThat(response.getMembers()).hasSize(1);
		assertThat(response.getMembers().get(0).getProfileImgUrl())
				.isEqualTo("https://example.com/team-member" + TEAM_MEMBER_ID + ".png");
	}
}
