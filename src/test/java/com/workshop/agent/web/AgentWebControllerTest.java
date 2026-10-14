package com.workshop.agent.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
class AgentWebControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void testIndexPageRendering() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attribute("modelName", "gemini-3.8-flash"))
                .andExpect(model().attribute("thinkingLevel", "medium"))
                .andExpect(model().attribute("showReasoning", true))
                .andExpect(model().attributeExists("systemPrompt"))
                .andExpect(model().attributeExists("defaultSystemPrompt"))
                .andExpect(model().attributeExists("activeToolCount"))
                .andExpect(model().attributeExists("totalToolCount"))
                .andExpect(model().attributeExists("activeMcpServerCount"))
                .andExpect(model().attributeExists("totalMcpServerCount"));
    }

    @Test
    void testChatEndpoint() throws Exception {
        mockMvc.perform(post("/chat")
                        .param("sessionId", "test-session")
                        .param("prompt", "What time is it?"))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/chat-panel :: chatContent"));
    }

    @Test
    void testTranscriptViewEndpoint() throws Exception {
        mockMvc.perform(get("/session/test-session/transcript/view"))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/log-viewer-modal :: logContent"));
    }

    @Test
    void testUpdateSessionSettingsForm() throws Exception {
        mockMvc.perform(post("/session/settings-session/settings")
                        .param("modelName", "gemini-3.8-flash")
                        .param("thinkingLevel", "high"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?sessionId=settings-session&mode=classic"));
    }

    @Test
    void testToggleReasoningEndpoint() throws Exception {
        mockMvc.perform(post("/session/toggle-session/toggle-reasoning")
                        .param("show", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.showReasoning").value(false));

        mockMvc.perform(post("/session/toggle-session/toggle-reasoning"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.showReasoning").value(true));
    }

    @Test
    void testUpdateSessionModelApi() throws Exception {
        mockMvc.perform(post("/api/session/api-session/model")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modelName\":\"gemini-2.5-pro\",\"thinkingLevel\":\"low\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.modelName").value("gemini-2.5-pro"))
                .andExpect(jsonPath("$.thinkingLevel").value("low"));
    }

    @Test
    void testToggleToolEndpoint() throws Exception {
        mockMvc.perform(post("/api/session/tool-toggle-sess/tools/toggle")
                        .param("toolName", "calculate")
                        .param("enabled", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.toolName").value("calculate"))
                .andExpect(jsonPath("$.enabled").value(false));

        mockMvc.perform(post("/api/session/tool-toggle-sess/tools/toggle")
                        .param("toolName", "calculate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.toolName").value("calculate"))
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void testToggleMcpServerEndpoint() throws Exception {
        mockMvc.perform(post("/api/session/mcp-toggle-sess/mcp/servers/toggle")
                        .param("serverId", "open-meteo-weather")
                        .param("enabled", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.serverId").value("open-meteo-weather"))
                .andExpect(jsonPath("$.enabled").value(false));

        mockMvc.perform(post("/api/session/mcp-toggle-sess/mcp/servers/toggle")
                        .param("serverId", "open-meteo-weather")
                        .param("enabled", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.serverId").value("open-meteo-weather"))
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void testToggleMcpToolEndpoint() throws Exception {
        mockMvc.perform(post("/api/session/mcp-tool-toggle-sess/mcp/tools/toggle")
                        .param("toolName", "get_current_weather")
                        .param("enabled", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.toolName").value("get_current_weather"))
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void testCreateNewSessionWithCustomSystemPrompt() throws Exception {
        mockMvc.perform(post("/session/new")
                        .param("mode", "mcp")
                        .param("modelName", "gemini-3.8-flash")
                        .param("thinkingLevel", "high")
                        .param("systemPrompt", "You are a custom workshop agent."))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void testUpdateSessionSettingsWithSystemPrompt() throws Exception {
        mockMvc.perform(post("/session/prompt-session/settings")
                        .param("systemPrompt", "You are a pirate agent."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?sessionId=prompt-session&mode=classic"));

        mockMvc.perform(get("/?sessionId=prompt-session"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("systemPrompt", "You are a pirate agent."));
    }

    @Test
    void testUpdateSystemPromptApi() throws Exception {
        mockMvc.perform(post("/api/session/api-prompt-sess/system-prompt")
                        .param("systemPrompt", "You are a JSON assistant."))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.systemPrompt").value("You are a JSON assistant."));
    }
}
