package com.workshop.agent.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeminiResponseCaptureTest {

    private GeminiResponseCapture capture;

    @BeforeEach
    void setUp() {
        capture = new GeminiResponseCapture(new ObjectMapper());
        GeminiResponseCapture.consumeLastCachedTokens(); // clear state
    }

    @AfterEach
    void tearDown() {
        GeminiResponseCapture.consumeLastCachedTokens();
    }

    @Test
    void testExtractCachedTokensFromLoggingHttpClientMessage() {
        String logMessage = """
                HTTP response:
                - status code: 200
                - headers: [content-type: application/json]
                - body: {
                  "candidates": [
                    {
                      "content": { "parts": [{ "text": "Hello" }], "role": "model" },
                      "finishReason": "STOP"
                    }
                  ],
                  "usageMetadata": {
                    "promptTokenCount": 4500,
                    "candidatesTokenCount": 50,
                    "totalTokenCount": 4550,
                    "cachedContentTokenCount": 3800
                  }
                }
                """;

        GeminiResponseCapture.extractAndSetCachedTokens(logMessage);
        int cached = GeminiResponseCapture.consumeLastCachedTokens();
        assertEquals(3800, cached, "Should accurately parse cachedContentTokenCount from HTTP client log");
        assertEquals(0, GeminiResponseCapture.consumeLastCachedTokens(), "Subsequent consume should return 0");
    }

    @Test
    void testNoCachedTokensInMessageReturnsZero() {
        String logMessage = """
                HTTP response:
                - status code: 200
                - body: {
                  "candidates": [],
                  "usageMetadata": {
                    "promptTokenCount": 1054,
                    "candidatesTokenCount": 34,
                    "totalTokenCount": 1088
                  }
                }
                """;

        GeminiResponseCapture.extractAndSetCachedTokens(logMessage);
        int cached = GeminiResponseCapture.consumeLastCachedTokens();
        assertEquals(0, cached);
    }
}
