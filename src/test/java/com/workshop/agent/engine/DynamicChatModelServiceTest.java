package com.workshop.agent.engine;

import com.workshop.agent.config.ApiKeyResolver;
import com.workshop.agent.config.GeminiProperties;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DynamicChatModelServiceTest {

    private DynamicChatModelService service;

    @BeforeEach
    void setUp() {
        GeminiProperties props = new GeminiProperties();
        props.setApiKey("test-key");
        props.setModelName("gemini-3.7-flash");
        ApiKeyResolver.ResolvedKey resolvedKey = new ApiKeyResolver.ResolvedKey("test-key", "Test", true);
        service = new DynamicChatModelService(props, resolvedKey);
    }

    @Test
    void testInitialAvailableModels() {
        List<String> models = service.getAvailableModels();
        assertTrue(models.contains("gemini-3.7-flash"));
        assertTrue(models.contains("gemini-2.5-flash"));
        assertTrue(models.contains("gemini-2.0-flash-thinking-exp-01-21"));
    }

    @Test
    void testRegisterCustomModelString() {
        String customModel = "gemini-experimental-custom-v1";
        assertFalse(service.getAvailableModels().contains(customModel));

        service.registerModel(customModel);
        assertTrue(service.getAvailableModels().contains(customModel));
    }

    @Test
    void testGetModelForArbitraryString() {
        ChatModel model1 = service.getModel("gemini-custom-future-9.0");
        assertNotNull(model1);
        assertTrue(service.getAvailableModels().contains("gemini-custom-future-9.0"));

        ChatModel model2 = service.getModel("gemini-custom-future-9.0");
        assertSame(model1, model2, "Model instance should be cached and reused across requests");
    }
}
