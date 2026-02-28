package com.hisaablite.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

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
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/login", "/register", "/forgot-password","/reset-password", "/css/**").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/owner/**").hasRole("OWNER")
                .requestMatchers("/manager/**").hasAnyRole("OWNER", "MANAGER")
                .requestMatchers("/cashier/**").hasAnyRole("OWNER", "CASHIER")
                .requestMatchers("/products/**").hasAnyRole("OWNER", "MANAGER")
                .requestMatchers("/profile/**").hasRole("OWNER")
                .requestMatchers("/staff/**").hasRole("OWNER")
                .requestMatchers("/app/**").authenticated()
                .anyRequest().authenticated()
            )

               
            
            .formLogin(form -> form
                .loginPage("/login")
                .failureHandler(failureHandler)
                .defaultSuccessUrl("/dashboard")
                .permitAll()
)
            .logout(logout -> logout
            .logoutUrl("/logout")
            .logoutSuccessUrl("/login?logout")
            .invalidateHttpSession(true)
            .clearAuthentication(true)
            .deleteCookies("JSESSIONID")
            .permitAll()
        );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider auth =
                new DaoAuthenticationProvider();
        auth.setUserDetailsService(userDetailsService);
        auth.setPasswordEncoder(passwordEncoder());
        return auth;
    }
}