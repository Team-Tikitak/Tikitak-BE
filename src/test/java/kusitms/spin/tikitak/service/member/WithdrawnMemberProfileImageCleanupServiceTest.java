package kusitms.spin.tikitak.service.member;

import kusitms.spin.tikitak.domain.media.entity.ObjectDeleteOutbox;
import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.member.enums.MemberStatus;
import kusitms.spin.tikitak.domain.member.enums.SocialProvider;
import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import kusitms.spin.tikitak.global.config.R2Properties;
import kusitms.spin.tikitak.repository.member.MemberRepository;
import kusitms.spin.tikitak.repository.team.TeamMemberRepository;
import kusitms.spin.tikitak.service.media.ObjectDeleteOutboxService;
import kusitms.spin.tikitak.support.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static kusitms.spin.tikitak.support.fixture.TeamFixture.activeTeam;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WithdrawnMemberProfileImageCleanupServiceTest extends UnitTest {

	@Mock
	private MemberRepository memberRepository;

	@Mock
	private TeamMemberRepository teamMemberRepository;

	@Mock
	private ObjectDeleteOutboxService objectDeleteOutboxService;

	private WithdrawnMemberProfileImageCleanupService cleanupService;

	@BeforeEach
	void setUp() {
		R2Properties r2Properties = new R2Properties();
		r2Properties.setBucketName("tikitak-dev-media");
		r2Properties.setEndpoint("https://example.r2.cloudflarestorage.com");
		r2Properties.setPublicBaseUrl("https://cdn.tikitak.com");
		cleanupService = new WithdrawnMemberProfileImageCleanupService(
				memberRepository,
				teamMemberRepository,
				objectDeleteOutboxService,
				new ProfileImageObjectKeyResolver(r2Properties),
				r2Properties
		);
	}

	@Test
	@DisplayName("탈퇴 회원과 팀 멤버 프로필 이미지를 null 처리하고 우리 R2 URL만 삭제 outbox에 등록한다")
	void cleanUpProfileImagesClearsDbAndRegistersR2Objects() {
		Member member = Member.builder()
				.id(1L)
				.email("user@example.com")
				.name("User")
				.nickname("user")
				.profileImgUrl("https://cdn.tikitak.com/media/profile/member.png")
				.socialProvider(SocialProvider.KAKAO)
				.providerId("WITHDRAWN:1:uuid")
				.status(MemberStatus.INACTIVE)
				.termsAgreed(false)
				.privacyAgreed(false)
				.createdAt(LocalDateTime.now())
				.updatedAt(LocalDateTime.now())
				.deletedAt(LocalDateTime.now().minusDays(8))
				.build();
		TeamMember r2TeamMember = TeamMember.builder()
				.id(10L)
				.team(activeTeam(100L))
				.member(member)
				.nickname("r2")
				.profileImgUrl("https://cdn.tikitak.com/media/profile/team-member.png")
				.role(kusitms.spin.tikitak.domain.team.enums.TeamMemberRole.MEMBER)
				.status(kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus.ACTIVE)
				.createdAt(LocalDateTime.now())
				.build();
		TeamMember externalTeamMember = TeamMember.builder()
				.id(11L)
				.team(activeTeam(100L))
				.member(member)
				.nickname("external")
				.profileImgUrl("https://k.kakaocdn.net/profile/external.png")
				.role(kusitms.spin.tikitak.domain.team.enums.TeamMemberRole.MEMBER)
				.status(kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus.ACTIVE)
				.createdAt(LocalDateTime.now())
				.build();
		when(memberRepository.findProfileCleanupTargetForUpdate(1L)).thenReturn(Optional.of(member));
		when(teamMemberRepository.findProfileImageTargetsByMemberId(1L))
				.thenReturn(List.of(r2TeamMember, externalTeamMember));

		boolean cleaned = cleanupService.cleanUpProfileImages(1L, LocalDateTime.now().minusDays(7));

		assertThat(cleaned).isTrue();
		assertThat(member.getProfileImgUrl()).isNull();
		assertThat(r2TeamMember.getProfileImgUrl()).isNull();
		assertThat(externalTeamMember.getProfileImgUrl()).isNull();

		verify(objectDeleteOutboxService).saveAll(argThat(deleteRequests ->
				deleteRequests.stream()
						.map(ObjectDeleteOutbox::getObjectKey)
						.toList()
						.equals(List.of("media/profile/member.png", "media/profile/team-member.png"))
		));
	}

	@Test
	@DisplayName("탈퇴 후 7일이 지나지 않은 회원은 프로필 이미지를 정리하지 않는다")
	void cleanUpProfileImagesSkipsRecentWithdrawnMember() {
		Member member = Member.builder()
				.id(1L)
				.profileImgUrl("https://cdn.tikitak.com/media/profile/member.png")
				.socialProvider(SocialProvider.KAKAO)
				.providerId("WITHDRAWN:1:uuid")
				.status(MemberStatus.INACTIVE)
				.termsAgreed(false)
				.privacyAgreed(false)
				.createdAt(LocalDateTime.now())
				.updatedAt(LocalDateTime.now())
				.deletedAt(LocalDateTime.now().minusDays(1))
				.build();
		when(memberRepository.findProfileCleanupTargetForUpdate(1L)).thenReturn(Optional.of(member));

		boolean cleaned = cleanupService.cleanUpProfileImages(1L, LocalDateTime.now().minusDays(7));

		assertThat(cleaned).isFalse();
		assertThat(member.getProfileImgUrl()).isEqualTo("https://cdn.tikitak.com/media/profile/member.png");
	}
}
