package kusitms.spin.tikitak.support;

import kusitms.spin.tikitak.global.exception.GlobalExceptionHandler;
import kusitms.spin.tikitak.global.security.CurrentMemberId;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

public abstract class ApiTest extends UnitTest {

	protected static final Long TEST_MEMBER_ID = 1L;

	protected MockMvc mockMvc(Object controller) {
		return MockMvcBuilders.standaloneSetup(controller)
				.setCustomArgumentResolvers(new TestCurrentMemberIdArgumentResolver())
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();
	}

	private static class TestCurrentMemberIdArgumentResolver implements HandlerMethodArgumentResolver {

		@Override
		public boolean supportsParameter(MethodParameter parameter) {
			return parameter.hasParameterAnnotation(CurrentMemberId.class)
					&& Long.class.isAssignableFrom(parameter.getParameterType());
		}

		@Override
		public Object resolveArgument(
				MethodParameter parameter,
				ModelAndViewContainer mavContainer,
				NativeWebRequest webRequest,
				WebDataBinderFactory binderFactory
		) {
			return TEST_MEMBER_ID;
		}
	}
}
