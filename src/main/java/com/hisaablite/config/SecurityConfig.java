package com.hisaablite.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.security.web.header.writers.ClearSiteDataHeaderWriter;
import org.springframework.security.web.authentication.logout.HeaderWriterLogoutHandler;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.session.SessionInformationExpiredEvent;
import org.springframework.security.web.session.SessionInformationExpiredStrategy;
import com.hisaablite.controller.CustomAuthFailureHandler;
import com.hisaablite.security.CustomUserDetailsService;
import com.hisaablite.security.AuthAuditHelper;

import java.io.IOException;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

        private final CustomUserDetailsService userDetailsService;
        private final CustomAuthFailureHandler failureHandler;
        private final AuthAuditHelper authAuditHelper;
        @Value("${app.security.enforce-single-session:true}")
        private boolean enforceSingleSession;

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http)
                        throws Exception {

                http
                        .authenticationProvider(authenticationProvider())
                        .csrf(csrf -> csrf
                                .ignoringRequestMatchers(
                                        "/admin/users/approve/*",
                                        "/admin/users/bulk-approve"
                                )
                        )    
                        .authorizeHttpRequests(auth -> auth
                                .requestMatchers("/", "/login", "/register", "/forgot-password",
                                                "/reset-password", "/verify",
                                                "/favicon.png", "/favicon.ico", "/css/**", "/js/**",
                                                "/sales/whatsapp/test", "/about", "/careers", "/blog",
                                        "/pricing", "/features", "/images/**", "/blog", "/contact", "/privacy", "/terms",
                                "/how-it-works","/features", "/help")
                                .permitAll()
                                .requestMatchers(HttpMethod.GET, "/support").permitAll()
                                .requestMatchers("/admin/**").hasRole("ADMIN")
                                .requestMatchers("/owner/**").hasRole("OWNER")
                                .requestMatchers("/manager/**").hasAnyRole("OWNER", "MANAGER")
                                .requestMatchers("/cashier/**").hasAnyRole("OWNER", "CASHIER")
                                .requestMatchers("/products/**").hasAnyRole("OWNER", "MANAGER")
                                .requestMatchers("/profile/**").hasRole("OWNER")
                                .requestMatchers("/staff/**").hasRole("OWNER")
                                .requestMatchers("/activity/**").hasRole("OWNER")
                                .requestMatchers("/app/**").authenticated()
                                .requestMatchers("/upgrade/**").authenticated()
                                .anyRequest().authenticated())
                        
                        .headers(headers -> headers
                                .addHeaderWriter(new XXssProtectionHeaderWriter())
                                .cacheControl(Customizer.withDefaults())
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

                        .sessionManagement(session -> session
                                .sessionFixation().migrateSession()
                                .invalidSessionUrl("/login?expired")
                        )

                        .formLogin(form -> form
                                        .loginPage("/login")
                                        .failureHandler(failureHandler)
                                        .successHandler(authenticationSuccessHandler()) // Add custom success handler
                                        .permitAll())
                        .logout(logout -> logout
                                        .logoutUrl("/logout")
                                        .logoutSuccessHandler(logoutSuccessHandler())
                                        .addLogoutHandler(new HeaderWriterLogoutHandler(
                                                        new ClearSiteDataHeaderWriter(
                                                                        ClearSiteDataHeaderWriter.Directive.CACHE,
                                                                        ClearSiteDataHeaderWriter.Directive.COOKIES,
                                                                        ClearSiteDataHeaderWriter.Directive.STORAGE)))
                                        .invalidateHttpSession(true)
                                        .clearAuthentication(true)
                                        .deleteCookies("JSESSIONID")
                                        .permitAll());

                if (enforceSingleSession) {
                        http.sessionManagement(session -> session
                                .maximumSessions(1)
                                .maxSessionsPreventsLogin(false)
                                .expiredSessionStrategy(sessionInformationExpiredStrategy())
                                .sessionRegistry(sessionRegistry()));
                }

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
                        authAuditHelper.logLoginSuccess(authentication, request);

                        response.sendRedirect("/dashboard");
                };
        }

        @Bean
        public LogoutSuccessHandler logoutSuccessHandler() {
                return (request, response, authentication) -> {
                        authAuditHelper.logLogout(authentication, request, "LOGOUT");
                        response.sendRedirect("/login?logout");
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

        private DaoAuthenticationProvider authenticationProvider() {
                DaoAuthenticationProvider auth = new DaoAuthenticationProvider();
                auth.setUserDetailsService(userDetailsService);
                auth.setPasswordEncoder(passwordEncoder());
                return auth;
        }
}
