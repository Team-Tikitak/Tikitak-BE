package kusitms.spin.tikitak.service.team;

import kusitms.spin.tikitak.domain.media.entity.Media;
import kusitms.spin.tikitak.domain.media.enums.MediaPurpose;
import kusitms.spin.tikitak.domain.media.enums.MediaStatus;
import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.member.enums.ProfileCharacterType;
import kusitms.spin.tikitak.domain.team.entity.Team;
import kusitms.spin.tikitak.domain.team.entity.TeamInvite;
import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberRole;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import kusitms.spin.tikitak.repository.media.MediaRepository;
import kusitms.spin.tikitak.repository.member.MemberRepository;
import kusitms.spin.tikitak.repository.team.TeamInviteRepository;
import kusitms.spin.tikitak.repository.team.TeamMemberRepository;
import kusitms.spin.tikitak.repository.team.TeamRepository;
import kusitms.spin.tikitak.service.team.dto.TeamInvitationRequestDTO;
import kusitms.spin.tikitak.service.team.dto.TeamInvitationResponseDTO;
import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static kusitms.spin.tikitak.support.fixture.MediaFixture.media;
import static kusitms.spin.tikitak.support.fixture.MemberFixture.activeMemberWithActiveTeam;
import static kusitms.spin.tikitak.support.fixture.MemberFixture.activeMemberWithActiveTeamAndCharacterType;
import static kusitms.spin.tikitak.support.fixture.TeamFixture.activeTeam;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamInvitationServiceTest extends UnitTest {

	private static final Long MEMBER_ID = 1L;
	private static final Long OTHER_MEMBER_ID = 2L;
	private static final Long TEAM_ID = 10L;
	private static final String TOKEN = "invite-token";
	private static final UUID MEDIA_PUBLIC_ID = UUID.fromString("00000000-0000-0000-0000-000000000191");
	private static final UUID OLD_MEDIA_PUBLIC_ID = UUID.fromString("00000000-0000-0000-0000-000000000190");

	@Mock
	private TeamRepository teamRepository;

	@Mock
	private MemberRepository memberRepository;

	@Mock
	private TeamMemberRepository teamMemberRepository;

	@Mock
	private TeamInviteRepository teamInviteRepository;

	@Mock
	private MediaRepository mediaRepository;

	private TeamInvitationService teamInvitationService;

	@BeforeEach
	void setUp() {
		teamInvitationService = new TeamInvitationService(
				teamRepository,
				memberRepository,
				teamMemberRepository,
				teamInviteRepository,
				new TeamProfileMediaResolver(mediaRepository)
		);
	}

	@Test
	@DisplayName("초대 링크 참여 후 참여한 팀을 활성 팀으로 선택한다")
	void acceptInviteLinkChangesActiveTeamToJoinedTeam() {
		Member member = activeMemberWithActiveTeamAndCharacterType(MEMBER_ID, 99L, ProfileCharacterType.TAK_LEADER);
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

	@Test
	@DisplayName("기본 캐릭터가 없는 멤버가 초대 링크로 참여하면 MEMBER002 예외가 발생한다")
	void acceptInviteLinkThrowsWhenMemberHasNoProfileCharacterType() {
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

		assertThatThrownBy(() -> teamInvitationService.acceptInviteLink(
				MEMBER_ID,
				TOKEN,
				new TeamInvitationRequestDTO.JoinTeamRequestDTO("닉네임", null)
		))
				.isInstanceOf(BusinessException.class)
				.satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.MEMBER002));
	}

	@Test
	@DisplayName("본인 소유 PROFILE_IMAGE UPLOADED media로 초대를 수락하면 USED로 바뀌고 새 팀 멤버에 연결된다")
	void acceptInviteLinkLinksUploadedProfileMedia() {
		Member member = stubAcceptPreconditions();
		Media profileMedia = media(100L, MEMBER_ID, MEDIA_PUBLIC_ID, MediaPurpose.PROFILE_IMAGE, MediaStatus.UPLOADED);
		when(teamMemberRepository.findByMemberIdAndTeamId(MEMBER_ID, TEAM_ID)).thenReturn(Optional.empty());
		when(mediaRepository.findByPublicIdForUpdate(MEDIA_PUBLIC_ID)).thenReturn(Optional.of(profileMedia));
		when(teamMemberRepository.save(any(TeamMember.class))).thenAnswer(inv -> inv.getArgument(0));

		teamInvitationService.acceptInviteLink(MEMBER_ID, TOKEN, joinRequest(MEDIA_PUBLIC_ID));

		assertThat(profileMedia.getStatus()).isEqualTo(MediaStatus.USED);
		ArgumentCaptor<TeamMember> captor = ArgumentCaptor.forClass(TeamMember.class);
		verify(teamMemberRepository).save(captor.capture());
		assertThat(captor.getValue().getProfileImgUrl()).isEqualTo(profileMedia.getUrl());
		assertThat(captor.getValue().getProfileMedia()).isEqualTo(profileMedia);
		assertThat(member.getActiveTeamId()).isEqualTo(TEAM_ID);
	}

	@Test
	@DisplayName("DELETED media로 초대를 수락하면 MEDIA018 예외가 발생하고 media 상태가 바뀌지 않는다")
	void acceptInviteLinkRejectsDeletedMedia() {
		assertRejectedForNewMember(
				media(100L, MEMBER_ID, MEDIA_PUBLIC_ID, MediaPurpose.PROFILE_IMAGE, MediaStatus.DELETED),
				MediaStatus.DELETED
		);
	}

	@Test
	@DisplayName("PENDING media로 초대를 수락하면 MEDIA018 예외가 발생하고 media 상태가 바뀌지 않는다")
	void acceptInviteLinkRejectsPendingMedia() {
		assertRejectedForNewMember(
				media(100L, MEMBER_ID, MEDIA_PUBLIC_ID, MediaPurpose.PROFILE_IMAGE, MediaStatus.PENDING),
				MediaStatus.PENDING
		);
	}

	@Test
	@DisplayName("타인 소유 media로 초대를 수락하면 MEDIA018 예외가 발생하고 media 상태가 바뀌지 않는다")
	void acceptInviteLinkRejectsOtherMembersMedia() {
		assertRejectedForNewMember(
				media(100L, OTHER_MEMBER_ID, MEDIA_PUBLIC_ID, MediaPurpose.PROFILE_IMAGE, MediaStatus.UPLOADED),
				MediaStatus.UPLOADED
		);
	}

	@Test
	@DisplayName("purpose가 PROFILE_IMAGE가 아닌 media로 초대를 수락하면 MEDIA018 예외가 발생하고 media 상태가 바뀌지 않는다")
	void acceptInviteLinkRejectsNonProfileImagePurpose() {
		assertRejectedForNewMember(
				media(100L, MEMBER_ID, MEDIA_PUBLIC_ID, MediaPurpose.FEED_IMAGE, MediaStatus.UPLOADED),
				MediaStatus.UPLOADED
		);
	}

	@Test
	@DisplayName("재가입 시 현재 연결된 USED 프로필 media를 다시 보내면 MEDIA018 예외가 발생하고 기존 media가 DELETED로 바뀌지 않는다")
	void acceptInviteLinkRejectsCurrentUsedProfileMediaOnRejoin() {
		Member member = stubAcceptPreconditions();
		Media currentProfileMedia = media(100L, MEMBER_ID, MEDIA_PUBLIC_ID, MediaPurpose.PROFILE_IMAGE, MediaStatus.USED);
		TeamMember leftMember = existingTeamMember(member, TeamMemberStatus.LEFT, currentProfileMedia);
		when(teamMemberRepository.findByMemberIdAndTeamId(MEMBER_ID, TEAM_ID)).thenReturn(Optional.of(leftMember));
		when(mediaRepository.findByPublicIdForUpdate(MEDIA_PUBLIC_ID)).thenReturn(Optional.of(currentProfileMedia));

		assertThatThrownBy(() -> teamInvitationService.acceptInviteLink(MEMBER_ID, TOKEN, joinRequest(MEDIA_PUBLIC_ID)))
				.isInstanceOf(BusinessException.class)
				.satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.MEDIA018));

		assertThat(currentProfileMedia.getStatus()).isEqualTo(MediaStatus.USED);
		assertThat(leftMember.getStatus()).isEqualTo(TeamMemberStatus.LEFT);
		assertThat(leftMember.getProfileMedia()).isSameAs(currentProfileMedia);
		verify(teamMemberRepository, never()).save(any(TeamMember.class));
	}

	@Test
	@DisplayName("재가입 시 본인 소유 PROFILE_IMAGE UPLOADED 새 media로 초대를 수락하면 기존 media는 DELETED, 새 media는 USED가 되고 새 media로 연결된다")
	void acceptInviteLinkReplacesProfileMediaOnRejoin() {
		Member member = stubAcceptPreconditions();
		Media oldProfileMedia = media(100L, MEMBER_ID, OLD_MEDIA_PUBLIC_ID, MediaPurpose.PROFILE_IMAGE, MediaStatus.USED);
		Media newProfileMedia = Media.builder()
				.id(101L)
				.publicId(MEDIA_PUBLIC_ID)
				.memberId(MEMBER_ID)
				.purpose(MediaPurpose.PROFILE_IMAGE)
				.status(MediaStatus.UPLOADED)
				.fileName("new-profile.png")
				.contentType("image/png")
				.size(1024L)
				.url("https://example.com/new-profile.png")
				.key("media/profile-image/" + MEDIA_PUBLIC_ID + ".png")
				.build();
		TeamMember leftMember = existingTeamMember(member, TeamMemberStatus.LEFT, oldProfileMedia);
		when(teamMemberRepository.findByMemberIdAndTeamId(MEMBER_ID, TEAM_ID)).thenReturn(Optional.of(leftMember));
		when(mediaRepository.findByPublicIdForUpdate(MEDIA_PUBLIC_ID)).thenReturn(Optional.of(newProfileMedia));

		teamInvitationService.acceptInviteLink(MEMBER_ID, TOKEN, joinRequest(MEDIA_PUBLIC_ID));

		assertThat(oldProfileMedia.getStatus()).isEqualTo(MediaStatus.DELETED);
		assertThat(newProfileMedia.getStatus()).isEqualTo(MediaStatus.USED);
		assertThat(leftMember.getStatus()).isEqualTo(TeamMemberStatus.ACTIVE);
		assertThat(leftMember.getProfileMedia()).isSameAs(newProfileMedia);
		assertThat(leftMember.getProfileImgUrl()).isEqualTo("https://example.com/new-profile.png");
		assertThat(member.getActiveTeamId()).isEqualTo(TEAM_ID);
		verify(teamMemberRepository, never()).save(any(TeamMember.class));
	}

	@Test
	@DisplayName("이미 ACTIVE인 멤버가 media와 함께 초대를 수락하면 media 조회 없이 INVITE006 예외가 발생한다")
	void acceptInviteLinkChecksActiveMemberBeforeMedia() {
		Member member = stubAcceptPreconditions();
		TeamMember activeMember = existingTeamMember(member, TeamMemberStatus.ACTIVE, null);
		when(teamMemberRepository.findByMemberIdAndTeamId(MEMBER_ID, TEAM_ID)).thenReturn(Optional.of(activeMember));

		assertThatThrownBy(() -> teamInvitationService.acceptInviteLink(MEMBER_ID, TOKEN, joinRequest(MEDIA_PUBLIC_ID)))
				.isInstanceOf(BusinessException.class)
				.satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.INVITE006));

		verify(mediaRepository, never()).findByPublicIdForUpdate(any());
		verify(teamMemberRepository, never()).save(any(TeamMember.class));
	}

	@Test
	@DisplayName("BANNED 멤버가 media와 함께 초대를 수락하면 media 조회 없이 INVITE007 예외가 발생한다")
	void acceptInviteLinkChecksBannedMemberBeforeMedia() {
		Member member = stubAcceptPreconditions();
		TeamMember bannedMember = existingTeamMember(member, TeamMemberStatus.BANNED, null);
		when(teamMemberRepository.findByMemberIdAndTeamId(MEMBER_ID, TEAM_ID)).thenReturn(Optional.of(bannedMember));

		assertThatThrownBy(() -> teamInvitationService.acceptInviteLink(MEMBER_ID, TOKEN, joinRequest(MEDIA_PUBLIC_ID)))
				.isInstanceOf(BusinessException.class)
				.satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.INVITE007));

		verify(mediaRepository, never()).findByPublicIdForUpdate(any());
		verify(teamMemberRepository, never()).save(any(TeamMember.class));
	}

	private void assertRejectedForNewMember(Media requestedMedia, MediaStatus expectedStatus) {
		stubAcceptPreconditions();
		when(teamMemberRepository.findByMemberIdAndTeamId(MEMBER_ID, TEAM_ID)).thenReturn(Optional.empty());
		when(mediaRepository.findByPublicIdForUpdate(MEDIA_PUBLIC_ID)).thenReturn(Optional.of(requestedMedia));

		assertThatThrownBy(() -> teamInvitationService.acceptInviteLink(MEMBER_ID, TOKEN, joinRequest(MEDIA_PUBLIC_ID)))
				.isInstanceOf(BusinessException.class)
				.satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.MEDIA018));

		assertThat(requestedMedia.getStatus()).isEqualTo(expectedStatus);
		verify(teamMemberRepository, never()).save(any(TeamMember.class));
	}

	private Member stubAcceptPreconditions() {
		Member member = activeMemberWithActiveTeamAndCharacterType(MEMBER_ID, 99L, ProfileCharacterType.TAK_LEADER);
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
		return member;
	}

	private TeamMember existingTeamMember(Member member, TeamMemberStatus status, Media profileMedia) {
		return TeamMember.builder()
				.team(activeTeam(TEAM_ID))
				.member(member)
				.nickname("기존닉네임")
				.profileImgUrl(profileMedia != null ? profileMedia.getUrl() : null)
				.profileMedia(profileMedia)
				.role(TeamMemberRole.MEMBER)
				.status(status)
				.build();
	}

	private TeamInvitationRequestDTO.JoinTeamRequestDTO joinRequest(UUID mediaPublicId) {
		return new TeamInvitationRequestDTO.JoinTeamRequestDTO("닉네임", mediaPublicId);
	}
}
