package com.workshop.randomnames;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * ============================================================================
 * Model Context Protocol (MCP) Demo Server: Random Names Service
 * ============================================================================
 *
 * Architecture & Purpose:
 * -----------------------
 * This standalone Spring Boot service acts as an out-of-process MCP Server
 * operating on HTTP port 9000. It is engineered specifically for the AI Mini-Agent
 * Workshop to demonstrate the three core primitives of the Model Context Protocol:
 *
 *   1. Tools     - Dynamic remote execution invoked by the LLM (tools/call).
 *   2. Resources - Passive, readable contextual data addressable by URI (resources/read).
 *   3. Prompts   - Parameterized reusable templates guiding interactions (prompts/get).
 *
 * Why This Proves MCP Tool Calling:
 * ---------------------------------
 * When users ask an AI agent "Pick a random workshop participant", a model relying
 * solely on its internal neural weights would fabricate (hallucinate) a name from
 * general internet culture. In contrast, by integrating this external MCP server,
 * the agent discovers our tools, issues an out-of-band JSON-RPC 2.0 request to port 9000,
 * and receives a real participant from our maintained pool of 10 attendees.
 *
 * Spring AI 2 MCP Under the Hood:
 * --------------------------------
 * With 'spring-ai-starter-mcp-server-webmvc' on the classpath, Spring AI automatically:
 *   - Configures the HTTP Streamable MCP endpoint at '/mcp'.
 *   - Scans the Spring ApplicationContext for beans annotated with @McpTool,
 *     @McpResource, and @McpPrompt.
 *   - Auto-generates standard JSON Schema definitions for every exposed method parameter.
 *   - Manages JSON-RPC 2.0 message parsing, SSE (Server-Sent Events) transport framing,
 *     and session life-cycles.
 *
 * Code Style Note:
 * ----------------
 * In accordance with workshop coding guidelines, all classes in this package follow
 * the Allman style (opening braces placed on their own line).
 */
@SpringBootApplication
public class RandomNamesServerApplication
{
    /**
     * Bootstraps the embedded Tomcat server on port 9000 (configurable via SERVER_PORT).
     *
     * @param args Command-line arguments passed to the Spring application.
     */
    public static void main(String[] args)
    {
        SpringApplication.run(RandomNamesServerApplication.class, args);
    }

    /**
     * Configures the Jackson ObjectMapper used for JSON-RPC serialization,
     * REST endpoints, and ISO-8601 date formatting.
     *
     * @return Configured ObjectMapper instance with Java 8 time module registered.
     */
    @Bean
    public ObjectMapper objectMapper()
    {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
