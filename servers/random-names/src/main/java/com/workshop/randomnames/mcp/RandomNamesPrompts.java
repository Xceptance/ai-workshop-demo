package com.workshop.randomnames.mcp;

import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.stereotype.Component;

/**
 * ============================================================================
 * MCP Concept 3: Prompts (Pre-Packaged Interaction Templates)
 * ============================================================================
 *
 * What are MCP Prompts?
 * ---------------------
 * In the Model Context Protocol, "Prompts" are pre-configured prompt templates
 * declared and exposed by the server. They provide standardized, high-quality
 * instruction workflows that host applications or UI clients can retrieve and
 * populate with runtime arguments.
 *
 * How Prompts Differ from Tools and Resources:
 * --------------------------------------------
 * - Tools: Executed dynamically by the model during a reasoning cycle (agent-driven).
 * - Resources: Read passively to attach data into the model context (agent or client-driven).
 * - Prompts: Selected by the human user or host application to kickstart and guide
 *   the entire agent interaction (user or client-driven).
 *
 * Protocol Workflow (prompts/list and prompts/get):
 * ------------------------------------------------
 * 1. The client queries 'prompts/list' to discover available prompt workflows and
 *    their declared arguments (@McpArg).
 * 2. When the user selects a prompt (e.g. "Pick Next Presenter" with topic="MCP Wire Security"),
 *    the host client calls 'prompts/get' with the chosen parameters.
 * 3. The server renders the templated instructions, which are then passed directly
 *    into the model as the initial conversation prompt.
 */
@Component
public class RandomNamesPrompts
{
    /**
     * MCP Prompt: Workflow template for selecting and announcing the next speaker.
     *
     * @param topic Optional workshop module to be presented.
     * @return Formatted instruction text guiding the agent.
     */
    @McpPrompt(
            name = "pick_next_presenter",
            title = "Pick Next Workshop Presenter",
            description = "Prompt template guiding the AI agent to invoke get_random_name and introduce the next presenter fairly"
    )
    public String pickNextPresenter(
            @McpArg(name = "topic", description = "The workshop topic to present", required = false) String topic
    )
    {
        String effectiveTopic = (topic != null && !topic.isBlank()) ? topic : "Hands-on MCP Tool Calling";
        return String.format(
                "You are facilitating the AI Mini-Agent Workshop. " +
                "First, call the 'get_random_name' tool to select a participant from the pool. " +
                "Then, announce the chosen person enthusiastically to present the topic '%s'. " +
                "Highlight to the audience that the selection was made genuinely by the external MCP server " +
                "on port 9000 rather than being generated or biased by the LLM.",
                effectiveTopic
        );
    }

    /**
     * MCP Prompt: Workflow template for assembling a two-person pair-programming team.
     *
     * @param taskName Name or focus of the programming challenge.
     * @return Formatted instruction text guiding role assignments and team rules.
     */
    @McpPrompt(
            name = "setup_pair_programming",
            title = "Setup Pair Programming Team",
            description = "Prompt template guiding the AI agent to assign a Driver and Navigator using pick_pair_programming_duo"
    )
    public String setupPairProgramming(
            @McpArg(name = "taskName", description = "Name or focus of the programming challenge", required = false) String taskName
    )
    {
        String effectiveTask = (taskName != null && !taskName.isBlank()) ? taskName : "Refactoring LRUClockMap with TDD";
        return String.format(
                "Facilitate a pair programming session for the exercise: '%s'. " +
                "Call the 'pick_pair_programming_duo' tool to pick two distinct workshop participants. " +
                "Designate the first as Driver (typing the code) and the second as Navigator (reviewing and thinking ahead). " +
                "Provide 3 clear rules for their pair session.",
                effectiveTask
        );
    }

    /**
     * MCP Prompt: Educational prompt asking the LLM to contrast all three MCP concepts.
     *
     * @return Structured prompt instructions for educational comparison.
     */
    @McpPrompt(
            name = "inspect_mcp_capabilities",
            title = "Explain MCP Primitives",
            description = "Prompt template prompting the model to explain Tools, Resources, and Prompts using this server as an example"
    )
    public String inspectMcpCapabilities()
    {
        return "Explain the three MCP primitives implemented in the 'random-names-server' (port 9000): " +
               "1. Tools: 'get_random_name', 'list_workshop_names', 'add_workshop_name', etc., for dynamic code execution. " +
               "2. Resources: 'names://participants/list' for contextual JSON data. " +
               "3. Prompts: 'pick_next_presenter' for pre-packaged workflow templates. " +
               "Contrast how each of these three primitives serves a distinct purpose in AI agent architectures.";
    }
}
