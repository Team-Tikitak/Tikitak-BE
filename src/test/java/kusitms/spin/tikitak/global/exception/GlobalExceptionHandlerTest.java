package kusitms.spin.tikitak.global.exception;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import kusitms.spin.tikitak.support.ApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.NoHandlerFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest extends ApiTest {

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = mockMvc(new TestController());
	}

	@Test
	@DisplayName("없는 경로는 404와 COMMON005로 응답한다")
	void notFound() throws Exception {
		mockMvc.perform(get("/test/not-exist"))
				.andExpect(status().isNotFound())
				.andExpect(result -> assertThat(result.getResolvedException())
						.isInstanceOf(NoHandlerFoundException.class))
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.code").value("COMMON005"));
	}

	@Test
	@DisplayName("지원하지 않는 HTTP 메서드는 405, COMMON006, Allow 헤더로 응답한다")
	void methodNotSupported() throws Exception {
		mockMvc.perform(delete("/test/ok"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(header().exists(HttpHeaders.ALLOW))
				.andExpect(header().string(HttpHeaders.ALLOW, containsString("GET")))
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.status").value(405))
				.andExpect(jsonPath("$.code").value("COMMON006"));
	}

	@Test
	@DisplayName("필수 요청 파라미터 누락은 400과 COMMON002로 응답한다")
	void missingRequestParameter() throws Exception {
		mockMvc.perform(get("/test/param"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.code").value("COMMON002"));
	}

	@Test
	@DisplayName("필수 요청 헤더 누락은 400과 COMMON002로 응답한다")
	void missingRequestHeader() throws Exception {
		mockMvc.perform(get("/test/header"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.code").value("COMMON002"));
	}

	@Test
	@DisplayName("ConstraintViolationException은 400, COMMON001, 위반 항목 목록으로 응답한다")
	void constraintViolation() throws Exception {
		mockMvc.perform(get("/test/constraint-violation"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.code").value("COMMON001"))
				.andExpect(jsonPath("$.errors", hasSize(1)))
				.andExpect(jsonPath("$.errors[0].field").value("name"));
	}

	@Test
	@DisplayName("HandlerMethodValidationException은 400, COMMON001, 위반 항목 목록으로 응답한다")
	void handlerMethodValidation() throws Exception {
		mockMvc.perform(get("/test/method-validation").param("size", "0"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.code").value("COMMON001"))
				.andExpect(jsonPath("$.errors", hasSize(1)))
				.andExpect(jsonPath("$.errors[0].field").value("size"));
	}

	@Test
	@DisplayName("지원하지 않는 Content-Type은 415와 COMMON003으로 응답한다")
	void mediaTypeNotSupported() throws Exception {
		mockMvc.perform(post("/test/body")
						.contentType(MediaType.APPLICATION_XML)
						.content("<name>a</name>"))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.status").value(415))
				.andExpect(jsonPath("$.code").value("COMMON003"));
	}

	@Test
	@DisplayName("미분류 예외는 500과 COMMON004로 응답한다")
	void unexpectedException() throws Exception {
		mockMvc.perform(get("/test/unexpected"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.status").value(500))
				.andExpect(jsonPath("$.code").value("COMMON004"));
	}

	@RestController
	static class TestController {

		private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

		@GetMapping("/test/ok")
		String ok() {
			return "ok";
		}

		@GetMapping("/test/param")
		String param(@RequestParam("value") String value) {
			return value;
		}

		@GetMapping("/test/header")
		String header(@RequestHeader("X-Test-Header") String value) {
			return value;
		}

		@GetMapping("/test/constraint-violation")
		String constraintViolation() {
			throw new ConstraintViolationException(VALIDATOR.validate(new TestBody("")));
		}

		@GetMapping("/test/method-validation")
		String methodValidation(@RequestParam("size") @Min(1) int size) {
			return String.valueOf(size);
		}

		@PostMapping("/test/body")
		String body(@RequestBody TestBody body) {
			return body.name();
		}

		@GetMapping("/test/unexpected")
		String unexpected() {
			throw new IllegalStateException("unexpected");
		}
	}

	record TestBody(@NotBlank String name) {
	}
}
