package kusitms.spin.tikitak.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import java.util.Set;

/**
 * 아키텍처 테스트가 공유하는 프로덕션 클래스 import.
 * 테스트 클래스(support, fake, fixture 등)는 검사 대상이 아니므로 제외한다.
 */
final class ArchitectureClasses {

	static final String ROOT_PACKAGE = "kusitms.spin.tikitak";

	static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
			.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
			.importPackages(ROOT_PACKAGE);

	private ArchitectureClasses() {
	}

	/**
	 * 예외 목록(최상위 클래스의 FQCN)에 들어 있지 않은 클래스. Lombok builder 같은 중첩 클래스는 바깥 클래스를 따른다.
	 */
	static DescribedPredicate<JavaClass> notInExceptions(Set<String> exceptions) {
		return DescribedPredicate.describe(
				"are not in explicit exception list " + exceptions,
				javaClass -> !exceptions.contains(topLevelName(javaClass)));
	}

	private static String topLevelName(JavaClass javaClass) {
		String name = javaClass.getName();
		int nestedIndex = name.indexOf('$');
		return nestedIndex < 0 ? name : name.substring(0, nestedIndex);
	}
}
