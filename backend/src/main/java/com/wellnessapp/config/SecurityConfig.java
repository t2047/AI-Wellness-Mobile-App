/**
 * @author Jia Qianrui
 * @author Tao Yuchen
 * @author Liu Zhuocheng
 * @author Zhang Xuhan
 */
package com.wellnessapp.config;

import com.wellnessapp.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration.
 * Public endpoints: auth/register, auth/login.
 * All other endpoints require JWT authentication.
 *
 * @author WellnessApp Team
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers(HttpMethod.GET, "/", "/api", "/api/health", "/favicon.ico").permitAll()
                .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                .requestMatchers("/h2-console/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/error").permitAll()
                // Protected endpoints
                .requestMatchers("/api/wellness-records/**").authenticated()
                .requestMatchers("/api/chat/**").authenticated()
                .requestMatchers("/api/agent/**").authenticated()
                .requestMatchers("/api/recommendations/**").authenticated()
                .requestMatchers("/api/weekly-summaries/**").authenticated()
                .requestMatchers("/api/rag/**").authenticated()
                .requestMatchers("/api/analytics/**").authenticated()
                // All other requests
                .anyRequest().authenticated()
            )
            // For H2 console
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }
}
