package com.workshop.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * AI Mini-Agent Workshop - Main Application Entry Point
 *
 * An educational Spring Boot application designed to demonstrate and demystify
 * the internal mechanics of AI Agents using LangChain4j, Google Gemini, and HTMX.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class MiniAgentWorkshopApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniAgentWorkshopApplication.class, args);
    }
}
