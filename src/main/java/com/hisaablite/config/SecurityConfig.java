package com.hisaablite.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.session.SessionInformationExpiredEvent;
import org.springframework.security.web.session.SessionInformationExpiredStrategy;
import com.hisaablite.controller.CustomAuthFailureHandler;
import com.hisaablite.security.CustomUserDetailsService;

import java.io.IOException;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

        private final CustomUserDetailsService userDetailsService;
        private final CustomAuthFailureHandler failureHandler;

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http)
                        throws Exception {

                http
                        .csrf(csrf -> csrf
                                .ignoringRequestMatchers(
                                        "/sales/invoice/*/pdf",
                                        "/admin/users/approve/*",
                                        "/admin/users/bulk-approve"
                                )
                        )    
                        .authorizeHttpRequests(auth -> auth
                                .requestMatchers("/", "/login", "/register", "/forgot-password",
                                                "/reset-password", "/verify",
                                                "/favicon.png", "/favicon.ico", "/css/**", "/js/**",
                                                "/sales/whatsapp/test", "/support/**",
                                                "/sales/invoice/*/pdf", "/about", "/careers", "/blog",
                                        "/pricing", "/features", "/images/**", "/blog", "/contact", "/privacy", "/terms")
                                .permitAll()
                                .requestMatchers("/admin/**").hasRole("ADMIN")
                                .requestMatchers("/owner/**").hasRole("OWNER")
                                .requestMatchers("/manager/**").hasAnyRole("OWNER", "MANAGER")
                                .requestMatchers("/cashier/**").hasAnyRole("OWNER", "CASHIER")
                                .requestMatchers("/products/**").hasAnyRole("OWNER", "MANAGER")
                                .requestMatchers("/profile/**").hasRole("OWNER")
                                .requestMatchers("/staff/**").hasRole("OWNER")
                                .requestMatchers("/app/**").authenticated()
                                .anyRequest().authenticated())
                        
                        .headers(headers -> headers
                                .addHeaderWriter(new XXssProtectionHeaderWriter())
                                .contentSecurityPolicy(csp -> csp
                                        .policyDirectives(
                                            "default-src 'self'; " +
                                            "script-src 'self' 'unsafe-inline' 'unsafe-eval' " +
                                                "https://cdn.jsdelivr.net https://cdnjs.cloudflare.com https://code.jquery.com; " +
                                            "style-src 'self' 'unsafe-inline' " +
                                                "https://fonts.googleapis.com https://cdn.jsdelivr.net https://cdnjs.cloudflare.com; " +
                                            "font-src 'self' https://fonts.gstatic.com https://cdnjs.cloudflare.com; " +
                                            "img-src 'self' data:; " +
                                            "connect-src 'self';"
                                        )
                                )
                        )

                        // ===== ADDED: Session Management =====
                        .sessionManagement(session -> session
                                .maximumSessions(1) // Only ONE session per user
                                .maxSessionsPreventsLogin(false) // Don't prevent, expire old session
                                .expiredSessionStrategy(sessionInformationExpiredStrategy())
                                .sessionRegistry(sessionRegistry())
                        )
                        .sessionManagement(session -> session
                                .sessionFixation().migrateSession()
                        )

                        .formLogin(form -> form
                                        .loginPage("/login")
                                        .failureHandler(failureHandler)
                                        .successHandler(authenticationSuccessHandler()) // Add custom success handler
                                        .permitAll())
                        .logout(logout -> logout
                                        .logoutUrl("/logout")
                                        .logoutSuccessUrl("/login?logout")
                                        .invalidateHttpSession(true)
                                        .clearAuthentication(true)
                                        .deleteCookies("JSESSIONID")
                                        .permitAll());

                return http.build();
        }

        @Bean
        public SessionRegistry sessionRegistry() {
                return new SessionRegistryImpl();
        }

        @Bean
        public HttpSessionEventPublisher httpSessionEventPublisher() {
                return new HttpSessionEventPublisher();
        }

        @Bean
        public AuthenticationSuccessHandler authenticationSuccessHandler() {
                return (request, response, authentication) -> {
                        // Register new session
                        sessionRegistry().registerNewSession(request.getSession().getId(), authentication.getPrincipal());
                        
                        // Redirect based on role
                        String role = authentication.getAuthorities().iterator().next().getAuthority();
                        if (role.equals("ROLE_OWNER")) {
                                response.sendRedirect("/owner/dashboard");
                        } else if (role.equals("ROLE_MANAGER")) {
                                response.sendRedirect("/manager/dashboard");
                        } else {
                                response.sendRedirect("/cashier/dashboard");
                        }
                };
        }

        @Bean
        public SessionInformationExpiredStrategy sessionInformationExpiredStrategy() {
                return new SessionInformationExpiredStrategy() {
                        @Override
                        public void onExpiredSessionDetected(SessionInformationExpiredEvent event) throws IOException {
                                event.getRequest().getSession().setAttribute("sessionExpiredMessage", 
                                        "Your session has expired because you logged in from another device.");
                                event.getResponse().sendRedirect("/login?expired");
                        }
                };
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        @Bean
        public DaoAuthenticationProvider authenticationProvider() {
                DaoAuthenticationProvider auth = new DaoAuthenticationProvider();
                auth.setUserDetailsService(userDetailsService);
                auth.setPasswordEncoder(passwordEncoder());
                return auth;
        }
}