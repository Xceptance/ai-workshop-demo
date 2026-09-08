package com.workshop.agent.model;

/**
 * Represents the distinct lifecycle stages of an AI Agent ReAct execution loop.
 */
public enum StepType {
    USER_INPUT("User Query", "fa-user", "border-primary"),
    CONTEXT_ASSEMBLY("Context Assembly & Memory", "fa-layer-group", "border-secondary"),
    MODEL_REQUEST("Model Request Payload", "fa-paper-plane", "border-info"),
    MODEL_THINKING("Model Reasoning & Thought", "fa-brain", "border-warning"),
    TOOL_CALL_REQUEST("Tool Selection & JSON Args", "fa-wrench", "border-warning"),
    TOOL_EXECUTION("Java Method Execution", "fa-gear", "border-danger"),
    TOOL_OBSERVATION("Observation / Tool Result", "fa-clipboard-check", "border-info"),
    MCP_HANDSHAKE("MCP Server Handshake", "fa-handshake", "border-purple"),
    MCP_DISCOVERY("MCP Tool Discovery", "fa-cubes", "border-purple"),
    MCP_TOOL_CALL("MCP Wire Tool Call", "fa-network-wired", "border-purple"),
    MCP_TOOL_RESULT("MCP Wire Tool Result", "fa-arrow-down-short-wide", "border-info"),
    FINAL_SYNTHESIS("Final Response Synthesis", "fa-check-circle", "border-success"),
    AGENT_ERROR("Agent Error / Retry", "fa-triangle-exclamation", "border-danger");

    private final String label;
    private final String icon;
    private final String borderClass;

    StepType(String label, String icon, String borderClass) {
        this.label = label;
        this.icon = icon;
        this.borderClass = borderClass;
    }

    public String getLabel() {
        return label;
    }

    public String getIcon() {
        return icon;
    }

    public String getBorderClass() {
        return borderClass;
    }
}
