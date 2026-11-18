package com.searchforge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Main entry point for the SearchForge backend application.
 *
 * @SpringBootApplication encapsulates:
 * - @Configuration: Tags the class as a source of bean definitions.
 * - @EnableAutoConfiguration: Tells Spring Boot to start adding beans based on classpath settings.
 * - @ComponentScan: Tells Spring to look for other components, configurations, and services in this package and sub-packages.
 *
 * @ConfigurationPropertiesScan automatically discovers and registers @ConfigurationProperties
 * records/classes without needing manual @EnableConfigurationProperties declarations.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class SearchForgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(SearchForgeApplication.class, args);
    }
}
