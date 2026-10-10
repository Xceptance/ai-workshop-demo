package com.workshop.agent.engine;

import com.workshop.agent.config.ApiKeyResolver;
import com.workshop.agent.config.GeminiProperties;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GeminiThinkingConfig;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages dynamic LLM model selection and instantiation at runtime.
 * Allows switching to predefined models or adding any arbitrary model string on the fly.
 */
@Service
public class DynamicChatModelService {

    private static final Logger log = LoggerFactory.getLogger(DynamicChatModelService.class);

    private final GeminiProperties geminiProperties;
    private final ApiKeyResolver.ResolvedKey resolvedKey;

    private final Map<String, ChatModel> modelCache = new ConcurrentHashMap<>();
    private final List<String> availableModels = new CopyOnWriteArrayList<>(List.of(
            "gemini-3.8-flash",
            "gemini-3.7-flash",
            "gemini-3.5-flash",
            "gemini-3.5-flash-lite",
            "gemini-3.1-flash-lite",
            "gemini-2.5-pro"
    ));

    public DynamicChatModelService(GeminiProperties geminiProperties, ApiKeyResolver.ResolvedKey resolvedKey) {
        this.geminiProperties = geminiProperties;
        this.resolvedKey = resolvedKey;

        // Ensure configured default model is present in the list
        String defaultModel = geminiProperties.getModelName();
        if (defaultModel != null && !defaultModel.isBlank() && !availableModels.contains(defaultModel)) {
            availableModels.add(0, defaultModel.trim());
        }
    }

    /**
     * Retrieves or instantiates a ChatModel for the requested model string with default thinking level.
     */
    public ChatModel getModel(String modelName) {
        return getModel(modelName, "medium");
    }

    /**
     * Retrieves or instantiates a ChatModel for the requested model string and thinking level.
     */
    public ChatModel getModel(String modelName, String thinkingLevel) {
        final String effectiveName = (modelName != null && !modelName.isBlank())
                ? modelName.trim()
                : (geminiProperties.getModelName() != null && !geminiProperties.getModelName().isBlank()
                    ? geminiProperties.getModelName().trim()
                    : "gemini-3.8-flash");

        String effectiveThinking = (thinkingLevel != null && !thinkingLevel.isBlank())
                ? thinkingLevel.trim().toLowerCase()
                : "medium";

        registerModel(effectiveName);

        if (resolvedKey.isMock()) {
            return modelCache.computeIfAbsent("mock", k -> new MockLanguageModel());
        }

        String cacheKey = effectiveName + ":" + effectiveThinking;

        return modelCache.computeIfAbsent(cacheKey, key -> {
            log.info("Instantiating dynamic GoogleAiGeminiChatModel for model: {} (thinking: {})", effectiveName, effectiveThinking);
            try {
                var builder = GoogleAiGeminiChatModel.builder()
                        .apiKey(resolvedKey.key())
                        .modelName(effectiveName)
                        .temperature(geminiProperties.getTemperature())
                        .maxRetries(geminiProperties.getMaxRetries())
                        .timeout(Duration.ofSeconds(geminiProperties.getTimeoutSeconds()))
                        .logRequestsAndResponses(true)
                        .returnThinking(true)
                        .sendThinking(true);

                if ("off".equalsIgnoreCase(effectiveThinking) || "none".equalsIgnoreCase(effectiveThinking)) {
                    builder.thinkingConfig(GeminiThinkingConfig.builder()
                            .includeThoughts(false)
                            .thinkingBudget(0)
                            .build());
                } else if ("high".equalsIgnoreCase(effectiveThinking)) {
                    builder.thinkingConfig(GeminiThinkingConfig.builder()
                            .includeThoughts(true)
                            .thinkingBudget(16384)
                            .build());
                } else {
                    // medium, low, dynamic: enable includeThoughts(true) for rich reasoning trace
                    builder.thinkingConfig(GeminiThinkingConfig.builder()
                            .includeThoughts(true)
                            .build());
                }

                return builder.build();
            } catch (Exception e) {
                log.error("Failed to instantiate Gemini model '{}': {}. Falling back to mock.", effectiveName, e.getMessage(), e);
                return new MockLanguageModel();
            }
        });
    }

    /**
     * Registers a new model name into the available models catalog if not already present.
     */
    public String registerModel(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return geminiProperties.getModelName();
        }
        String cleanName = modelName.trim();
        if (!availableModels.contains(cleanName)) {
            availableModels.add(0, cleanName);
            log.info("Registered new custom model for runtime selection: {}", cleanName);
        }
        return cleanName;
    }

    public List<String> getAvailableModels() {
        return Collections.unmodifiableList(availableModels);
    }
}
