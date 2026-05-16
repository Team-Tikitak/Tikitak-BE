package kusitms.spin.tikitak.support;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.member.enums.MemberStatus;
import kusitms.spin.tikitak.domain.member.enums.SocialProvider;
import kusitms.spin.tikitak.domain.team.entity.Team;
import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberRole;
import kusitms.spin.tikitak.domain.team.enums.TeamMemberStatus;
import kusitms.spin.tikitak.domain.team.enums.TeamStatus;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.LocalDateTime;

@Tag("integration")
@Testcontainers
@DataJpaTest
@ActiveProfiles("integration-test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class IntegrationTest {

	@Container
	private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("postgres:16-alpine");

	protected static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 3, 4, 20, 30);

	@PersistenceContext
	private EntityManager entityManager;

	@DynamicPropertySource
	static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRESQL::getUsername);
		registry.add("spring.datasource.password", POSTGRESQL::getPassword);
		registry.add("spring.datasource.driver-class-name", POSTGRESQL::getDriverClassName);
	}

	protected Member member(String suffix) {
		return member(suffix, MemberStatus.ACTIVE, null);
	}

	protected Member member(String suffix, MemberStatus status, Long activeTeamId) {
		return Member.builder()
				.email("user" + suffix + "@example.com")
				.name("User " + suffix)
				.nickname("user" + suffix)
				.profileImgUrl("https://example.com/profile" + suffix + ".png")
				.socialProvider(SocialProvider.KAKAO)
				.providerId("provider-" + suffix)
				.status(status)
				.termsAgreed(false)
				.privacyAgreed(false)
				.activeTeamId(activeTeamId)
				.createdAt(BASE_TIME)
				.updatedAt(BASE_TIME)
				.build();
	}

	protected Team team(String suffix) {
		return team(suffix, TeamStatus.ACTIVE, null);
	}

	protected Team team(String suffix, TeamStatus status, LocalDateTime deletedAt) {
		return Team.builder()
				.name("Team " + suffix)
				.description("Team description " + suffix)
				.teamImgUrl("https://example.com/team" + suffix + ".png")
				.status(status)
				.createdAt(BASE_TIME)
				.updatedAt(BASE_TIME)
				.deletedAt(deletedAt)
				.build();
	}

	protected TeamMember teamMember(Member member, Team team, TeamMemberRole role, TeamMemberStatus status) {
		return TeamMember.builder()
				.team(team)
				.member(member)
				.nickname("nickname-" + member.getProviderId())
				.profileImgUrl("https://example.com/team-member.png")
				.role(role)
				.status(status)
				.createdAt(BASE_TIME)
				.build();
	}

	protected <T> T persist(T entity) {
		entityManager.persist(entity);
		return entity;
	}

	protected void flushAndClear() {
		entityManager.flush();
		entityManager.clear();
	}

	protected EntityManager entityManager() {
		return entityManager;
	}
}
