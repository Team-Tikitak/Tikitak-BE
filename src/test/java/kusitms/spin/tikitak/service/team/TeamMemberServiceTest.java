package kusitms.spin.tikitak.service.team;

import kusitms.spin.tikitak.domain.media.entity.Media;
import kusitms.spin.tikitak.domain.media.enums.MediaPurpose;
import kusitms.spin.tikitak.domain.media.enums.MediaStatus;
import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.member.enums.ProfileCharacterType;
import kusitms.spin.tikitak.domain.team.entity.Team;
import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberRole;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus;
import kusitms.spin.tikitak.domain.team.enums.TeamStatus;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.repository.media.MediaRepository;
import kusitms.spin.tikitak.repository.team.TeamMemberRepository;
import kusitms.spin.tikitak.repository.team.TeamRepository;
import kusitms.spin.tikitak.service.me.DefaultProfileImageResolver;
import kusitms.spin.tikitak.service.team.dto.TeamMemberRequestDTO;
import kusitms.spin.tikitak.service.team.dto.TeamMemberResponseDTO;
import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static kusitms.spin.tikitak.support.fixture.MediaFixture.media;
import static kusitms.spin.tikitak.support.fixture.MemberFixture.activeMemberWithCharacterType;
import static kusitms.spin.tikitak.support.fixture.TeamFixture.activeTeam;
import static kusitms.spin.tikitak.support.fixture.TeamMemberFixture.activeMember;
import static kusitms.spin.tikitak.support.fixture.TeamMemberFixture.activeMemberWithoutProfileImg;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamMemberServiceTest extends UnitTest {

	private static final Long MEMBER_ID = 1L;
	private static final Long OTHER_MEMBER_ID = 2L;
	private static final Long TEAM_ID = 10L;
	private static final Long TEAM_MEMBER_ID = 100L;
	private static final Long OTHER_TEAM_MEMBER_ID = 101L;
	private static final String ORIGINAL_NICKNAME = "original-nickname";
	private static final String NEW_NICKNAME = "new-nickname";
	private static final UUID OLD_MEDIA_PUBLIC_ID = UUID.fromString("00000000-0000-0000-0000-000000000100");
	private static final UUID NEW_MEDIA_PUBLIC_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");

	@Mock
	private TeamRepository teamRepository;

	@Mock
	private TeamMemberRepository teamMemberRepository;

	@Mock
	private MediaRepository mediaRepository;

	@Mock
	private DefaultProfileImageResolver defaultProfileImageResolver;

	private TeamMemberService teamMemberService;

	@BeforeEach
	void setUp() {
		teamMemberService = new TeamMemberService(
				teamRepository,
				teamMemberRepository,
				defaultProfileImageResolver,
				new TeamProfileMediaResolver(mediaRepository)
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
		when(defaultProfileImageResolver.resolveForTeamMember(memberTm)).thenReturn(memberDefaultImg);
		when(defaultProfileImageResolver.resolveForTeamMember(otherTm)).thenReturn(otherDefaultImg);

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
		when(defaultProfileImageResolver.resolveForTeamMember(memberTm))
				.thenReturn("https://example.com/team-member" + TEAM_MEMBER_ID + ".png");

		TeamMemberResponseDTO.TeamMemberListResponseDTO response =
				teamMemberService.listTeamMembers(MEMBER_ID, TEAM_ID);

		assertThat(response.getMembers()).hasSize(1);
		assertThat(response.getMembers().get(0).getProfileImgUrl())
				.isEqualTo("https://example.com/team-member" + TEAM_MEMBER_ID + ".png");
	}

	@Test
	@DisplayName("내 프로필 수정 시 본인 소유 PROFILE_IMAGE UPLOADED 새 media면 기존 media는 DELETED, 새 media는 USED가 되고 새 url로 바뀐다")
	void updateMyProfileReplacesProfileMedia() {
		Media oldProfileMedia = oldProfileMedia();
		TeamMember teamMember = teamMemberWithProfileMedia(oldProfileMedia);
		Media newProfileMedia = Media.builder()
				.id(201L)
				.publicId(NEW_MEDIA_PUBLIC_ID)
				.memberId(MEMBER_ID)
				.purpose(MediaPurpose.PROFILE_IMAGE)
				.status(MediaStatus.UPLOADED)
				.fileName("new-profile.png")
				.contentType("image/png")
				.size(1024L)
				.url("https://example.com/new-profile.png")
				.key("media/profile-image/" + NEW_MEDIA_PUBLIC_ID + ".png")
				.build();
		stubTeamMember(teamMember);
		stubProfileImageResolver(teamMember);
		when(mediaRepository.findByPublicIdForUpdate(NEW_MEDIA_PUBLIC_ID)).thenReturn(Optional.of(newProfileMedia));

		TeamMemberResponseDTO.TeamMemberUpdateResponseDTO response =
				teamMemberService.updateMyProfile(MEMBER_ID, TEAM_ID, updateRequest(NEW_MEDIA_PUBLIC_ID));

		assertThat(oldProfileMedia.getStatus()).isEqualTo(MediaStatus.DELETED);
		assertThat(newProfileMedia.getStatus()).isEqualTo(MediaStatus.USED);
		assertThat(teamMember.getProfileMedia()).isSameAs(newProfileMedia);
		assertThat(teamMember.getProfileImgUrl()).isEqualTo("https://example.com/new-profile.png");
		assertThat(teamMember.getNickname()).isEqualTo(NEW_NICKNAME);
		assertThat(response.getNickname()).isEqualTo(NEW_NICKNAME);
		assertThat(response.getProfileImgUrl()).isEqualTo("https://example.com/new-profile.png");
	}

	@Test
	@DisplayName("내 프로필 수정 시 지금 연결된 media를 다시 보내면 media 조회 없이 닉네임만 바뀌고 기존 media는 USED로 남는다")
	void updateMyProfileKeepsCurrentProfileMediaWhenResent() {
		Media oldProfileMedia = oldProfileMedia();
		TeamMember teamMember = teamMemberWithProfileMedia(oldProfileMedia);
		String oldProfileImgUrl = teamMember.getProfileImgUrl();
		stubTeamMember(teamMember);
		stubProfileImageResolver(teamMember);

		TeamMemberResponseDTO.TeamMemberUpdateResponseDTO response =
				teamMemberService.updateMyProfile(MEMBER_ID, TEAM_ID, updateRequest(OLD_MEDIA_PUBLIC_ID));

		assertThat(response.getNickname()).isEqualTo(NEW_NICKNAME);
		assertThat(response.getProfileImgUrl()).isEqualTo(oldProfileImgUrl);
		assertThat(teamMember.getNickname()).isEqualTo(NEW_NICKNAME);
		assertThat(teamMember.getProfileMedia()).isSameAs(oldProfileMedia);
		assertThat(teamMember.getProfileImgUrl()).isEqualTo(oldProfileImgUrl);
		assertThat(oldProfileMedia.getStatus()).isEqualTo(MediaStatus.USED);
		verify(mediaRepository, never()).findByPublicIdForUpdate(any());
	}

	@Test
	@DisplayName("내 프로필 수정 시 media가 없으면 MEDIA018 예외가 발생하고 닉네임·이미지·기존 media가 바뀌지 않는다")
	void updateMyProfileThrowsWhenProfileMediaNotFound() {
		Media oldProfileMedia = oldProfileMedia();
		TeamMember teamMember = teamMemberWithProfileMedia(oldProfileMedia);
		String oldProfileImgUrl = teamMember.getProfileImgUrl();
		stubTeamMember(teamMember);
		when(mediaRepository.findByPublicIdForUpdate(NEW_MEDIA_PUBLIC_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> teamMemberService.updateMyProfile(MEMBER_ID, TEAM_ID, updateRequest(NEW_MEDIA_PUBLIC_ID)))
				.isInstanceOf(BusinessException.class)
				.satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.MEDIA018));

		verify(mediaRepository).findByPublicIdForUpdate(NEW_MEDIA_PUBLIC_ID);
		assertProfileUnchanged(teamMember, oldProfileMedia, oldProfileImgUrl);
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("invalidProfileMedia")
	@DisplayName("내 프로필 수정 시 검증에 실패한 media면 MEDIA018 예외가 발생하고 닉네임·이미지·기존 media·요청 media가 바뀌지 않는다")
	void updateMyProfileThrowsWhenProfileMediaIsInvalid(
			String caseName, Long ownerId, MediaPurpose purpose, MediaStatus status
	) {
		Media oldProfileMedia = oldProfileMedia();
		TeamMember teamMember = teamMemberWithProfileMedia(oldProfileMedia);
		String oldProfileImgUrl = teamMember.getProfileImgUrl();
		Media requestedMedia = media(201L, ownerId, NEW_MEDIA_PUBLIC_ID, purpose, status);
		stubTeamMember(teamMember);
		when(mediaRepository.findByPublicIdForUpdate(NEW_MEDIA_PUBLIC_ID)).thenReturn(Optional.of(requestedMedia));

		assertThatThrownBy(() -> teamMemberService.updateMyProfile(MEMBER_ID, TEAM_ID, updateRequest(NEW_MEDIA_PUBLIC_ID)))
				.isInstanceOf(BusinessException.class)
				.satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.MEDIA018));

		verify(mediaRepository).findByPublicIdForUpdate(NEW_MEDIA_PUBLIC_ID);
		assertThat(requestedMedia.getStatus()).isEqualTo(status);
		assertProfileUnchanged(teamMember, oldProfileMedia, oldProfileImgUrl);
	}

	@Test
	@DisplayName("내 프로필 수정 시 mediaPublicId가 null이면 media 조회 없이 닉네임만 바뀐다")
	void updateMyProfileChangesNicknameOnlyWhenMediaPublicIdIsNull() {
		Media oldProfileMedia = oldProfileMedia();
		TeamMember teamMember = teamMemberWithProfileMedia(oldProfileMedia);
		String oldProfileImgUrl = teamMember.getProfileImgUrl();
		stubTeamMember(teamMember);
		stubProfileImageResolver(teamMember);

		TeamMemberResponseDTO.TeamMemberUpdateResponseDTO response =
				teamMemberService.updateMyProfile(MEMBER_ID, TEAM_ID, updateRequest(null));

		assertThat(response.getNickname()).isEqualTo(NEW_NICKNAME);
		assertThat(teamMember.getNickname()).isEqualTo(NEW_NICKNAME);
		assertThat(teamMember.getProfileMedia()).isSameAs(oldProfileMedia);
		assertThat(teamMember.getProfileImgUrl()).isEqualTo(oldProfileImgUrl);
		assertThat(oldProfileMedia.getStatus()).isEqualTo(MediaStatus.USED);
		verify(mediaRepository, never()).findByPublicIdForUpdate(any());
	}

	private static Stream<Arguments> invalidProfileMedia() {
		return Stream.of(
				Arguments.of("다른 회원 소유", OTHER_MEMBER_ID, MediaPurpose.PROFILE_IMAGE, MediaStatus.UPLOADED),
				Arguments.of("purpose 불일치", MEMBER_ID, MediaPurpose.FEED_IMAGE, MediaStatus.UPLOADED),
				Arguments.of("PENDING", MEMBER_ID, MediaPurpose.PROFILE_IMAGE, MediaStatus.PENDING),
				Arguments.of("현재 media가 아닌 USED", MEMBER_ID, MediaPurpose.PROFILE_IMAGE, MediaStatus.USED),
				Arguments.of("DELETED", MEMBER_ID, MediaPurpose.PROFILE_IMAGE, MediaStatus.DELETED)
		);
	}

	private Media oldProfileMedia() {
		return media(200L, MEMBER_ID, OLD_MEDIA_PUBLIC_ID, MediaPurpose.PROFILE_IMAGE, MediaStatus.USED);
	}

	private TeamMember teamMemberWithProfileMedia(Media profileMedia) {
		Member member = activeMemberWithCharacterType(MEMBER_ID, ProfileCharacterType.TAK_LEADER);
		return TeamMember.builder()
				.id(TEAM_MEMBER_ID)
				.team(activeTeam(TEAM_ID))
				.member(member)
				.nickname(ORIGINAL_NICKNAME)
				.profileImgUrl(profileMedia.getUrl())
				.profileMedia(profileMedia)
				.role(TeamMemberRole.MEMBER)
				.status(TeamMemberStatus.ACTIVE)
				.build();
	}

	private void stubTeamMember(TeamMember teamMember) {
		when(teamMemberRepository.findByMemberIdAndTeamId(MEMBER_ID, TEAM_ID)).thenReturn(Optional.of(teamMember));
	}

	private void stubProfileImageResolver(TeamMember teamMember) {
		when(defaultProfileImageResolver.resolveForTeamMember(teamMember))
				.thenAnswer(inv -> ((TeamMember) inv.getArgument(0)).getProfileImgUrl());
	}

	private TeamMemberRequestDTO.TeamMemberUpdateRequestDTO updateRequest(UUID mediaPublicId) {
		return new TeamMemberRequestDTO.TeamMemberUpdateRequestDTO(NEW_NICKNAME, mediaPublicId);
	}

	private void assertProfileUnchanged(TeamMember teamMember, Media oldProfileMedia, String oldProfileImgUrl) {
		assertThat(teamMember.getNickname()).isEqualTo(ORIGINAL_NICKNAME);
		assertThat(teamMember.getProfileMedia()).isSameAs(oldProfileMedia);
		assertThat(teamMember.getProfileImgUrl()).isEqualTo(oldProfileImgUrl);
		assertThat(oldProfileMedia.getStatus()).isEqualTo(MediaStatus.USED);
	}
}
