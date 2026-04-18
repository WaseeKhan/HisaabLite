package com.expygen.config;

import com.expygen.interceptor.SessionInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    
    @Autowired
    private SessionInterceptor sessionInterceptor;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(sessionInterceptor)
                .addPathPatterns("/dashboard", "/owner/**", "/manager/**", "/cashier/**", "/profile/**",
                        "/user-profile/**", "/products/**", "/sales/**", "/purchases/**", "/insights/**",
                        "/staff/**", "/activity/**", "/support/**", "/upgrade/**", "/subscription/**")
                .excludePathPatterns("/login", "/css/**", "/js/**", "/images/**", "/favicon.png");
    }
}
