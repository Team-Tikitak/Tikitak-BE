package kusitms.spin.tikitak.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static kusitms.spin.tikitak.architecture.ArchitectureClasses.PRODUCTION_CLASSES;

/**
 * CLAUDE.md "Architecture": 최상위는 계층별(controller/service/repository/domain), 그 아래는 기능별 구조다.
 * 계층 간 의존 방향을 강제한다. 기존 위반은 규칙별 예외 목록에 이유와 함께 명시한다.
 */
class LayerDependencyArchTest {

	@Test
	@DisplayName("L1: controller는 repository와 domain entity에 의존하지 않는다")
	void l1_controllerDoesNotDependOnRepositoryOrEntity() {
		noClasses()
				.that().resideInAPackage("..controller..")
				.should().dependOnClassesThat().resideInAnyPackage("..repository..", "..domain..entity..")
				.because("CLAUDE.md Architecture: controller는 service를 통해서만 데이터에 접근하고, 엔티티 대신 DTO로 응답한다")
				.check(PRODUCTION_CLASSES);
	}

	@Test
	@DisplayName("L2: service는 controller에 의존하지 않는다")
	void l2_serviceDoesNotDependOnController() {
		noClasses()
				.that().resideInAPackage("..service..")
				.should().dependOnClassesThat().resideInAPackage("..controller..")
				.because("CLAUDE.md Architecture: 계층 의존은 controller -> service 한 방향이다")
				.check(PRODUCTION_CLASSES);
	}

	@Test
	@DisplayName("L3: domain은 service, controller, repository에 의존하지 않는다")
	void l3_domainDoesNotDependOnUpperLayers() {
		noClasses()
				.that().resideInAPackage("..domain..")
				.should().dependOnClassesThat().resideInAnyPackage("..service..", "..controller..", "..repository..")
				.because("CLAUDE.md Architecture: domain(entity/enums)은 가장 안쪽 계층이라 다른 계층을 알지 않는다")
				.check(PRODUCTION_CLASSES);
	}

	@Test
	@DisplayName("L4: repository는 service, controller에 의존하지 않는다")
	void l4_repositoryDoesNotDependOnServiceOrController() {
		noClasses()
				.that().resideInAPackage("..repository..")
				.should().dependOnClassesThat().resideInAnyPackage("..service..", "..controller..")
				.because("CLAUDE.md Architecture: 계층 의존은 service -> repository 한 방향이다")
				.check(PRODUCTION_CLASSES);
	}

	@Test
	@DisplayName("L5: domain 기능 slice 사이에 순환 의존이 없다")
	void l5_domainSlicesAreFreeOfCycles() {
		slices()
				.matching("kusitms.spin.tikitak.domain.(*)..")
				.should().beFreeOfCycles()
				.because("CLAUDE.md Architecture: 기능별 하위 패키지는 하나씩 따로 이해할 수 있어야 한다")
				.check(PRODUCTION_CLASSES);
	}
}
