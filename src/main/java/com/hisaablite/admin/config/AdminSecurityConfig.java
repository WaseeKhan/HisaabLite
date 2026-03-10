package com.hisaablite.admin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.header.writers.StaticHeadersWriter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@Slf4j
@Order(1)
public class AdminSecurityConfig {

    @Bean
    public AuthenticationSuccessHandler adminAuthenticationSuccessHandler() {
        return (request, response, authentication) -> {
            log.info("🔐 Admin logged in: {}", authentication.getName());
            response.sendRedirect("/admin/dashboard");
        };
    }

    @Bean
    @Order(1)
    public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http) throws Exception {
        
        http
            .securityMatcher("/admin/**", "/api/admin/**")
            .authorizeHttpRequests(authz -> authz
                .requestMatchers(
                    "/admin/login",
                    "/admin/css/**",
                    "/admin/js/**",
                    "/admin/images/**"
                ).permitAll()
                .anyRequest().hasRole("ADMIN")
            )
            .formLogin(form -> form
                .loginPage("/admin/login")
                .loginProcessingUrl("/admin/login")
                .usernameParameter("username")
                .passwordParameter("password")
                .successHandler(adminAuthenticationSuccessHandler())
                .failureUrl("/admin/login?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/admin/logout"))
                .logoutSuccessUrl("/admin/login?logout=true")
                .deleteCookies("JSESSIONID")
                .clearAuthentication(true)
                .invalidateHttpSession(true)
                .permitAll()
            )
            .sessionManagement(session -> session
                .maximumSessions(1)
                .expiredUrl("/admin/login?expired=true")
            )
            .exceptionHandling(exception -> exception
                .accessDeniedPage("/admin/access-denied")
            )
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/admin/**")
            )
            // ✅ FIXED: Comprehensive CSP for admin panel
            .headers(headers -> headers
                .addHeaderWriter(new StaticHeadersWriter("Content-Security-Policy",
                    "default-src 'self'; " +
                    
                    // ✅ Allow fonts from Google and Font Awesome CDN
                    "font-src 'self' https://fonts.gstatic.com https://cdnjs.cloudflare.com; " +
                    
                    // ✅ Allow styles from all required sources
                    "style-src 'self' 'unsafe-inline' " +
                        "https://fonts.googleapis.com " +
                        "https://cdnjs.cloudflare.com; " +
                    
                    // ✅ Allow scripts with source maps
                    "script-src 'self' 'unsafe-inline' 'unsafe-eval' " +
                        "https://cdn.jsdelivr.net " +
                        "https://cdnjs.cloudflare.com; " +
                    
                    // ✅ Allow connections for source maps and AJAX
                    "connect-src 'self' https://cdn.jsdelivr.net https://cdnjs.cloudflare.com; " +
                    
                    // ✅ Allow images from self and data URIs
                    "img-src 'self' data: https://cdn.jsdelivr.net https://cdnjs.cloudflare.com;"
                ))
                .frameOptions(frame -> frame.deny())
            );
        
        log.info("✅ Admin Security Configuration loaded with full CSP");
        
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain adminApiSecurityFilterChain(HttpSecurity http) throws Exception {
        
        http
            .securityMatcher("/api/admin/**")
            .authorizeHttpRequests(authz -> authz
                .anyRequest().hasRole("ADMIN")
            )
            .httpBasic(basic -> basic
                .realmName("HisaabLite Admin API")
            )
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session
                .sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS)
            )
            .headers(headers -> headers
                .addHeaderWriter(new StaticHeadersWriter("Content-Security-Policy",
                    "default-src 'none'; " +
                    "font-src 'self' https://fonts.gstatic.com https://cdnjs.cloudflare.com; " +
                    "style-src 'unsafe-inline' https://fonts.googleapis.com https://cdnjs.cloudflare.com; " +
                    "script-src 'unsafe-inline' 'unsafe-eval' https://cdn.jsdelivr.net; " +
                    "connect-src 'self' https://cdn.jsdelivr.net;"
                ))
            );
        
        return http.build();
    }
}