package com.workshop.agent.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentSessionTest {

    @Test
    @DisplayName("Tools and MCP servers are enabled by default upon session creation")
    void testDefaultEnablementState() {
        AgentSession session = new AgentSession("sess-1", "Test Session", "gemini-3.8-flash", "System Prompt", 0.2);

        assertThat(session.isToolEnabled("calculate")).isTrue();
        assertThat(session.isToolEnabled("get_system_info")).isTrue();
        assertThat(session.isMcpServerEnabled("local-mcp-server")).isTrue();
        assertThat(session.isMcpServerEnabled("open-meteo-weather")).isTrue();
        assertThat(session.isMcpToolEnabled("get_weather")).isTrue();

        assertThat(session.getDisabledTools()).isEmpty();
        assertThat(session.getDisabledMcpServers()).isEmpty();
        assertThat(session.getDisabledMcpTools()).isEmpty();
    }

    @Test
    @DisplayName("Disabling and re-enabling tools and servers updates state correctly")
    void testToggleToolsAndServers() {
        AgentSession session = new AgentSession("sess-1", "Test Session", "gemini-3.8-flash", "System Prompt", 0.2);

        session.setToolEnabled("calculate", false);
        assertThat(session.isToolEnabled("calculate")).isFalse();
        assertThat(session.getDisabledTools()).containsExactly("calculate");

        session.setToolEnabled("calculate", true);
        assertThat(session.isToolEnabled("calculate")).isTrue();
        assertThat(session.getDisabledTools()).isEmpty();

        session.setMcpServerEnabled("open-meteo-weather", false);
        assertThat(session.isMcpServerEnabled("open-meteo-weather")).isFalse();
        assertThat(session.getDisabledMcpServers()).containsExactly("open-meteo-weather");

        session.setMcpToolEnabled("get_weather", false);
        assertThat(session.isMcpToolEnabled("get_weather")).isFalse();
        assertThat(session.getDisabledMcpTools()).containsExactly("get_weather");
    }

    @Test
    @DisplayName("Enablement state is strictly session-isolated")
    void testSessionIsolation() {
        AgentSession sessionA = new AgentSession("sess-a", "Session A", "gemini-3.8-flash", "System Prompt", 0.2);
        AgentSession sessionB = new AgentSession("sess-b", "Session B", "gemini-3.8-flash", "System Prompt", 0.2);

        sessionA.setToolEnabled("calculate", false);
        sessionA.setMcpServerEnabled("local-mcp-server", false);

        assertThat(sessionA.isToolEnabled("calculate")).isFalse();
        assertThat(sessionA.isMcpServerEnabled("local-mcp-server")).isFalse();

        // Session B must remain unaffected
        assertThat(sessionB.isToolEnabled("calculate")).isTrue();
        assertThat(sessionB.isMcpServerEnabled("local-mcp-server")).isTrue();
        assertThat(sessionB.getDisabledTools()).isEmpty();
        assertThat(sessionB.getDisabledMcpServers()).isEmpty();
    }

    @Test
    @DisplayName("clearHistory does not wipe out tool configuration settings")
    void testClearHistoryPreservesToolSettings() {
        AgentSession session = new AgentSession("sess-1", "Test Session", "gemini-3.8-flash", "System Prompt", 0.2);
        session.setToolEnabled("inspect_file", false);
        session.setMcpServerEnabled("energy-market-server", false);

        session.addMessage(ChatMessageDto.user("m1", "Hello"));
        session.clearHistory();

        assertThat(session.getMessages()).isEmpty();
        assertThat(session.isToolEnabled("inspect_file")).isFalse();
        assertThat(session.isMcpServerEnabled("energy-market-server")).isFalse();
    }
}
