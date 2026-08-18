package com.workshop.agent.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Educational Tool: Web Search and Mock Knowledge Base
 *
 * Demonstrates how agents retrieve external knowledge via simulated or real web calls.
 */
@Component
public class WebSearchMockTool {

    private static final Logger log = LoggerFactory.getLogger(WebSearchMockTool.class);
    private static final Map<String, String> KNOWLEDGE_CACHE = new HashMap<>();

    static {
        KNOWLEDGE_CACHE.put("langchain4j", """
                LangChain4j is an open-source Java library designed to simplify the integration of AI LLMs into Java applications.
                Key features include:
                - Support for OpenAI, Google Gemini, Anthropic, Ollama, HuggingFace, etc.
                - Tool and Function Calling abstractions via @Tool and AiServices.
                - RAG (Retrieval-Augmented Generation) pipelines and embedding stores.
                - Structured outputs, chat memory, and streaming responses.
                """);
        KNOWLEDGE_CACHE.put("gemini 3.7", """
                Gemini 3.7 Flash is Google's state-of-the-art hybrid reasoning model designed for speed, coding, and multi-step agentic workflows.
                It features native tool calling, large context windows (up to 1M+ tokens), and enhanced instruction following.
                """);
        KNOWLEDGE_CACHE.put("agent react pattern", """
                The ReAct (Reasoning + Acting) pattern enables LLMs to solve complex tasks through interleaved steps:
                1. Thought: Model reflects on current state and decides on a sub-goal.
                2. Action: Model selects a tool and generates structured JSON arguments.
                3. Observation: Execution runtime runs the tool and returns output to the model.
                4. Repeat until goal is achieved or max iterations reached.
                """);
        KNOWLEDGE_CACHE.put("weather", """
                Simulated Global Weather Report:
                - Berlin, DE: 21°C, Partly Cloudy, Humidity 55%
                - San Francisco, USA: 17°C, Sunny with coastal breeze
                - Tokyo, JP: 26°C, Clear Sky
                - London, UK: 19°C, Light Showers
                """);
    }

    @Tool("Searches an external knowledge base or web for current topics, definitions, or weather.")
    public String searchWeb(
            @P("The query or search keywords") String query
    ) {
        log.info("[TOOL:WebSearchMock] searchWeb(query={})", query);
        String lowerQuery = query.toLowerCase();

        for (Map.Entry<String, String> entry : KNOWLEDGE_CACHE.entrySet()) {
            if (lowerQuery.contains(entry.getKey())) {
                return "Search Results for '" + query + "':\n\n" + entry.getValue();
            }
        }

        // Generic mock search fallback
        return String.format("""
                Search Results for '%s':
                1. [Simulated Knowledge Portal] Overview and documentation regarding %s.
                2. [Community Forum] Recent discussions and implementation tips for %s.
                (Note: This is simulated search data returned by the educational mock search tool).
                """, query, query, query);
    }

    @Tool("Fetches the text content from a public web URL via HTTP GET.")
    public String fetchUrlContent(
            @P("The full HTTP or HTTPS URL to fetch") String url
    ) {
        log.info("[TOOL:WebSearchMock] fetchUrlContent(url={})", url);
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return "Invalid URL schema. URL must start with http:// or https://";
        }

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "AI-Mini-Agent-Workshop/1.0")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();
            if (body.length() > 2000) {
                body = body.substring(0, 2000) + "\n\n... [TRUNCATED: Showing first 2000 characters]";
            }
            return String.format("HTTP %d Response from %s:\n\n%s", response.statusCode(), url, body);
        } catch (Exception e) {
            return "Failed to fetch URL '" + url + "': " + e.getMessage();
        }
    }
}
