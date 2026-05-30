package kusitms.spin.tikitak.global.config;

import kusitms.spin.tikitak.global.filter.RequestBodyLoggingFilter;
import kusitms.spin.tikitak.global.security.CurrentMemberIdArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

	private final CurrentMemberIdArgumentResolver currentMemberIdArgumentResolver;

	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		resolvers.add(currentMemberIdArgumentResolver);
	}
}
