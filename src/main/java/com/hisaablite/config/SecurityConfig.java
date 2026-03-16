package com.hisaablite.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import com.hisaablite.controller.CustomAuthFailureHandler;
import com.hisaablite.security.CustomUserDetailsService;

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
                                        "/sales/invoice/*/pdf", // PDF download URLs ke liye CSRF off and open for all
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
                        
                        // ===== ADDED: Headers configuration with CSP =====
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

                        .formLogin(form -> form
                                        .loginPage("/login")
                                        .failureHandler(failureHandler)
                                        .defaultSuccessUrl("/dashboard")
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