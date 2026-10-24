package dev.pluck.backend.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CompositeAuthFilter compositeAuthFilter;

    public SecurityConfig(CompositeAuthFilter compositeAuthFilter) {
        this.compositeAuthFilter = compositeAuthFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write("{\"error\":\"UNAUTHORIZED\",\"message\":\"Authentication required to access this resource\"}");
                }))
                .authorizeHttpRequests(auth -> auth
                        // Frontend page views and assets
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/app",
                                "/app.html",
                                "/auth",
                                "/auth.html",
                                "/studio",
                                "/studio.html",
                                "/snippet",
                                "/snippet.html",
                                "/share",
                                "/share.html",
                                "/settings",
                                "/settings.html",
                                "/team",
                                "/team.html",
                                "/*.html",
                                "/*.css",
                                "/*.js",
                                "/*.png",
                                "/*.webp",
                                "/*.svg",
                                "/css/**",
                                "/js/**",
                                "/assets/**",
                                "/favicon.ico"
                        ).permitAll()

                        // Health and diagnostics
                        .requestMatchers("/api/health/**").permitAll()

                        // Authentication public routes
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/register",
                                "/api/auth/oauth",
                                "/api/auth/password/**"
                        ).permitAll()

                        // Public snippet views, secret scanning, and stateless AST parsing
                        .requestMatchers("/api/public/**", "/api/snippets/parse", "/api/secrets/**", "/api/runner/**", "/api/search/**", "/api/search", "/api/ai/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/folders/**", "/api/folders").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/snippets/*/versions").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/teams/**").permitAll()
                        .requestMatchers("/api/mcp/**", "/api/mcp", "/api/cli/**").permitAll()

                        // All other /api endpoints require authentication (Session Cookie or Bearer PAT)
                        .requestMatchers("/api/**").authenticated()

                        // Default permit any other static resource requests
                        .anyRequest().permitAll()
                )
                .addFilterBefore(compositeAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
