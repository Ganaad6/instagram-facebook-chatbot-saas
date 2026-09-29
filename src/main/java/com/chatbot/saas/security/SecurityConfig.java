package com.chatbot.saas.security;

import com.chatbot.saas.service.BusinessService;
import com.chatbot.saas.service.StaffAuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${admin.username}")
    private String adminUsername;

    @Value("${admin.password}")
    private String adminPassword;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    static final String SESSION_COOKIE = "SESSION";

    @Value("${server.servlet.session.cookie.secure:false}")
    private boolean secureCookies;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        return new InMemoryUserDetailsManager(
                User.withUsername(adminUsername)
                        .password(passwordEncoder.encode(adminPassword))
                        .roles("ADMIN")
                        .build());
    }

    @Bean
    public ApiKeyAuthenticationFilter apiKeyAuthenticationFilter(BusinessService businessService) {
        return new ApiKeyAuthenticationFilter(businessService);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                     ApiKeyAuthenticationFilter apiKeyAuthenticationFilter,
                                                     StaffAuthService staffAuthService) throws Exception {
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokenRepository.setCookieCustomizer(cookie -> cookie.secure(secureCookies).sameSite("Lax").path("/"));

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                        .requireCsrfProtectionMatcher(SecurityConfig::requiresCsrf))
                .cors(cors -> {})
                // Spring Security keeps no session state of its own: StaffSessionFilter re-reads
                // the dashboard login from the Spring Session store on every request, and
                // AuthController rotates the session id at sign-in. Session management must be
                // off, or it would treat each such request as a new login - rotating the session
                // id and clearing the CSRF cookie every time.
                .sessionManagement(AbstractHttpConfigurer::disable)
                .securityContext(context -> context.securityContextRepository(new RequestAttributeSecurityContextRepository()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/webhook/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/businesses/register").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/meta/callback").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf", "/api/auth/links/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/signup",
                                "/api/auth/logout", "/api/auth/links/accept").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").hasRole("BUSINESS")
                        .anyRequest().denyAll())
                .httpBasic(basic -> basic
                        .authenticationEntryPoint((request, response, authException) ->
                                writeError(response, 401, "Authentication required")))
                .exceptionHandling(handling -> handling
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeError(response, 403, "Access denied")))
                .addFilterBefore(apiKeyAuthenticationFilter, BasicAuthenticationFilter.class)
                .addFilterAfter(new StaffSessionFilter(staffAuthService), ApiKeyAuthenticationFilter.class)
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CSRF only matters where the browser attaches credentials by itself - the dashboard's
     * session cookie. API-key and webhook calls carry their credential explicitly, and the sign-in
     * endpoints are always protected so a foreign site can't sign a visitor into another account.
     */
    static boolean requiresCsrf(HttpServletRequest request) {
        if (SAFE_METHODS.contains(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        if (request.getHeader(ApiKeyAuthenticationFilter.API_KEY_HEADER) != null
                || path.startsWith("/webhook") || path.startsWith("/api/admin/")
                || path.equals("/api/businesses/register")) {
            return false;
        }
        if (path.startsWith("/api/auth/")) {
            return true;
        }
        Cookie[] cookies = request.getCookies();
        return cookies != null && Arrays.stream(cookies).anyMatch(c -> SESSION_COOKIE.equals(c.getName()));
    }

    private static void writeError(jakarta.servlet.http.HttpServletResponse response, int status, String message)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(Map.of("error", message, "status", status)));
    }
}
