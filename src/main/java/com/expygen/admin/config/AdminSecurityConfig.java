package com.expygen.admin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.HeaderWriterLogoutHandler;
import org.springframework.security.web.header.writers.ClearSiteDataHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;

import com.expygen.security.AuthAuditHelper;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@Slf4j
@Order(1)
public class AdminSecurityConfig {

    private final AuthAuditHelper authAuditHelper;

    @Bean
    public AuthenticationSuccessHandler adminAuthenticationSuccessHandler() {
        return (request, response, authentication) -> {
            log.info("🔐 Admin logged in: {}", authentication.getName());
            authAuditHelper.logLoginSuccess(authentication, request);
            response.sendRedirect("/admin/dashboard");
        };
    }

    @Bean
    public AuthenticationFailureHandler adminAuthenticationFailureHandler() {
        return (request, response, exception) -> {
            authAuditHelper.logLoginFailure(request.getParameter("username"), exception.getMessage(), request);
            response.sendRedirect("/admin/login?error=true");
        };
    }

    @Bean
    public LogoutSuccessHandler adminLogoutSuccessHandler() {
        return (request, response, authentication) -> {
            authAuditHelper.logLogout(authentication, request, "ADMIN_LOGOUT");
            response.setStatus(HttpServletResponse.SC_FOUND);
            response.sendRedirect("/admin/login?logout=true");
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
                                "/admin/access-denied",
                                "/admin/404",
                                "/admin/css/**",
                                "/admin/js/**",
                                "/admin/images/**")
                        .permitAll()
                        .anyRequest().hasRole("ADMIN"))
                .formLogin(form -> form
                        .loginPage("/admin/login")
                        .loginProcessingUrl("/admin/login")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .successHandler(adminAuthenticationSuccessHandler())
                        .failureHandler(adminAuthenticationFailureHandler())
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/admin/logout")
                        .logoutSuccessHandler(adminLogoutSuccessHandler())
                        .addLogoutHandler(new HeaderWriterLogoutHandler(
                                new ClearSiteDataHeaderWriter(
                                        ClearSiteDataHeaderWriter.Directive.CACHE,
                                        ClearSiteDataHeaderWriter.Directive.COOKIES,
                                        ClearSiteDataHeaderWriter.Directive.STORAGE)))
                        .deleteCookies("JSESSIONID")
                        .clearAuthentication(true)
                        .invalidateHttpSession(true)
                        .permitAll())
                .sessionManagement(session -> session
                        .maximumSessions(1)
                        .expiredUrl("/admin/login?expired=true"))
                .exceptionHandling(exception -> exception
                        .accessDeniedPage("/admin/access-denied"))
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/api/admin/**"))

                .headers(headers -> headers
                        .addHeaderWriter(new StaticHeadersWriter("Content-Security-Policy",
                                "default-src 'self'; " +

                                        "font-src 'self' https://fonts.gstatic.com https://cdnjs.cloudflare.com; " +

                                        "style-src 'self' 'unsafe-inline' " +
                                        "https://fonts.googleapis.com " +
                                        "https://cdnjs.cloudflare.com; " +

                                        "script-src 'self' 'unsafe-inline' 'unsafe-eval' " +
                                        "https://cdn.jsdelivr.net " +
                                        "https://cdnjs.cloudflare.com; " +

                                        "connect-src 'self' https://cdn.jsdelivr.net https://cdnjs.cloudflare.com; " +

                                        "img-src 'self' data: https://cdn.jsdelivr.net https://cdnjs.cloudflare.com;"))
                        .frameOptions(frame -> frame.deny()));

        log.info("Admin Security Configuration loaded with full CSP");

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain adminApiSecurityFilterChain(HttpSecurity http) throws Exception {

        http
                .securityMatcher("/api/admin/**")
                .authorizeHttpRequests(authz -> authz
                        .anyRequest().hasRole("ADMIN"))
                .httpBasic(basic -> basic
                        .realmName("Expygen Admin API"))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(
                                org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .addHeaderWriter(new StaticHeadersWriter("Content-Security-Policy",
                                "default-src 'none'; " +
                                        "font-src 'self' https://fonts.gstatic.com https://cdnjs.cloudflare.com; " +
                                        "style-src 'unsafe-inline' https://fonts.googleapis.com https://cdnjs.cloudflare.com; "
                                        +
                                        "script-src 'unsafe-inline' 'unsafe-eval' https://cdn.jsdelivr.net; " +
                                        "connect-src 'self' https://cdn.jsdelivr.net;")));

        return http.build();
    }
}
