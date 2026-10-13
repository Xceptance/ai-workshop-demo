package com.workshop.randomnames.mcp;

import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.stereotype.Component;

/**
 * MCP Prompts Concept: Pre-Packaged Templates
 * Pre-configured prompt workflows declared by the server that users or host UIs
 * can invoke with parameters to guide the LLM's interactions with tools and resources.
 */
@Component
public class RandomNamesPrompts {

    @McpPrompt(
            name = "pick_next_presenter",
            title = "Pick Next Workshop Presenter",
            description = "Prompt template guiding the AI agent to invoke get_random_name and introduce the next presenter fairly"
    )
    public String pickNextPresenter(
            @McpArg(name = "topic", description = "The workshop topic to present", required = false) String topic
    ) {
        String effectiveTopic = (topic != null && !topic.isBlank()) ? topic : "Hands-on MCP Tool Calling";
        return String.format(
                "You are facilitating the AI Mini-Agent Workshop. " +
                "First, call the 'get_random_name' tool to select a participant from the 10-person pool. " +
                "Then, announce the chosen person enthusiastically to present the topic '%s'. " +
                "Highlight to the audience that the selection was made genuinely by the external MCP server " +
                "on port 9000 rather than being generated or biased by the LLM.",
                effectiveTopic
        );
    }

    @McpPrompt(
            name = "setup_pair_programming",
            title = "Setup Pair Programming Team",
            description = "Prompt template guiding the AI agent to assign a Driver and Navigator using pick_pair_programming_duo"
    )
    public String setupPairProgramming(
            @McpArg(name = "taskName", description = "Name or focus of the programming challenge", required = false) String taskName
    ) {
        String effectiveTask = (taskName != null && !taskName.isBlank()) ? taskName : "Refactoring LRUClockMap with TDD";
        return String.format(
                "Facilitate a pair programming session for the exercise: '%s'. " +
                "Call the 'pick_pair_programming_duo' tool to pick two distinct workshop participants. " +
                "Designate the first as Driver (typing the code) and the second as Navigator (reviewing and thinking ahead). " +
                "Provide 3 clear rules for their pair session.",
                effectiveTask
        );
    }

    @McpPrompt(
            name = "inspect_mcp_capabilities",
            title = "Explain MCP Primitives",
            description = "Prompt template prompting the model to explain Tools, Resources, and Prompts using this server as an example"
    )
    public String inspectMcpCapabilities() {
        return "Explain the three MCP primitives implemented in the 'random-names-server' (port 9000): " +
               "1. Tools: 'get_random_name' and 'list_workshop_names' for dynamic code execution. " +
               "2. Resources: 'names://participants/list' for contextual JSON data. " +
               "3. Prompts: 'pick_next_presenter' for pre-packaged workflow templates. " +
               "Contrast how each of these three primitives serves a distinct purpose in AI agent architectures.";
    }
}
