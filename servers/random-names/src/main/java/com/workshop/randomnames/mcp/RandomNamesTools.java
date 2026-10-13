package com.workshop.randomnames.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.workshop.randomnames.domain.NameService;
import com.workshop.randomnames.domain.RandomNameResult;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ============================================================================
 * MCP Concept 1: Tools (Dynamic Remote Execution)
 * ============================================================================
 *
 * What are MCP Tools?
 * -------------------
 * In the Model Context Protocol (MCP), "Tools" represent callable functions with
 * typed parameter schemas that an AI agent can discover and invoke dynamically
 * at runtime.
 *
 * How Tool Calling Works Under the Hood:
 * --------------------------------------
 * 1. Discovery (tools/list):
 *    When an agent connects to this server over HTTP SSE at '/mcp', it sends a
 *    JSON-RPC 2.0 request:
 *      {"jsonrpc":"2.0", "id": 1, "method": "tools/list", "params": {}}
 *    Spring AI inspects this class, extracts method metadata, parameter names,
 *    descriptions, and JSON Schema types, and returns the tool registry.
 *
 * 2. Model Selection:
 *    When the user asks a question, the LLM receives these tool declarations.
 *    If the model decides it needs to execute an action (e.g. pick a name, add an
 *    attendee, or filter by letter), it generates a structured tool call response:
 *      {"name": "get_random_name", "arguments": {"exclude": "Rene"}}
 *
 * 3. Execution (tools/call):
 *    The MCP Client dispatches the call to port 9000:
 *      {"jsonrpc":"2.0", "id": 2, "method": "tools/call",
 *       "params": {"name": "get_random_name", "arguments": {"exclude": "Rene"}}}
 *    The corresponding Java method executes locally, and its text or JSON return value
 *    is delivered back to the LLM as an observation step.
 *
 * Why Dual Annotations (@McpTool and @Tool)?
 * ------------------------------------------
 * Spring AI provides both '@McpTool' (specialized for MCP servers) and the general
 * Spring AI '@Tool' annotation. Decorating with both ensures maximum compatibility
 * across both the MCP JSON-RPC protocol bridge and any internal Spring AI tool registries.
 */
@Component
public class RandomNamesTools
{
    private final NameService nameService;
    private final ObjectMapper objectMapper;

    /**
     * Constructs the MCP tools component with injected domain services.
     *
     * @param nameService  Thread-safe service managing attendee names.
     * @param objectMapper Jackson serializer configured for formatted output.
     */
    public RandomNamesTools(NameService nameService, ObjectMapper objectMapper)
    {
        this.nameService = nameService;
        this.objectMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * MCP Tool: Selects a single random participant from the pool.
     *
     * <p>Educational Note for Workshop Attendees:
     * This is the flagship demo tool. Its formatted response explicitly identifies
     * the remote server name ("random-names-server") and port (9000). When you ask the
     * agent "Who should present next?", observe the step inspector drawer on the right:
     * you will see the exact JSON-RPC tools/call wire message.
     *
     * @param exclude Optional participant name to exclude (e.g. current presenter).
     * @return Formatted confirmation string containing the selected name and audit details.
     */
    @McpTool(
            name = "get_random_name",
            description = "Select and return a random name from the fixed list of 10 workshop participants (Alex, Rene, Ray, Jane, Katja, Katrin, Johannes, Bernd, Dude, Mario). Demonstrates external MCP tool execution instead of LLM hallucination."
    )
    @Tool(
            name = "get_random_name",
            description = "Select and return a random name from the fixed list of 10 workshop participants (Alex, Rene, Ray, Jane, Katja, Katrin, Johannes, Bernd, Dude, Mario). Demonstrates external MCP tool execution instead of LLM hallucination."
    )
    public String getRandomName(
            @McpToolParam(description = "Optional name to exclude from the random selection (e.g. current speaker).", required = false)
            @ToolParam(description = "Optional name to exclude from the random selection (e.g. current speaker).", required = false) String exclude
    )
    {
        try
        {
            RandomNameResult result = nameService.selectRandomName(exclude);
            return String.format(
                    "[MCP Server: random-names-server (Port 9000)] Selected random name: '%s' (from pool of %d participants: %s)",
                    result.selectedName(),
                    result.poolSize(),
                    String.join(", ", result.pool())
            );
        }
        catch (Exception e)
        {
            return "Error selecting random name: " + e.getMessage();
        }
    }

    /**
     * MCP Tool: Retrieves the complete list of all currently registered workshop attendees.
     *
     * @return Formatted summary showing the count and names of all participants.
     */
    @McpTool(
            name = "list_workshop_names",
            description = "Retrieve the complete list of all registered workshop participants configured in the random-names MCP server."
    )
    @Tool(
            name = "list_workshop_names",
            description = "Retrieve the complete list of all registered workshop participants configured in the random-names MCP server."
    )
    public String listWorkshopNames()
    {
        try
        {
            List<String> names = nameService.getNames();
            return String.format(
                    "[MCP Server: random-names-server (Port 9000)] All %d workshop participants: %s",
                    names.size(),
                    String.join(", ", names)
            );
        }
        catch (Exception e)
        {
            return "Error retrieving participant list: " + e.getMessage();
        }
    }

    /**
     * MCP Tool: Picks two distinct participants for hands-on pair programming.
     *
     * @return Text designating the Driver (keyboard coder) and Navigator (reviewer).
     */
    @McpTool(
            name = "pick_pair_programming_duo",
            description = "Randomly pick two distinct participants from the pool to form a Driver and Navigator pair for hands-on exercises."
    )
    @Tool(
            name = "pick_pair_programming_duo",
            description = "Randomly pick two distinct participants from the pool to form a Driver and Navigator pair for hands-on exercises."
    )
    public String pickPairProgrammingDuo()
    {
        try
        {
            List<String> pair = nameService.selectPair();
            return String.format(
                    "[MCP Server: random-names-server (Port 9000)] Selected pair: Driver = '%s', Navigator = '%s' (from pool of %d participants)",
                    pair.get(0), pair.get(1), nameService.getNamesCount()
            );
        }
        catch (Exception e)
        {
            return "Error picking pair duo: " + e.getMessage();
        }
    }

    /**
     * MCP Tool: Dynamically adds a new participant name to the workshop pool.
     *
     * <p>Demonstrates State Mutation via MCP:
     * Unlike purely read-only tools, MCP tools can mutate server-side domain state.
     * When the agent calls this tool, subsequent calls to 'get_random_name' or
     * 'list_workshop_names' immediately incorporate the newly added attendee.
     *
     * @param name The participant's name to add.
     * @return Confirmation message indicating success or duplicate detection.
     */
    @McpTool(
            name = "add_workshop_name",
            description = "Add a new participant name to the workshop participant pool."
    )
    @Tool(
            name = "add_workshop_name",
            description = "Add a new participant name to the workshop participant pool."
    )
    public String addWorkshopName(
            @McpToolParam(description = "The participant name to add to the workshop pool.", required = true)
            @ToolParam(description = "The participant name to add to the workshop pool.", required = true) String name
    )
    {
        try
        {
            if (name == null || name.trim().isBlank())
            {
                return "[MCP Server: random-names-server (Port 9000)] Error: Participant name cannot be blank.";
            }
            boolean added = nameService.addName(name);
            if (added)
            {
                return String.format(
                        "[MCP Server: random-names-server (Port 9000)] Successfully added '%s'. Participant pool now has %d names: %s",
                        name.trim(), nameService.getNamesCount(), String.join(", ", nameService.getNames())
                );
            }
            else
            {
                return String.format(
                        "[MCP Server: random-names-server (Port 9000)] Participant '%s' already exists in the pool (%d names: %s).",
                        name.trim(), nameService.getNamesCount(), String.join(", ", nameService.getNames())
                );
            }
        }
        catch (Exception e)
        {
            return "Error adding participant name: " + e.getMessage();
        }
    }

    /**
     * MCP Tool: Removes an existing participant from the workshop pool.
     *
     * @param name The name of the participant to remove.
     * @return Confirmation of removal or warning if the name was not in the pool.
     */
    @McpTool(
            name = "remove_workshop_name",
            description = "Remove a participant name from the workshop participant pool."
    )
    @Tool(
            name = "remove_workshop_name",
            description = "Remove a participant name from the workshop participant pool."
    )
    public String removeWorkshopName(
            @McpToolParam(description = "The participant name to remove from the workshop pool.", required = true)
            @ToolParam(description = "The participant name to remove from the workshop pool.", required = true) String name
    )
    {
        try
        {
            if (name == null || name.trim().isBlank())
            {
                return "[MCP Server: random-names-server (Port 9000)] Error: Participant name cannot be blank.";
            }
            boolean removed = nameService.removeName(name);
            if (removed)
            {
                return String.format(
                        "[MCP Server: random-names-server (Port 9000)] Successfully removed '%s'. Participant pool now has %d names: %s",
                        name.trim(), nameService.getNamesCount(), String.join(", ", nameService.getNames())
                );
            }
            else
            {
                return String.format(
                        "[MCP Server: random-names-server (Port 9000)] Participant '%s' was not found in the pool of %d names: %s",
                        name.trim(), nameService.getNamesCount(), String.join(", ", nameService.getNames())
                );
            }
        }
        catch (Exception e)
        {
            return "Error removing participant name: " + e.getMessage();
        }
    }

    /**
     * MCP Tool: Filters participant names by their initial letter or prefix.
     *
     * <p>Search Example:
     * Calling with 'K' or 'k' returns ["Katja", "Katrin"].
     *
     * @param letter The letter or prefix to filter by (e.g. 'A', 'K', 'Jo').
     * @return Formatted listing of all matching attendees.
     */
    @McpTool(
            name = "search_names_by_letter",
            description = "Search and filter workshop participant names by their starting letter or prefix (case-insensitive)."
    )
    @Tool(
            name = "search_names_by_letter",
            description = "Search and filter workshop participant names by their starting letter or prefix (case-insensitive)."
    )
    public String searchNamesByLetter(
            @McpToolParam(description = "The starting letter or prefix to search for (e.g. 'A', 'B', 'Ka').", required = true)
            @ToolParam(description = "The starting letter or prefix to search for (e.g. 'A', 'B', 'Ka').", required = true) String letter
    )
    {
        try
        {
            if (letter == null || letter.trim().isBlank())
            {
                return "[MCP Server: random-names-server (Port 9000)] Error: Search letter/prefix cannot be blank.";
            }
            List<String> matches = nameService.searchByStartingLetter(letter);
            if (!matches.isEmpty())
            {
                return String.format(
                        "[MCP Server: random-names-server (Port 9000)] Found %d participant(s) starting with '%s': %s",
                        matches.size(), letter.trim(), String.join(", ", matches)
                );
            }
            else
            {
                return String.format(
                        "[MCP Server: random-names-server (Port 9000)] No participants found starting with '%s'. Current pool (%d names): %s",
                        letter.trim(), nameService.getNamesCount(), String.join(", ", nameService.getNames())
                );
            }
        }
        catch (Exception e)
        {
            return "Error searching participants by letter: " + e.getMessage();
        }
    }
}
