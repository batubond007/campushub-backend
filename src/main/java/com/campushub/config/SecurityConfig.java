package com.campushub.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.campushub.exception.Problems;

/**
 * Stateless API security:
 * <ul>
 * <li>Signed-in calls carry {@code Authorization: Bearer <access token>} (a JWT, see {@link JwtConfig}).</li>
 * <li>{@code /api/auth/**} is open: sign-up, verification, log-in, refresh and log-out work without an access
 * token (refresh and log-out use the httpOnly refresh cookie instead).</li>
 * <li>CSRF protection is off: there is no session cookie, and the refresh cookie is {@code SameSite=Strict}
 * on a same-origin API, so other sites cannot make the browser send it.</li>
 * <li>Role checks use {@code @PreAuthorize}; the JWT's {@code role} claim becomes {@code ROLE_<role>}.</li>
 * <li>401 and 403 answers are Problem Details bodies like every other error.</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

	@Bean
	SecurityFilterChain apiSecurity(HttpSecurity http, AuthenticationEntryPoint entryPoint,
			AccessDeniedHandler accessDeniedHandler) throws Exception {
		http.csrf(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.sessionManagement((session) -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests((requests) -> requests
				.requestMatchers("/api/auth/**", "/api/hello", "/actuator/health", "/error").permitAll()
				// Checked here (before the body is read and validated) and again with @PreAuthorize in EventService.
				.requestMatchers(HttpMethod.POST, "/api/events").hasRole("UNIVERSITY_ADMIN")
				.requestMatchers(HttpMethod.PUT, "/api/events/*").hasRole("UNIVERSITY_ADMIN")
				.requestMatchers(HttpMethod.DELETE, "/api/events/*").hasRole("UNIVERSITY_ADMIN")
				.anyRequest().authenticated())
			.oauth2ResourceServer((oauth2) -> oauth2
				.jwt((jwt) -> jwt.jwtAuthenticationConverter(roleClaimConverter()))
				.authenticationEntryPoint(entryPoint)
				.accessDeniedHandler(accessDeniedHandler))
			.exceptionHandling((exceptions) -> exceptions
				.authenticationEntryPoint(entryPoint)
				.accessDeniedHandler(accessDeniedHandler));
		return http.build();
	}

	/**
	 * Hands security errors to the same {@code @RestControllerAdvice} as all other errors, so 401 / 403 are
	 * Problem Details with {@code code} UNAUTHENTICATED / FORBIDDEN.
	 */
	@Bean
	AuthenticationEntryPoint problemAuthenticationEntryPoint(
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
		return (request, response, ex) -> resolver.resolveException(request, response, null, Problems.unauthenticated());
	}

	@Bean
	AccessDeniedHandler problemAccessDeniedHandler(
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
		return (request, response, ex) -> resolver.resolveException(request, response, null, Problems.forbidden());
	}

	private static JwtAuthenticationConverter roleClaimConverter() {
		JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName("role");
		authorities.setAuthorityPrefix("ROLE_");
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authorities);
		return converter;
	}

}
