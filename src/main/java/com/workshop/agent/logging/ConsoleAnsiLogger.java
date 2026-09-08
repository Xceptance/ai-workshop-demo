package com.workshop.agent.logging;

import com.workshop.agent.model.AgentStepEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Pretty-prints real-time agent execution events to the console with ANSI styling
 * for live workshop demonstrations and terminal debugging.
 */
@Component
public class ConsoleAnsiLogger {

    private static final Logger log = LoggerFactory.getLogger("AGENT_CONSOLE");

    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String CYAN = "\u001B[36m";
    private static final String YELLOW = "\u001B[33m";
    private static final String GREEN = "\u001B[32m";
    private static final String MAGENTA = "\u001B[35m";
    private static final String RED = "\u001B[31m";
    private static final String BLUE = "\u001B[34m";

    public void logStep(AgentStepEvent event) {
        String color = switch (event.type()) {
            case USER_INPUT -> CYAN;
            case CONTEXT_ASSEMBLY -> BLUE;
            case MODEL_REQUEST -> MAGENTA;
            case MODEL_THINKING -> YELLOW;
            case TOOL_CALL_REQUEST -> YELLOW;
            case TOOL_EXECUTION -> RED;
            case TOOL_OBSERVATION -> GREEN;
            case MCP_HANDSHAKE, MCP_DISCOVERY, MCP_TOOL_CALL -> MAGENTA;
            case MCP_TOOL_RESULT -> GREEN;
            case FINAL_SYNTHESIS -> GREEN + BOLD;
            case AGENT_ERROR -> RED + BOLD;
            default -> BLUE;
        };

        String banner = String.format("%s┌── [STEP %d: %s] %s %s", color, event.stepNumber(), event.type(), event.title(), RESET);
        log.info(banner);

        if (event.summary() != null && !event.summary().isBlank()) {
            log.info("{}│  Summary: {}{}", color, event.summary(), RESET);
        }

        if (event.tokenStats() != null && event.tokenStats().totalTokens() > 0) {
            log.info("{}│  Tokens: {} (Input: {}, Output: {}, Cost: {}, Latency: {}){}",
                    color,
                    event.tokenStats().totalTokens(),
                    event.tokenStats().inputTokens(),
                    event.tokenStats().outputTokens(),
                    event.tokenStats().formattedCost(),
                    event.tokenStats().formattedDuration(),
                    RESET);
        }

        log.info("{}└──{}", color, RESET);
    }
}
