package rw.ac.auca.transitdues.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AccessDeniedHandler accessDeniedHandler,
                                                     GrantedAuthoritiesMapper oAuth2UserRoleMapper,
                                                     OAuth2AuthorizationRequestResolver authorizationRequestResolver) throws Exception {
        http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/css/**", "/webjars/**", "/login", "/oauth2/**", "/login/oauth2/**",
                                "/access-denied").permitAll()
                        .requestMatchers("/", "/web/dashboard").hasAnyRole("ADMIN", "FINANCE_OFFICER")
                        .requestMatchers("/web/stages/**").hasAnyRole("ADMIN", "FINANCE_OFFICER")
                        .requestMatchers("/web/operators/**").hasAnyRole("ADMIN", "FINANCE_OFFICER")
                        .requestMatchers("/web/duepayments/**").hasAnyRole("ADMIN", "FINANCE_OFFICER")
                        .requestMatchers("/web/audit-log/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .formLogin(login -> login
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .permitAll())
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
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

    /**
     * Seed users are held in memory because of the current deadline. Post-deadline this
     * should be replaced by a database-backed UserDetailsService so accounts can be
     * managed without a redeploy.
     */
    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        UserDetails admin = User.withUsername("admin")
                .password(passwordEncoder.encode("admin123"))
                .roles("ADMIN")
                .build();

        UserDetails financeOfficer = User.withUsername("finance")
                .password(passwordEncoder.encode("finance123"))
                .roles("FINANCE_OFFICER")
                .build();

        return new InMemoryUserDetailsManager(admin, financeOfficer);
    }
}
