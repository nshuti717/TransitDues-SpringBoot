package rw.ac.auca.transitdues.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AccessDeniedHandler accessDeniedHandler,
                                                     AuthenticationSuccessHandler roleBasedAuthenticationSuccessHandler,
                                                     GrantedAuthoritiesMapper oAuth2UserRoleMapper,
                                                     OAuth2AuthorizationRequestResolver authorizationRequestResolver) throws Exception {
        http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/css/**", "/webjars/**", "/login", "/register", "/oauth2/**",
                                "/login/oauth2/**", "/access-denied", "/forgot-password", "/reset-password",
                                "/verify-account", "/verify-account/resend")
                        .permitAll()
                        .requestMatchers("/", "/web/dashboard").hasAnyRole("ADMIN", "FINANCE_OFFICER")
                        .requestMatchers("/web/stages/**").hasAnyRole("ADMIN", "FINANCE_OFFICER")
                        .requestMatchers("/web/operators/**").hasAnyRole("ADMIN", "FINANCE_OFFICER")
                        .requestMatchers("/web/duepayments/**").hasAnyRole("ADMIN", "FINANCE_OFFICER")
                        .requestMatchers("/web/finance/**").hasAnyRole("ADMIN", "FINANCE_OFFICER")
                        .requestMatchers("/web/audit-log/**").hasRole("ADMIN")
                        .requestMatchers("/portal/**").hasRole("OPERATOR")
                        .anyRequest().authenticated())
                .formLogin(login -> login
                        .loginPage("/login")
                        .successHandler(roleBasedAuthenticationSuccessHandler)
                        .permitAll())
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/login")
                        .successHandler(roleBasedAuthenticationSuccessHandler)
                        .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(authorizationRequestResolver))
                        .userInfoEndpoint(userInfo -> userInfo.userAuthoritiesMapper(oAuth2UserRoleMapper)))
                .exceptionHandling(exceptions -> exceptions
                        .accessDeniedHandler(accessDeniedHandler));

        return http.build();
    }

    /**
     * Forces the Google account chooser on every login attempt instead of silently
     * reusing whichever Google account last signed in on this browser.
     */
    @Bean
    public OAuth2AuthorizationRequestResolver authorizationRequestResolver(
            ClientRegistrationRepository clientRegistrationRepository) {
        DefaultOAuth2AuthorizationRequestResolver resolver = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository, DefaultOAuth2AuthorizationRequestResolver.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
        resolver.setAuthorizationRequestCustomizer(
                customizer -> customizer.additionalParameters(params -> params.put("prompt", "select_account")));
        return resolver;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * No explicit AuthenticationProvider bean is declared here: Spring Boot's
     * autoconfiguration wires a DaoAuthenticationProvider from whatever
     * UserDetailsService and PasswordEncoder beans are present in the context
     * (see CustomUserDetailsService), the same way it did for the in-memory users
     * this replaced.
     */

    /**
     * Forwards role-denied requests (e.g. a FINANCE_OFFICER POSTing to an ADMIN-only
     * route) to a styled Access Denied page instead of Spring's default Whitelabel
     * error page.
     */
    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            request.getRequestDispatcher("/access-denied").forward(request, response);
        };
    }
}
