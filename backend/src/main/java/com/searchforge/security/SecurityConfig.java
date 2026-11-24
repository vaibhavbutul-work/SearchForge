package com.searchforge.security;

import com.searchforge.security.ratelimit.RateLimiterRegistry;
import com.searchforge.security.ratelimit.RateLimitingFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Spring configuration class registering SearchForge API Rate Limiting and Security filters.
 */
@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    @Bean
    public RateLimiterRegistry rateLimiterRegistry(SecurityProperties properties) {
        return new RateLimiterRegistry(properties);
    }

    @Bean
    public RateLimitingFilter rateLimitingFilter(RateLimiterRegistry registry, SecurityProperties properties) {
        return new RateLimitingFilter(registry, properties);
    }

    @Bean
    public FilterRegistrationBean<RateLimitingFilter> rateLimitingFilterRegistration(RateLimitingFilter filter) {
        FilterRegistrationBean<RateLimitingFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/api/v1/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 5); // Executes before ApiKeyAuthenticationFilter
        registration.setName("searchforgeRateLimitingFilter");
        return registration;
    }

    @Bean
    public ApiKeyAuthenticationFilter apiKeyAuthenticationFilter(SecurityProperties properties) {
        return new ApiKeyAuthenticationFilter(properties);
    }

    @Bean
    public FilterRegistrationBean<ApiKeyAuthenticationFilter> apiKeyFilterRegistration(ApiKeyAuthenticationFilter filter) {
        FilterRegistrationBean<ApiKeyAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/api/v1/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.setName("searchforgeApiKeyFilter");
        return registration;
    }
}
