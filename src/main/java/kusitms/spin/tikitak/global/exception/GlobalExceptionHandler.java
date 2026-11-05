package kusitms.spin.tikitak.global.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import kusitms.spin.tikitak.global.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e, HttpServletRequest request) {
		ErrorCode errorCode = e.getErrorCode();
		if (errorCode.getStatus() >= 500) {
			log.error("Business Exception: {}", e.getMessage(), e);
		} else {
			log.warn("Business Exception: {}", e.getMessage());
		}
		ErrorResponse response = ErrorResponse.of(
				errorCode.getCode(),
				errorCode.getMessage(),
				errorCode.getStatus(),
				request.getRequestURI()
		);
		return ResponseEntity.status(errorCode.getStatus()).body(response);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e, HttpServletRequest request) {
		log.warn("Validation Exception: {}", e.getMessage());
		List<ErrorResponse.FieldError> errors = e.getBindingResult().getFieldErrors().stream()
				.map(error -> ErrorResponse.FieldError.builder()
						.field(error.getField())
						.message(error.getDefaultMessage())
						.build())
				.collect(Collectors.toList());

		ErrorResponse response = ErrorResponse.of(
				ErrorCode.COMMON001.getCode(),
				ErrorCode.COMMON001.getMessage(),
				HttpStatus.BAD_REQUEST.value(),
				request.getRequestURI(),
				errors
		);
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> handleTypeMismatchException(
			MethodArgumentTypeMismatchException e,
			HttpServletRequest request
	) {
		log.warn("Type Mismatch Exception: {}", e.getMessage());
		ErrorResponse response = ErrorResponse.of(
				ErrorCode.COMMON001.getCode(),
				ErrorCode.COMMON001.getMessage(),
				HttpStatus.BAD_REQUEST.value(),
				request.getRequestURI()
		);
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(
			HttpMessageNotReadableException e,
			HttpServletRequest request
	) {
		log.warn("Http Message Not Readable Exception: {}", e.getMessage());
		ErrorResponse response = ErrorResponse.of(
				ErrorCode.COMMON001.getCode(),
				ErrorCode.COMMON001.getMessage(),
				HttpStatus.BAD_REQUEST.value(),
				request.getRequestURI()
		);
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
	}

	@ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
	public ResponseEntity<ErrorResponse> handleNotFoundException(Exception e, HttpServletRequest request) {
		log.warn("Not Found Exception: {}", e.getMessage());
		return buildResponse(ErrorCode.COMMON005, request);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ErrorResponse> handleMethodNotSupportedException(
			HttpRequestMethodNotSupportedException e,
			HttpServletRequest request
	) {
		log.warn("Method Not Supported Exception: {}", e.getMessage());
		ErrorCode errorCode = ErrorCode.COMMON006;
		ErrorResponse response = ErrorResponse.of(
				errorCode.getCode(),
				errorCode.getMessage(),
				errorCode.getStatus(),
				request.getRequestURI()
		);
		Set<HttpMethod> supportedMethods = e.getSupportedHttpMethods();
		return ResponseEntity.status(errorCode.getStatus())
				.headers(headers -> {
					if (supportedMethods != null && !supportedMethods.isEmpty()) {
						headers.setAllow(supportedMethods);
					}
				})
				.body(response);
	}

	@ExceptionHandler({MissingServletRequestParameterException.class, MissingRequestHeaderException.class})
	public ResponseEntity<ErrorResponse> handleMissingRequestValueException(
			Exception e,
			HttpServletRequest request
	) {
		log.warn("Missing Request Value Exception: {}", e.getMessage());
		return buildResponse(ErrorCode.COMMON002, request);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ErrorResponse> handleConstraintViolationException(
			ConstraintViolationException e,
			HttpServletRequest request
	) {
		log.warn("Constraint Violation Exception: {}", e.getMessage());
		List<ErrorResponse.FieldError> errors = e.getConstraintViolations().stream()
				.map(violation -> ErrorResponse.FieldError.builder()
						.field(violation.getPropertyPath().toString())
						.message(violation.getMessage())
						.build())
				.collect(Collectors.toList());
		return buildResponse(ErrorCode.COMMON001, request, errors);
	}

	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ErrorResponse> handleHandlerMethodValidationException(
			HandlerMethodValidationException e,
			HttpServletRequest request
	) {
		log.warn("Handler Method Validation Exception: {}", e.getMessage());
		List<ErrorResponse.FieldError> errors = e.getParameterValidationResults().stream()
				.flatMap(result -> result.getResolvableErrors().stream()
						.map(error -> ErrorResponse.FieldError.builder()
								.field(result.getMethodParameter().getParameterName())
								.message(error.getDefaultMessage())
								.build()))
				.collect(Collectors.toList());
		return buildResponse(ErrorCode.COMMON001, request, errors);
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<ErrorResponse> handleMediaTypeNotSupportedException(
			HttpMediaTypeNotSupportedException e,
			HttpServletRequest request
	) {
		log.warn("Media Type Not Supported Exception: {}", e.getMessage());
		return buildResponse(ErrorCode.COMMON003, request);
	}

	private ResponseEntity<ErrorResponse> buildResponse(ErrorCode errorCode, HttpServletRequest request) {
		ErrorResponse response = ErrorResponse.of(
				errorCode.getCode(),
				errorCode.getMessage(),
				errorCode.getStatus(),
				request.getRequestURI()
		);
		return ResponseEntity.status(errorCode.getStatus()).body(response);
	}

	private ResponseEntity<ErrorResponse> buildResponse(
			ErrorCode errorCode,
			HttpServletRequest request,
			List<ErrorResponse.FieldError> errors
	) {
		ErrorResponse response = ErrorResponse.of(
				errorCode.getCode(),
				errorCode.getMessage(),
				errorCode.getStatus(),
				request.getRequestURI(),
				errors
		);
		return ResponseEntity.status(errorCode.getStatus()).body(response);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleException(Exception e, HttpServletRequest request) {
		log.error("Internal Server Error: ", e);
		ErrorResponse response = ErrorResponse.of(
				ErrorCode.COMMON004.getCode(),
				ErrorCode.COMMON004.getMessage(),
				HttpStatus.INTERNAL_SERVER_ERROR.value(),
				request.getRequestURI()
		);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
	}
}
