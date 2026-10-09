package com.tuneflow.common.config;

import com.tuneflow.auth.security.JwtAuthenticationFilter;
import com.tuneflow.common.security.RateLimitFilter;
import com.tuneflow.common.security.RestAccessDeniedHandler;
import com.tuneflow.common.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * Stateless security configuration.
 *
 * <ul>
 *   <li>BCrypt password hashing (docs section 30)</li>
 *   <li>JWT bearer authentication, no server-side HTTP session</li>
 *   <li>Public read access to the catalog so anonymous browsing works, everything else authenticated</li>
 *   <li>Method-level RBAC via {@code @PreAuthorize} for ARTIST/ADMIN operations</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(AppProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final RestAccessDeniedHandler restAccessDeniedHandler;
    private final AppProperties appProperties;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // stateless JWT API, no cookie session
                .cors(withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .exceptionHandling(ex -> ex
                        // 401 tells the client to refresh its access token; 403 must not.
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        // --- infrastructure / docs ---
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/error").permitAll()
                        // --- auth ---
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        // Signed media URLs are self-authorizing (HMAC + expiry), which is what lets
                        // the audio player stream without an Authorization header. Tampering is
                        // rejected by MediaController / the object store, not by Spring Security.
                        .requestMatchers(HttpMethod.GET, "/api/v1/media/**").permitAll()
                        // --- public catalog reads (anonymous browsing) ---
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/songs", "/api/v1/songs/*", "/api/v1/songs/*/lyrics",
                                "/api/v1/albums", "/api/v1/albums/*", "/api/v1/albums/*/songs",
                                "/api/v1/artists", "/api/v1/artists/*", "/api/v1/artists/*/albums",
                                "/api/v1/artists/*/songs", "/api/v1/genres",
                                "/api/v1/search/**", "/api/v1/home/**",
                                "/api/v1/recommendations/**").permitAll()
                        // --- everything else requires a valid access token ---
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = appProperties.cors().allowedOrigins();
        if (origins.contains("*")) {
            configuration.setAllowedOriginPatterns(List.of("*"));
        } else {
            configuration.setAllowedOrigins(origins);
        }
        configuration.setAllowedMethods(appProperties.cors().allowedMethods());
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Content-Disposition"));
        configuration.setAllowCredentials(appProperties.cors().allowCredentials());
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
