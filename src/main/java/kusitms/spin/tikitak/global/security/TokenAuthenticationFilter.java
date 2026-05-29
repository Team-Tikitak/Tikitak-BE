package kusitms.spin.tikitak.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kusitms.spin.tikitak.global.exception.BusinessException;
import kusitms.spin.tikitak.service.auth.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class TokenAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";

	private final TokenService tokenService;

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain
	) throws ServletException, IOException {
		String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
			filterChain.doFilter(request, response);
			return;
		}

		try {
			String accessToken = authorization.substring(BEARER_PREFIX.length());
			Long memberId = tokenService.getMemberIdFromAccessToken(accessToken);
			AuthPrincipal principal = new AuthPrincipal(memberId);
			UsernamePasswordAuthenticationToken authentication =
					new UsernamePasswordAuthenticationToken(principal, null, List.of());
			SecurityContextHolder.getContext().setAuthentication(authentication);
			filterChain.doFilter(request, response);
		} catch (BusinessException e) {
			SecurityContextHolder.clearContext();
			filterChain.doFilter(request, response);
		}
	}
}
