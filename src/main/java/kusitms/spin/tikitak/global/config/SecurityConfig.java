package kusitms.spin.tikitak.global.config;

import kusitms.spin.tikitak.global.security.CustomAccessDeniedHandler;
import kusitms.spin.tikitak.global.security.CustomAuthenticationEntryPoint;
import kusitms.spin.tikitak.global.security.TokenAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final TokenAuthenticationFilter tokenAuthenticationFilter;
    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
    private final CustomAccessDeniedHandler customAccessDeniedHandler;
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .formLogin(formLogin -> formLogin.disable())
            .httpBasic(httpBasic -> httpBasic.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(customAuthenticationEntryPoint)
                .accessDeniedHandler(customAccessDeniedHandler)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    // health 체크
                    "/api/v1/system/health",
                    "/actuator/**",

                    // auth
                    "/api/v1/auth/oauth/*/start",
                    "/api/v1/auth/oauth/*/callback",
                    "/api/v1/auth/token/refresh",
                    "/api/v1/auth/logout",

                    // swagger 허용
                    "/swagger-ui.html",
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                    "/v3/api-docs.json"
                ).permitAll()
                // 초대 링크 미리보기만 비로그인 허용 (수락 API는 인증 필요)
                .requestMatchers(HttpMethod.GET, "/api/v1/invitation-links/*").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(tokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
