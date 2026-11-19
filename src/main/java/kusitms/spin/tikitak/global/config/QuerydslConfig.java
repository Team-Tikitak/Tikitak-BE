package kusitms.spin.tikitak.global.config;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 프로젝트 공용 {@link JPAQueryFactory}. 리포지토리 Impl은 이 Bean을 생성자로 주입받고 직접 생성하지 않는다.
 * 템플릿 설정이 필요해지면 이 Bean 한 곳에서만 한다. docs/decisions/2026-11-19-querydsl-feed-list.md
 */
@Configuration
public class QuerydslConfig {

	/** 트랜잭션마다 실제 EntityManager로 위임하는 공유 프록시. */
	@PersistenceContext
	private EntityManager entityManager;

	@Bean
	public JPAQueryFactory jpaQueryFactory() {
		return new JPAQueryFactory(entityManager);
	}
}
