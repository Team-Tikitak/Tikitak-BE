package kusitms.spin.tikitak.repository.member;

import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.member.enums.MemberStatus;
import kusitms.spin.tikitak.domain.member.enums.SocialProvider;
import kusitms.spin.tikitak.domain.team.entity.Team;
import kusitms.spin.tikitak.domain.team.enums.TeamStatus;
import kusitms.spin.tikitak.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberRepositoryIntegrationTest extends IntegrationTest {

	@Autowired
	private MemberRepository memberRepository;

	@Test
	@DisplayName("sets activeTeamId only when current activeTeamId is null")
	void setActiveTeamIdIfNullUpdatesOnlyNullActiveTeamId() {
		Team team = persist(team("active"));
		Member memberWithoutActiveTeam = persist(member("without-active-team"));
		Member memberWithActiveTeam = persist(member("with-active-team", MemberStatus.ACTIVE, team.getId()));
		flushAndClear();

		int updated = memberRepository.setActiveTeamIdIfNull(memberWithoutActiveTeam.getId(), team.getId());
		int skipped = memberRepository.setActiveTeamIdIfNull(memberWithActiveTeam.getId(), team.getId() + 100);
		flushAndClear();

		assertThat(updated).isEqualTo(1);
		assertThat(skipped).isZero();
		assertThat(memberRepository.findById(memberWithoutActiveTeam.getId())).get()
				.extracting(Member::getActiveTeamId)
				.isEqualTo(team.getId());
		assertThat(memberRepository.findById(memberWithActiveTeam.getId())).get()
				.extracting(Member::getActiveTeamId)
				.isEqualTo(team.getId());
	}

	@Test
	@DisplayName("clears activeTeamId by team id")
	void clearActiveTeamIdByTeamIdClearsMatchingMembers() {
		Team targetTeam = persist(team("target"));
		Team otherTeam = persist(team("other"));
		Member targetMember = persist(member("target-member", MemberStatus.ACTIVE, targetTeam.getId()));
		Member otherMember = persist(member("other-member", MemberStatus.ACTIVE, otherTeam.getId()));
		flushAndClear();

		memberRepository.clearActiveTeamIdByTeamId(targetTeam.getId());
		flushAndClear();

		assertThat(memberRepository.findById(targetMember.getId())).get()
				.extracting(Member::getActiveTeamId)
				.isNull();
		assertThat(memberRepository.findById(otherMember.getId())).get()
				.extracting(Member::getActiveTeamId)
				.isEqualTo(otherTeam.getId());
	}

	@Test
	@DisplayName("clears activeTeamId for inactive teams older than cutoff")
	void clearActiveTeamIdsByInactiveTeamPredicateClearsOnlyMatchingTeams() {
		Team oldInactiveTeam = persist(team("old-inactive", TeamStatus.INACTIVE, BASE_TIME.minusDays(10)));
		Team recentInactiveTeam = persist(team("recent-inactive", TeamStatus.INACTIVE, BASE_TIME.minusDays(1)));
		Team activeTeam = persist(team("active", TeamStatus.ACTIVE, null));
		Member oldInactiveMember = persist(member("old-inactive-member", MemberStatus.ACTIVE, oldInactiveTeam.getId()));
		Member recentInactiveMember = persist(member("recent-inactive-member", MemberStatus.ACTIVE, recentInactiveTeam.getId()));
		Member activeMember = persist(member("active-member", MemberStatus.ACTIVE, activeTeam.getId()));
		flushAndClear();

		memberRepository.clearActiveTeamIdsByInactiveTeamPredicate(TeamStatus.INACTIVE, BASE_TIME.minusDays(3));
		flushAndClear();

		assertThat(memberRepository.findById(oldInactiveMember.getId())).get()
				.extracting(Member::getActiveTeamId)
				.isNull();
		assertThat(memberRepository.findById(recentInactiveMember.getId())).get()
				.extracting(Member::getActiveTeamId)
				.isEqualTo(recentInactiveTeam.getId());
		assertThat(memberRepository.findById(activeMember.getId())).get()
				.extracting(Member::getActiveTeamId)
				.isEqualTo(activeTeam.getId());
	}

	@Test
	@DisplayName("PostgreSQL foreign key sets activeTeamId to null when team is deleted")
	void activeTeamIdForeignKeySetsNullOnTeamDelete() {
		Team team = persist(team("fk-target"));
		Member member = persist(member("fk-member", MemberStatus.ACTIVE, team.getId()));
		flushAndClear();

		entityManager().createNativeQuery("delete from team where id = :teamId")
				.setParameter("teamId", team.getId())
				.executeUpdate();
		flushAndClear();

		assertThat(memberRepository.findById(member.getId())).get()
				.extracting(Member::getActiveTeamId)
				.isNull();
	}

	@Test
	@DisplayName("ACTIVE members with the same social provider and provider id violate the unique index")
	void activeMembersWithSameSocialAccountViolateUniqueIndex() {
		memberRepository.saveAndFlush(member("duplicate-social"));

		assertThatThrownBy(() -> memberRepository.saveAndFlush(member("duplicate-social")))
				.isInstanceOf(DataIntegrityViolationException.class)
				.satisfies(exception -> assertThat(messageChain(exception))
						.anyMatch(message -> message.contains("uk_member_social_provider_active")));
	}

	@Test
	@DisplayName("INACTIVE and ACTIVE members with the same social account can coexist")
	void inactiveAndActiveMembersWithSameSocialAccountAreSaved() {
		Member inactiveMember = memberRepository.saveAndFlush(member("rejoin-social", MemberStatus.INACTIVE, null));
		Member activeMember = memberRepository.saveAndFlush(member("rejoin-social"));
		flushAndClear();

		assertThat(memberRepository.findById(inactiveMember.getId())).get()
				.extracting(Member::getStatus, Member::getSocialProvider, Member::getProviderId)
				.containsExactly(MemberStatus.INACTIVE, SocialProvider.KAKAO, "provider-rejoin-social");
		assertThat(memberRepository.findById(activeMember.getId())).get()
				.extracting(Member::getStatus, Member::getSocialProvider, Member::getProviderId)
				.containsExactly(MemberStatus.ACTIVE, SocialProvider.KAKAO, "provider-rejoin-social");
	}

	@Test
	@DisplayName("ACTIVE members with the same provider id but different social providers are saved")
	void activeMembersWithDifferentSocialProvidersAreSaved() {
		Member kakaoMember = memberRepository.saveAndFlush(member("shared-provider-id"));
		Member googleMember = memberRepository.saveAndFlush(googleMember("shared-provider-id"));
		flushAndClear();

		assertThat(memberRepository.findById(kakaoMember.getId())).get()
				.extracting(Member::getStatus, Member::getSocialProvider, Member::getProviderId)
				.containsExactly(MemberStatus.ACTIVE, SocialProvider.KAKAO, "provider-shared-provider-id");
		assertThat(memberRepository.findById(googleMember.getId())).get()
				.extracting(Member::getStatus, Member::getSocialProvider, Member::getProviderId)
				.containsExactly(MemberStatus.ACTIVE, SocialProvider.GOOGLE, "provider-shared-provider-id");
	}

	private Member googleMember(String suffix) {
		return Member.builder()
				.email("google" + suffix + "@example.com")
				.name("Google User")
				.nickname("googleuser")
				.profileImgUrl("https://example.com/profile" + suffix + ".png")
				.socialProvider(SocialProvider.GOOGLE)
				.providerId("provider-" + suffix)
				.status(MemberStatus.ACTIVE)
				.termsAgreed(false)
				.privacyAgreed(false)
				.createdAt(BASE_TIME)
				.updatedAt(BASE_TIME)
				.build();
	}

	private List<String> messageChain(Throwable exception) {
		List<String> messages = new ArrayList<>();
		Throwable current = exception;
		while (current != null) {
			if (current.getMessage() != null) {
				messages.add(current.getMessage());
			}
			current = current.getCause();
		}
		return messages;
	}

}
