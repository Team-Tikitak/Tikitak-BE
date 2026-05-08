package kusitms.spin.tikitak.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kusitms.spin.tikitak.global.dto.ErrorResponse;
import kusitms.spin.tikitak.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

	private final ObjectMapper objectMapper;

	@Override
	public void handle(
			HttpServletRequest request,
			HttpServletResponse response,
			AccessDeniedException accessDeniedException
	) throws IOException {
		ErrorResponse errorResponse = ErrorResponse.of(
				ErrorCode.AUTH009.getCode(),
				ErrorCode.AUTH009.getMessage(),
				ErrorCode.AUTH009.getStatus(),
				request.getRequestURI()
		);
		response.setStatus(ErrorCode.AUTH009.getStatus());
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
	}
}
