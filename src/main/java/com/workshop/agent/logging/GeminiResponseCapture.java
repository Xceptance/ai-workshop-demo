package com.workshop.agent.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Intercepts Gemini API responses logged by LangChain4j to capture usage metrics
 * such as cachedContentTokenCount (prompt cache hits).
 */
@Component
public class GeminiResponseCapture {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(GeminiResponseCapture.class);
    private static final ThreadLocal<Integer> LAST_CACHED_TOKENS = new ThreadLocal<>();

    private final ObjectMapper objectMapper;

    public GeminiResponseCapture(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public static int consumeLastCachedTokens() {
        Integer val = LAST_CACHED_TOKENS.get();
        LAST_CACHED_TOKENS.remove();
        return val != null ? val : 0;
    }

    public static void setLastCachedTokens(int count) {
        LAST_CACHED_TOKENS.set(count);
    }

    @PostConstruct
    public void registerAppender() {
        try {
            org.slf4j.ILoggerFactory factory = LoggerFactory.getILoggerFactory();
            if (factory instanceof LoggerContext lc) {
                Logger geminiModelLogger = lc.getLogger("dev.langchain4j.model.googleai.GoogleAiGeminiChatModel");
                geminiModelLogger.setLevel(Level.DEBUG);

                AppenderBase<ILoggingEvent> appender = new AppenderBase<>() {
                    @Override
                    protected void append(ILoggingEvent event) {
                        String msg = event.getFormattedMessage();
                        if (msg != null && msg.contains("Response from Gemini:")) {
                            int idx = msg.indexOf('{');
                            if (idx >= 0) {
                                String json = msg.substring(idx);
                                try {
                                    JsonNode root = objectMapper.readTree(json);
                                    JsonNode usage = root.path("usageMetadata");
                                    if (!usage.isMissingNode()) {
                                        int cached = usage.path("cachedContentTokenCount").asInt(0);
                                        if (cached > 0) {
                                            setLastCachedTokens(cached);
                                            log.debug("[PROMPT-CACHE-HIT] Intercepted {} cached prompt tokens from Gemini API", cached);
                                        }
                                    }
                                } catch (Exception ignored) {
                                }
                            }
                        }
                    }
                };
                appender.setContext(lc);
                appender.setName("GeminiResponseCaptureAppender");
                appender.start();
                geminiModelLogger.addAppender(appender);
                log.info("Registered GeminiResponseCaptureAppender on dev.langchain4j.model.googleai.GoogleAiGeminiChatModel");
            }
        } catch (Exception e) {
            log.warn("Could not register Gemini response capture appender: {}", e.getMessage());
        }
    }
}
