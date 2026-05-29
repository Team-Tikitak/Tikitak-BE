package kusitms.spin.tikitak.service.team;

import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.member.enums.ProfileCharacterType;
import kusitms.spin.tikitak.domain.team.entity.Team;
import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import kusitms.spin.tikitak.domain.team.enums.TeamStatus;
import kusitms.spin.tikitak.repository.member.MemberRepository;
import kusitms.spin.tikitak.repository.team.TeamInviteRepository;
import kusitms.spin.tikitak.repository.team.TeamMemberRepository;
import kusitms.spin.tikitak.repository.team.TeamRepository;
import kusitms.spin.tikitak.service.me.DefaultProfileImageResolver;
import kusitms.spin.tikitak.service.team.dto.TeamRequestDTO;
import kusitms.spin.tikitak.service.team.dto.TeamResponseDTO;
import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static kusitms.spin.tikitak.support.fixture.MemberFixture.activeMemberWithCharacterType;
import static kusitms.spin.tikitak.support.fixture.MemberFixture.activeMemberWithActiveTeam;
import static kusitms.spin.tikitak.support.fixture.TeamFixture.activeTeam;
import static kusitms.spin.tikitak.support.fixture.TeamMemberFixture.activeMemberWithoutProfileImg;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamServiceTest extends UnitTest {

	private static final Long MEMBER_ID = 1L;
	private static final Long OTHER_MEMBER_ID = 2L;
	private static final Long TEAM_ID = 10L;
	private static final Long OWNER_TEAM_MEMBER_ID = 100L;
	private static final Long OTHER_TEAM_MEMBER_ID = 101L;

	@Mock
	private TeamRepository teamRepository;

	@Mock
	private MemberRepository memberRepository;

	@Mock
	private TeamMemberRepository teamMemberRepository;

	@Mock
	private TeamInviteRepository teamInviteRepository;

	@Mock
	private DefaultProfileImageResolver defaultProfileImageResolver;

	private TeamService teamService;

	@BeforeEach
	void setUp() {
		teamService = new TeamService(
				teamRepository,
				memberRepository,
				teamMemberRepository,
				teamInviteRepository,
				defaultProfileImageResolver
		);
	}

	@Test
	@DisplayName("팀 생성 후 새 팀을 활성 팀으로 선택한다")
	void createTeamChangesActiveTeamToCreatedTeam() {
		Member member = activeMemberWithActiveTeam(MEMBER_ID, 99L);
		TeamRequestDTO.TeamCreateRequestDTO request =
				new TeamRequestDTO.TeamCreateRequestDTO("팀명", "소개", null, "닉네임");

		when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
		when(teamRepository.save(any())).thenAnswer(inv -> {
			Team savedTeam = inv.getArgument(0);
			ReflectionTestUtils.setField(savedTeam, "id", TEAM_ID);
			return savedTeam;
		});
		when(teamMemberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(teamInviteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		teamService.createTeam(MEMBER_ID, request);

		assertThat(member.getActiveTeamId()).isEqualTo(TEAM_ID);
	}

	@Test
	@DisplayName("팀 생성 시 profileImageUrl이 없으면 TeamMember에 null을 저장한다")
	void createTeamStoresNullWhenProfileImageUrlIsAbsent() {
		Member member = activeMemberWithCharacterType(MEMBER_ID, ProfileCharacterType.TAK_LEADER);
		TeamRequestDTO.TeamCreateRequestDTO request =
				new TeamRequestDTO.TeamCreateRequestDTO("팀명", "소개", null, "닉네임");

		when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
		when(teamRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(teamMemberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(teamInviteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		teamService.createTeam(MEMBER_ID, request);

		ArgumentCaptor<TeamMember> captor = ArgumentCaptor.forClass(TeamMember.class);
		verify(teamMemberRepository).save(captor.capture());
		assertThat(captor.getValue().getProfileImgUrl()).isNull();
	}

	@Test
	@DisplayName("팀 생성 시 profileImageUrl이 있으면 해당 URL을 TeamMember에 저장한다")
	void createTeamStoresProvidedProfileImageUrl() {
		Member member = activeMemberWithCharacterType(MEMBER_ID, ProfileCharacterType.TAK_LEADER);
		String customImgUrl = "https://example.com/my-photo.png";
		TeamRequestDTO.TeamCreateRequestDTO request =
				new TeamRequestDTO.TeamCreateRequestDTO("팀명", "소개", customImgUrl, "닉네임");

		when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
		when(teamRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(teamMemberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(teamInviteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		teamService.createTeam(MEMBER_ID, request);

		ArgumentCaptor<TeamMember> captor = ArgumentCaptor.forClass(TeamMember.class);
		verify(teamMemberRepository).save(captor.capture());
		assertThat(captor.getValue().getProfileImgUrl()).isEqualTo(customImgUrl);
	}

	@Test
	@DisplayName("팀 상세 조회 시 profileImgUrl이 없으면 내 프로필과 팀원 모두 기본 이미지로 fallback된다")
	void viewTeamDetailUsesDefaultProfileImageWhenProfileImgUrlIsNull() {
		Member ownerMember = activeMemberWithCharacterType(MEMBER_ID, ProfileCharacterType.TAK_LEADER);
		Member otherMember = activeMemberWithCharacterType(OTHER_MEMBER_ID, ProfileCharacterType.TAK_SPARK);
		String ownerDefaultImg = "https://cdn.example.com/default-profiles/tak-leader.png";
		String otherDefaultImg = "https://cdn.example.com/default-profiles/tak-spark.png";

		Team tempTeam = activeTeam(TEAM_ID);
		TeamMember owner = activeMemberWithoutProfileImg(OWNER_TEAM_MEMBER_ID, ownerMember, tempTeam);
		TeamMember other = activeMemberWithoutProfileImg(OTHER_TEAM_MEMBER_ID, otherMember, tempTeam);
		Team teamWithMembers = Team.builder()
				.id(TEAM_ID)
				.name("팀명")
				.status(TeamStatus.ACTIVE)
				.teamMembers(List.of(owner, other))
				.build();

		when(teamRepository.findTeamWithTeamMembersById(TEAM_ID)).thenReturn(Optional.of(teamWithMembers));
		when(defaultProfileImageResolver.resolve(ProfileCharacterType.TAK_LEADER)).thenReturn(ownerDefaultImg);
		when(defaultProfileImageResolver.resolve(ProfileCharacterType.TAK_SPARK)).thenReturn(otherDefaultImg);

		TeamResponseDTO.TeamDetailResponseDTO response = teamService.viewTeamDetail(MEMBER_ID, TEAM_ID);

		assertThat(response.getMyProfile().getProfileImgUrl()).isEqualTo(ownerDefaultImg);
		assertThat(response.getTeamMembers()).hasSize(1);
		assertThat(response.getTeamMembers().get(0).getProfileImgUrl()).isEqualTo(otherDefaultImg);
	}

	@Test
	@DisplayName("팀 상세 조회 시 profileImgUrl이 있으면 저장된 이미지를 그대로 반환한다")
	void viewTeamDetailReturnsStoredProfileImageWhenPresent() {
		Member ownerMember = activeMemberWithCharacterType(MEMBER_ID, ProfileCharacterType.TAK_LEADER);

		Team tempTeam = activeTeam(TEAM_ID);
		TeamMember ownerWithImg = kusitms.spin.tikitak.support.fixture.TeamMemberFixture.activeMember(
				OWNER_TEAM_MEMBER_ID, ownerMember, tempTeam);
		Team teamWithMembers = Team.builder()
				.id(TEAM_ID)
				.name("팀명")
				.status(TeamStatus.ACTIVE)
				.teamMembers(List.of(ownerWithImg))
				.build();

		when(teamRepository.findTeamWithTeamMembersById(TEAM_ID)).thenReturn(Optional.of(teamWithMembers));

		TeamResponseDTO.TeamDetailResponseDTO response = teamService.viewTeamDetail(MEMBER_ID, TEAM_ID);

		assertThat(response.getMyProfile().getProfileImgUrl())
				.isEqualTo("https://example.com/team-member" + OWNER_TEAM_MEMBER_ID + ".png");
	}
}
