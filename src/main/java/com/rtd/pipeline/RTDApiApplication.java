package com.rtd.pipeline;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Spring Boot application for RTD API server.
 * Provides REST API endpoints for the occupancy analysis system.
 */
@SpringBootApplication
public class RTDApiApplication implements WebMvcConfigurer {
    
    private static final Logger logger = LoggerFactory.getLogger(RTDApiApplication.class);
    
    public static void main(String[] args) {
        logger.info("🚀 Starting RTD API Server...");
        SpringApplication.run(RTDApiApplication.class, args);
        logger.info("✅ RTD API Server started on http://localhost:8080");
    }
    
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:3000")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}