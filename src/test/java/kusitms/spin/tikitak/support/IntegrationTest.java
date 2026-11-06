package kusitms.spin.tikitak.support;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Table;
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
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.LocalDateTime;

@Tag("integration")
@DataJpaTest
@ActiveProfiles("integration-test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class IntegrationTest {

	private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("postgres:16-alpine");

	protected static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 3, 4, 20, 30);

	static {
		POSTGRESQL.start();
	}

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
		String token = token(suffix);
		return Member.builder()
				.email("user" + suffix + "@example.com")
				.name("User " + token)
				.nickname("user" + token)
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
				.nickname("tm" + token(member.getProviderId()))
				.profileImgUrl("https://example.com/team-member.png")
				.role(role)
				.status(status)
				.createdAt(BASE_TIME)
				.lastActivityCheckedAt(BASE_TIME)
				.build();
	}

	protected <T> T persist(T entity) {
		entityManager.persist(entity);
		return entity;
	}

	/**
	 * 엔티티를 persist한 뒤 created_at을 주어진 시각으로 바꾼다. {@link #overrideCreatedAt} 참고.
	 */
	protected <T> T persistWithCreatedAt(T entity, LocalDateTime createdAt) {
		return overrideCreatedAt(persist(entity), createdAt);
	}

	/**
	 * 이미 persist된 엔티티의 created_at을 DB(native update)와 영속성 컨텍스트 양쪽에서 주어진 시각으로 맞춘다.
	 * auditing(@CreatedDate)이 persist 시각으로 덮어써도 테스트가 의도한 시각을 쓰게 하기 위한 헬퍼다.
	 * 엔티티 클래스의 {@code @Table(name)}과 {@code id} 컬럼을 사용한다.
	 */
	protected <T> T overrideCreatedAt(T entity, LocalDateTime createdAt) {
		entityManager.flush();
		Object id = entityManager.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(entity);
		int updated = entityManager.createNativeQuery(
				"UPDATE " + tableName(entity) + " SET created_at = :createdAt WHERE id = :id")
				.setParameter("createdAt", createdAt)
				.setParameter("id", id)
				.executeUpdate();
		if (updated != 1) {
			throw new IllegalStateException("created_at 갱신 대상이 1건이 아님: " + updated);
		}
		// DB 값을 다시 읽어 엔티티 필드와 스냅샷을 함께 맞춘다 (getCreatedAt()을 커서로 쓰는 테스트용).
		entityManager.refresh(entity);
		return entity;
	}

	protected void flushAndClear() {
		entityManager.flush();
		entityManager.clear();
	}

	protected EntityManager entityManager() {
		return entityManager;
	}

	private String tableName(Object entity) {
		Table table = entity.getClass().getAnnotation(Table.class);
		if (table == null || table.name().isBlank()) {
			throw new IllegalArgumentException("@Table(name)이 없는 엔티티: " + entity.getClass().getName());
		}
		return table.name();
	}

	private String token(String value) {
		return Integer.toUnsignedString(value.hashCode(), 36);
	}
}
