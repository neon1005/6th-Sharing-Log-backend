package gdg.sharinglog.config;

import gdg.sharinglog.config.oauth.OAuth2FailureHandler;
import gdg.sharinglog.config.oauth.OAuth2UserCustomService;
import gdg.sharinglog.config.oauth.OAuth2SuccessHandler;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.util.StringUtils;

@Configuration
@RequiredArgsConstructor
public class WebOAuthSecurityConfig {

    private final OAuth2UserCustomService oAuth2UserCustomService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final OAuth2FailureHandler oAuth2FailureHandler;

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            CorsConfigurationSource corsConfigurationSource
    ) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/login",
                                "/css/**",
                                "/img/**",
                                "/js/**",
                                "/favicon.ico",
                                "/error",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/login")
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(oAuth2UserCustomService))
                        .successHandler(oAuth2SuccessHandler)
                        .failureHandler(oAuth2FailureHandler)
                )
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                WebOAuthSecurityConfig::isApiRequest
                        )
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/login"),
                                request -> !isApiRequest(request)
                        ))
                .requestCache(cache -> cache.requestCache(invitationRequestCache()))

                // 해당 요청만 CSRF 예외 처리하고
                // Spring Security 로그아웃 필터가 세션 무효화, 인증 정보 삭제, JSESSIONID 쿠키 삭제 후
                // 204 No Content를 반환하도록 설정
                .csrf(csrf -> csrf.ignoringRequestMatchers(WebOAuthSecurityConfig::isApiLogoutRequest))
                .logout(logout -> logout
                        .logoutRequestMatcher(WebOAuthSecurityConfig::isApiLogoutRequest)
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(HttpStatus.NO_CONTENT.value()))
                )
                .build();
    }

    private static HttpSessionRequestCache invitationRequestCache() {
        HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
        requestCache.setRequestMatcher(WebOAuthSecurityConfig::isInvitationPageRequest);
        return requestCache;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.frontend-origin}")
            String frontendOrigin
    ) {
        String normalizedFrontendOrigin = normalizeFrontendOrigin(frontendOrigin);
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(normalizedFrontendOrigin));
        configuration.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Location"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    static String normalizeFrontendOrigin(String frontendOrigin) {
        if (!StringUtils.hasText(frontendOrigin)) {
            throw new IllegalArgumentException("app.frontend-origin은 비어 있을 수 없습니다.");
        }

        String normalized = frontendOrigin.strip().replaceFirst("/+$", "");
        try {
            URI origin = new URI(normalized);
            String scheme = origin.getScheme();
            boolean supportedScheme = "http".equalsIgnoreCase(scheme)
                    || "https".equalsIgnoreCase(scheme);
            boolean hasOnlyOriginComponents = origin.getHost() != null
                    && origin.getUserInfo() == null
                    && origin.getRawPath().isEmpty()
                    && origin.getRawQuery() == null
                    && origin.getRawFragment() == null;
            if (!supportedScheme || !hasOnlyOriginComponents) {
                throw invalidFrontendOrigin(frontendOrigin);
            }

            return new URI(
                    scheme.toLowerCase(Locale.ROOT),
                    null,
                    origin.getHost().toLowerCase(Locale.ROOT),
                    origin.getPort(),
                    null,
                    null,
                    null
            ).toASCIIString();
        } catch (URISyntaxException exception) {
            throw invalidFrontendOrigin(frontendOrigin, exception);
        }
    }

    private static IllegalArgumentException invalidFrontendOrigin(String frontendOrigin) {
        return invalidFrontendOrigin(frontendOrigin, null);
    }

    private static IllegalArgumentException invalidFrontendOrigin(
            String frontendOrigin,
            Exception cause
    ) {
        String message = "app.frontend-origin은 경로가 없는 http(s) origin이어야 합니다: "
                + frontendOrigin;
        return cause == null
                ? new IllegalArgumentException(message)
                : new IllegalArgumentException(message, cause);
    }

    private static boolean isApiLogoutRequest(jakarta.servlet.http.HttpServletRequest request) {
        String path = requestPath(request);
        return "POST".equals(request.getMethod()) && "/api/auth/logout".equals(path);
    }

    private static boolean isInvitationPageRequest(
            jakarta.servlet.http.HttpServletRequest request
    ) {
        return "GET".equals(request.getMethod())
                && requestPath(request).startsWith("/invite/");
    }

    private static boolean isApiRequest(jakarta.servlet.http.HttpServletRequest request) {
        return requestPath(request).startsWith("/api/");
    }

    private static String requestPath(jakarta.servlet.http.HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return path;
    }
}
