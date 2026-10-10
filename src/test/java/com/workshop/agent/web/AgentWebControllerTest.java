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
                .andExpect(model().attribute("showReasoning", true));
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
}
