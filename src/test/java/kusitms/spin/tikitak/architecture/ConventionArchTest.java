package kusitms.spin.tikitak.architecture;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.domain.properties.CanBeAnnotated;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.CompositeArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import kusitms.spin.tikitak.global.dto.CommonResponse;
import kusitms.spin.tikitak.global.dto.PatchField;
import kusitms.spin.tikitak.global.jackson.PatchFieldDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
import static kusitms.spin.tikitak.architecture.ArchitectureClasses.PRODUCTION_CLASSES;
import static kusitms.spin.tikitak.architecture.ArchitectureClasses.notInExceptions;

/**
 * CLAUDE.md "Architecture"/"Conventions"의 코드 컨벤션 중 기계적으로 판정할 수 있는 것을 강제한다.
 * 기존 위반은 규칙별 예외 목록에 이유와 함께 명시한다.
 */
class ConventionArchTest {

	private static final String SPRING_TRANSACTIONAL = "org.springframework.transaction.annotation.Transactional";
	private static final String JAKARTA_TRANSACTIONAL = "jakarta.transaction.Transactional";

	/** C7 예외 목록. "클래스 FQCN#메서드 이름" 형식. */
	private static final Set<String> C7_RESPONSE_TYPE_EXCEPTIONS = Set.of(
			// OAuth 시작: 302 리다이렉트 또는 authorize URL 응답이라 ResponseEntity<?>를 반환. 후속: 리다이렉트 응답 규칙을 따로 정한다
			"kusitms.spin.tikitak.controller.auth.AuthController#startOAuthLogin",
			// OAuth 콜백: 302 리다이렉트라 본문 없음(ResponseEntity<Void>). 후속: 위와 같음
			"kusitms.spin.tikitak.controller.auth.AuthController#handleOAuthCallback",
			// Apple OAuth 콜백(form_post): 302 리다이렉트라 본문 없음(ResponseEntity<Void>). 후속: 위와 같음
			"kusitms.spin.tikitak.controller.auth.AuthController#handleAppleOAuthCallback"
	);

	/** C9 예외 목록. */
	private static final Set<String> C9_RAW_EXCEPTION_EXCEPTIONS = Set.of(
			// JSON 직렬화/서명/해시 실패(인프라 오류)를 IllegalStateException으로 감쌈. 후속: ErrorCode 추가 후 BusinessException으로 전환
			"kusitms.spin.tikitak.service.auth.TokenService",
			// 로그인 코드 payload 직렬화/역직렬화 실패(인프라 오류). 후속: 위와 같음
			"kusitms.spin.tikitak.service.auth.RedisLoginCodeStore",
			// 이미지 최적화 설정 누락(기동 시 설정 오류). 후속: *Properties 검증(@Validated)으로 옮긴다
			"kusitms.spin.tikitak.service.media.ImageUrlResolver"
	);

	private static final List<Class<? extends RuntimeException>> RAW_EXCEPTION_TYPES = List.of(
			IllegalArgumentException.class,
			IllegalStateException.class,
			RuntimeException.class,
			UnsupportedOperationException.class
	);

	@Test
	@DisplayName("C1: @RestController는 controller 패키지에 있고 이름이 Controller로 끝난다")
	void c1_restControllersResideInControllerPackageWithControllerSuffix() {
		classes()
				.that().areAnnotatedWith(RestController.class)
				.should().resideInAPackage("..controller..")
				.andShould().haveSimpleNameEndingWith("Controller")
				.because("CLAUDE.md Architecture: 컨트롤러는 controller/<feature>에 둔다")
				.check(PRODUCTION_CLASSES);
	}

	@Test
	@DisplayName("C2: @Transactional은 service 패키지에서만 쓴다")
	void c2_transactionalOnlyInService() {
		DescribedPredicate<CanBeAnnotated> transactional =
				annotatedWith(SPRING_TRANSACTIONAL).or(annotatedWith(JAKARTA_TRANSACTIONAL));

		CompositeArchRule
				.of(classes()
						.that(transactional)
						.should().resideInAPackage("..service.."))
				.and(methods()
						.that(transactional)
						.should().beDeclaredInClassesThat().resideInAPackage("..service.."))
				.because("CLAUDE.md Architecture: 트랜잭션 경계는 service 계층이 정한다")
				.check(PRODUCTION_CLASSES);
	}

	@Test
	@DisplayName("C3: @ConfigurationProperties는 global.config 패키지에만 있다")
	void c3_configurationPropertiesOnlyInGlobalConfig() {
		classes()
				.that().areAnnotatedWith(ConfigurationProperties.class)
				.should().resideInAPackage("..global.config..")
				.because("CLAUDE.md Conventions: 설정값은 global/config의 *Properties 클래스로 바인딩한다")
				.check(PRODUCTION_CLASSES);
	}

	@Test
	@DisplayName("C4: @Entity는 domain.<feature>.entity 패키지에만 있다")
	void c4_entitiesResideInDomainEntityPackage() {
		classes()
				.that().areAnnotatedWith(Entity.class)
				.should().resideInAPackage("..domain.*.entity..")
				.because("CLAUDE.md Architecture: 엔티티는 domain/<feature>/entity에 둔다")
				.check(PRODUCTION_CLASSES);
	}

	@Test
	@DisplayName("C5: domain.<feature>.entity 패키지의 최상위 클래스는 @Entity다")
	void c5_topLevelClassesInEntityPackageAreEntities() {
		classes()
				.that().resideInAPackage("..domain.*.entity..")
				.and().areTopLevelClasses()
				.should().beAnnotatedWith(Entity.class)
				.because("CLAUDE.md Architecture: domain/<feature>/entity에는 엔티티만 두고 enum 등은 enums에 둔다")
				.check(PRODUCTION_CLASSES);
	}

	@Test
	@DisplayName("C6: PatchField 필드에는 @JsonDeserialize(using = PatchFieldDeserializer.class)가 있다")
	void c6_patchFieldsUsePatchFieldDeserializer() {
		fields()
				.that().haveRawType(PatchField.class)
				.and().areNotStatic()
				.should(beAnnotatedWithPatchFieldDeserializer())
				.because("CLAUDE.md Architecture: PatchField의 Jackson 연동은 PatchFieldDeserializer가 담당한다. "
						+ "없으면 '필드 없음'과 '명시적 null'이 구분되지 않는다")
				.check(PRODUCTION_CLASSES);
	}

	@Test
	@DisplayName("C7: @RestController 핸들러는 ResponseEntity<CommonResponse<...>> 또는 CommonResponse<...>를 반환한다")
	void c7_handlersReturnCommonResponse() {
		methods()
				.that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
				.and().areMetaAnnotatedWith(RequestMapping.class)
				.and(notInMethodExceptions(C7_RESPONSE_TYPE_EXCEPTIONS))
				.should(returnCommonResponse())
				.because("CLAUDE.md Conventions: 새 엔드포인트는 CommonResponse.success(...)로 응답한다")
				.check(PRODUCTION_CLASSES);
	}

	@Test
	@DisplayName("C8: service는 ResponseEntity, CommonResponse, jakarta.servlet에 의존하지 않는다")
	void c8_serviceDoesNotDependOnWebTypes() {
		noClasses()
				.that().resideInAPackage("..service..")
				.should().dependOnClassesThat(
						belongToAnyOf(ResponseEntity.class, CommonResponse.class)
								.or(resideInAPackage("jakarta.servlet..")))
				.because("CLAUDE.md Architecture/Conventions: HTTP 응답 포맷(CommonResponse)과 서블릿은 controller 계층의 관심사다")
				.check(PRODUCTION_CLASSES);
	}

	@Test
	@DisplayName("C9: service, controller는 raw 런타임 예외를 직접 생성하지 않는다")
	void c9_noRawRuntimeExceptionsInServiceAndController() {
		noClasses()
				.that().resideInAnyPackage("..service..", "..controller..")
				.and(notInExceptions(C9_RAW_EXCEPTION_EXCEPTIONS))
				.should(instantiateAnyOf(RAW_EXCEPTION_TYPES))
				.because("CLAUDE.md Architecture/Conventions: 비즈니스 규칙 실패는 new BusinessException(ErrorCode.XXX)로 던진다")
				.check(PRODUCTION_CLASSES);
	}

	@Test
	@DisplayName("C10: @Value는 global.config 밖에서 쓰지 않는다")
	void c10_valueAnnotationOnlyInGlobalConfig() {
		noClasses()
				.that().resideOutsideOfPackage("..global.config..")
				.should().dependOnClassesThat().belongToAnyOf(Value.class)
				.because("CLAUDE.md Conventions: 설정값은 흩어진 @Value 대신 global/config의 *Properties로 바인딩한다")
				.check(PRODUCTION_CLASSES);
	}

	@Test
	@DisplayName("C11: 표준 출력과 java.util.logging을 쓰지 않는다")
	void c11_noStandardStreamsOrJavaUtilLogging() {
		CompositeArchRule
				.of(NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS)
				.and(NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING)
				.because("로그는 SLF4J로 남긴다. 표준 출력과 printStackTrace는 로그 수집에서 빠진다")
				.check(PRODUCTION_CLASSES);
	}

	private static DescribedPredicate<JavaMethod> notInMethodExceptions(Set<String> exceptions) {
		return DescribedPredicate.describe(
				"are not in explicit exception list " + exceptions,
				method -> !exceptions.contains(method.getOwner().getName() + "#" + method.getName()));
	}

	private static ArchCondition<JavaField> beAnnotatedWithPatchFieldDeserializer() {
		return new ArchCondition<>("be annotated with @JsonDeserialize(using = PatchFieldDeserializer.class)") {
			@Override
			public void check(JavaField field, ConditionEvents events) {
				boolean usesPatchFieldDeserializer = field.tryGetAnnotationOfType(JsonDeserialize.class.getName())
						.flatMap(annotation -> annotation.get("using"))
						.filter(JavaClass.class::isInstance)
						.map(JavaClass.class::cast)
						.filter(deserializer -> deserializer.isEquivalentTo(PatchFieldDeserializer.class))
						.isPresent();
				if (!usesPatchFieldDeserializer) {
					events.add(SimpleConditionEvent.violated(field,
							field.getFullName() + " is not annotated with @JsonDeserialize(using = PatchFieldDeserializer.class)"));
				}
			}
		};
	}

	private static ArchCondition<JavaMethod> returnCommonResponse() {
		return new ArchCondition<>("return ResponseEntity<CommonResponse<...>> or CommonResponse<...>") {
			@Override
			public void check(JavaMethod method, ConditionEvents events) {
				JavaType returnType = method.getReturnType();
				if (!isCommonResponse(returnType) && !isResponseEntityOfCommonResponse(returnType)) {
					events.add(SimpleConditionEvent.violated(method,
							method.getFullName() + " returns " + returnType.getName()));
				}
			}
		};
	}

	private static boolean isResponseEntityOfCommonResponse(JavaType type) {
		if (!(type instanceof JavaParameterizedType parameterized)
				|| !parameterized.toErasure().isEquivalentTo(ResponseEntity.class)) {
			return false;
		}
		List<JavaType> typeArguments = parameterized.getActualTypeArguments();
		return typeArguments.size() == 1 && isCommonResponse(typeArguments.get(0));
	}

	private static boolean isCommonResponse(JavaType type) {
		return (type instanceof JavaClass || type instanceof JavaParameterizedType)
				&& type.toErasure().isEquivalentTo(CommonResponse.class);
	}

	/**
	 * 지정한 예외 타입을 직접 생성(new)하는 호출을 잡는다.
	 * 서브클래스 생성자의 super(...) 호출은 생성이 아니므로 제외한다 (예: BusinessException extends RuntimeException).
	 */
	private static ArchCondition<JavaClass> instantiateAnyOf(List<Class<? extends RuntimeException>> types) {
		Set<String> typeNames = types.stream().map(Class::getName).collect(Collectors.toSet());
		return new ArchCondition<>("instantiate any of " + typeNames) {
			@Override
			public void check(JavaClass javaClass, ConditionEvents events) {
				for (JavaConstructorCall call : javaClass.getConstructorCallsFromSelf()) {
					String targetName = call.getTargetOwner().getName();
					// noClasses().should(...) 아래에서 쓰이므로 "조건 충족 = 생성함"으로 기록한다 (never()가 뒤집는다)
					boolean instantiates = typeNames.contains(targetName) && !isSuperConstructorCall(call);
					events.add(new SimpleConditionEvent(call, instantiates, call.getDescription()));
				}
			}
		};
	}

	private static boolean isSuperConstructorCall(JavaConstructorCall call) {
		return call.getOrigin() instanceof JavaConstructor
				&& call.getOriginOwner().getRawSuperclass()
				.map(superclass -> superclass.getName().equals(call.getTargetOwner().getName()))
				.orElse(false);
	}
}
