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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Intercepts Gemini API responses logged by LangChain4j to capture usage metrics
 * such as cachedContentTokenCount (prompt cache hits).
 */
@Component
public class GeminiResponseCapture {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(GeminiResponseCapture.class);
    private static final Pattern CACHED_TOKENS_PATTERN = Pattern.compile("\"cachedContentTokenCount\"\\s*:\\s*(\\d+)");
    private static final ThreadLocal<Integer> LAST_CACHED_TOKENS = new ThreadLocal<>();
    private static final AtomicInteger LAST_GLOBAL_CACHED = new AtomicInteger(0);

    private final ObjectMapper objectMapper;

    public GeminiResponseCapture(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public static int consumeLastCachedTokens() {
        Integer val = LAST_CACHED_TOKENS.get();
        LAST_CACHED_TOKENS.remove();
        if (val != null && val > 0) {
            LAST_GLOBAL_CACHED.set(0);
            return val;
        }
        return LAST_GLOBAL_CACHED.getAndSet(0);
    }

    public static void setLastCachedTokens(int count) {
        LAST_CACHED_TOKENS.set(count);
        LAST_GLOBAL_CACHED.set(count);
    }

    public static void extractAndSetCachedTokens(String message) {
        if (message == null || message.isBlank()) return;
        if (!message.contains("cachedContentTokenCount")) return;

        Matcher matcher = CACHED_TOKENS_PATTERN.matcher(message);
        if (matcher.find()) {
            try {
                int count = Integer.parseInt(matcher.group(1));
                if (count > 0) {
                    setLastCachedTokens(count);
                    log.info("[PROMPT-CACHE-HIT] Intercepted {} cached prompt tokens from Gemini API response", count);
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }

    @PostConstruct
    public void registerAppender() {
        try {
            org.slf4j.ILoggerFactory factory = LoggerFactory.getILoggerFactory();
            if (factory instanceof LoggerContext lc) {
                AppenderBase<ILoggingEvent> appender = new AppenderBase<>() {
                    @Override
                    protected void append(ILoggingEvent event) {
                        String msg = event.getFormattedMessage();
                        extractAndSetCachedTokens(msg);
                    }
                };
                appender.setContext(lc);
                appender.setName("GeminiResponseCaptureAppender");
                appender.start();

                // 1. LangChain4j 1.x / 1.22+ HTTP client logger
                Logger httpLogger = lc.getLogger("dev.langchain4j.http.client.log.LoggingHttpClient");
                httpLogger.setLevel(Level.DEBUG);
                httpLogger.addAppender(appender);

                // 2. GoogleAiGeminiChatModel logger
                Logger geminiModelLogger = lc.getLogger("dev.langchain4j.model.googleai.GoogleAiGeminiChatModel");
                geminiModelLogger.setLevel(Level.DEBUG);
                geminiModelLogger.addAppender(appender);

                // 3. Fallback parent logger
                Logger parentLogger = lc.getLogger("dev.langchain4j.http");
                parentLogger.addAppender(appender);

                log.info("Registered GeminiResponseCaptureAppender on LoggingHttpClient and GoogleAiGeminiChatModel");
            }
        } catch (Exception e) {
            log.warn("Could not register Gemini response capture appender: {}", e.getMessage());
        }
    }
}
