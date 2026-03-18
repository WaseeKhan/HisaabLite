package com.hisaablite.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // CSS files
        registry.addResourceHandler("/css/**")
                .addResourceLocations("classpath:/static/css/")
                .setCachePeriod(3600);
        
        // JS files
        registry.addResourceHandler("/js/**")
                .addResourceLocations("classpath:/static/js/")
                .setCachePeriod(3600);
        
        // Images
        registry.addResourceHandler("/images/**")
                .addResourceLocations("classpath:/static/images/")
                .setCachePeriod(3600);
        
        // Favicon
        registry.addResourceHandler("/favicon.*")
                .addResourceLocations("classpath:/static/")
                .setCachePeriod(3600);
        
        // Root level static files (if any)
        registry.addResourceHandler("/**.png", "/**.jpg", "/**.ico", "/**.svg")
                .addResourceLocations("classpath:/static/")
                .setCachePeriod(3600);
    }
}