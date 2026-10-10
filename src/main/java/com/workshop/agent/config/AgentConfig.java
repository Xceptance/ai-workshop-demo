package com.workshop.agent.config;

import com.workshop.agent.engine.MockLanguageModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GeminiThinkingConfig;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class AgentConfig {

    private static final Logger log = LoggerFactory.getLogger(AgentConfig.class);

    private final GeminiProperties geminiProperties;
    private final ApiKeyResolver apiKeyResolver;

    public AgentConfig(GeminiProperties geminiProperties, ApiKeyResolver apiKeyResolver) {
        this.geminiProperties = geminiProperties;
        this.apiKeyResolver = apiKeyResolver;
    }

    @Bean
    public com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .findAndRegisterModules()
                .disable(com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }

    @Bean
    public ApiKeyResolver.ResolvedKey resolvedApiKey() {
        return apiKeyResolver.resolveApiKey(geminiProperties.getApiKey());
    }

    @Bean
    public ChatModel chatModel(ApiKeyResolver.ResolvedKey resolvedKey) {
        if (resolvedKey.isMock()) {
            log.warn("=== ACTIVATING MOCK CHAT LANGUAGE MODEL (Offline Mode) ===");
            return new MockLanguageModel();
        }

        String modelName = geminiProperties.getModelName();
        if (modelName == null || modelName.isBlank()) {
            modelName = "gemini-3.8-flash";
        }

        log.info("=== INITIALIZING GOOGLE GEMINI CHAT MODEL ===");
        log.info("Model: {}", modelName);
        log.info("Key Source: {}", resolvedKey.source());
        log.info("Masked Key: {}", resolvedKey.maskedKey());
        log.info("Temperature: {}", geminiProperties.getTemperature());

        try {
            return GoogleAiGeminiChatModel.builder()
                    .apiKey(resolvedKey.key())
                    .modelName(modelName)
                    .temperature(geminiProperties.getTemperature())
                    .maxRetries(geminiProperties.getMaxRetries())
                    .timeout(Duration.ofSeconds(geminiProperties.getTimeoutSeconds()))
                    .logRequestsAndResponses(true)
                    .returnThinking(true)
                    .sendThinking(true)
                    .thinkingConfig(GeminiThinkingConfig.builder().includeThoughts(true).build())
                    .build();
        } catch (Exception e) {
            log.error("Failed to initialize GoogleAiGeminiChatModel: {}. Falling back to Mock model.", e.getMessage(), e);
            return new MockLanguageModel();
        }
    }
}
