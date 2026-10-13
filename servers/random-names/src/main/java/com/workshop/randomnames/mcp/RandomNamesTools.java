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
 * MCP Tools Concept: Dynamic Execution
 * Functions that the AI agent can discover and invoke dynamically at runtime.
 */
@Component
public class RandomNamesTools {

    private final NameService nameService;
    private final ObjectMapper objectMapper;

    public RandomNamesTools(NameService nameService, ObjectMapper objectMapper) {
        this.nameService = nameService;
        this.objectMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
    }

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
    ) {
        try {
            RandomNameResult result = nameService.selectRandomName(exclude);
            return String.format(
                    "[MCP Server: random-names-server (Port 9000)] Selected random name: '%s' (from pool of %d participants: %s)",
                    result.selectedName(),
                    result.poolSize(),
                    String.join(", ", result.pool())
            );
        } catch (Exception e) {
            return "Error selecting random name: " + e.getMessage();
        }
    }

    @McpTool(
            name = "list_workshop_names",
            description = "Retrieve the complete list of all 10 registered workshop participants configured in the random-names MCP server."
    )
    @Tool(
            name = "list_workshop_names",
            description = "Retrieve the complete list of all 10 registered workshop participants configured in the random-names MCP server."
    )
    public String listWorkshopNames() {
        try {
            List<String> names = nameService.getNames();
            return String.format(
                    "[MCP Server: random-names-server (Port 9000)] All %d workshop participants: %s",
                    names.size(),
                    String.join(", ", names)
            );
        } catch (Exception e) {
            return "Error retrieving participant list: " + e.getMessage();
        }
    }

    @McpTool(
            name = "pick_pair_programming_duo",
            description = "Randomly pick two distinct participants from the 10-person pool to form a Driver and Navigator pair for hands-on exercises."
    )
    @Tool(
            name = "pick_pair_programming_duo",
            description = "Randomly pick two distinct participants from the 10-person pool to form a Driver and Navigator pair for hands-on exercises."
    )
    public String pickPairProgrammingDuo() {
        try {
            List<String> pair = nameService.selectPair();
            return String.format(
                    "[MCP Server: random-names-server (Port 9000)] Selected pair: Driver = '%s', Navigator = '%s' (from pool of %d participants)",
                    pair.get(0), pair.get(1), nameService.getNamesCount()
            );
        } catch (Exception e) {
            return "Error picking pair duo: " + e.getMessage();
        }
    }

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
    ) {
        try {
            if (name == null || name.trim().isBlank()) {
                return "[MCP Server: random-names-server (Port 9000)] Error: Participant name cannot be blank.";
            }
            boolean added = nameService.addName(name);
            if (added) {
                return String.format(
                        "[MCP Server: random-names-server (Port 9000)] Successfully added '%s'. Participant pool now has %d names: %s",
                        name.trim(), nameService.getNamesCount(), String.join(", ", nameService.getNames())
                );
            } else {
                return String.format(
                        "[MCP Server: random-names-server (Port 9000)] Participant '%s' already exists in the pool (%d names: %s).",
                        name.trim(), nameService.getNamesCount(), String.join(", ", nameService.getNames())
                );
            }
        } catch (Exception e) {
            return "Error adding participant name: " + e.getMessage();
        }
    }

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
    ) {
        try {
            if (name == null || name.trim().isBlank()) {
                return "[MCP Server: random-names-server (Port 9000)] Error: Participant name cannot be blank.";
            }
            boolean removed = nameService.removeName(name);
            if (removed) {
                return String.format(
                        "[MCP Server: random-names-server (Port 9000)] Successfully removed '%s'. Participant pool now has %d names: %s",
                        name.trim(), nameService.getNamesCount(), String.join(", ", nameService.getNames())
                );
            } else {
                return String.format(
                        "[MCP Server: random-names-server (Port 9000)] Participant '%s' was not found in the pool of %d names: %s",
                        name.trim(), nameService.getNamesCount(), String.join(", ", nameService.getNames())
                );
            }
        } catch (Exception e) {
            return "Error removing participant name: " + e.getMessage();
        }
    }

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
    ) {
        try {
            if (letter == null || letter.trim().isBlank()) {
                return "[MCP Server: random-names-server (Port 9000)] Error: Search letter/prefix cannot be blank.";
            }
            List<String> matches = nameService.searchByStartingLetter(letter);
            if (!matches.isEmpty()) {
                return String.format(
                        "[MCP Server: random-names-server (Port 9000)] Found %d participant(s) starting with '%s': %s",
                        matches.size(), letter.trim(), String.join(", ", matches)
                );
            } else {
                return String.format(
                        "[MCP Server: random-names-server (Port 9000)] No participants found starting with '%s'. Current pool (%d names): %s",
                        letter.trim(), nameService.getNamesCount(), String.join(", ", nameService.getNames())
                );
            }
        } catch (Exception e) {
            return "Error searching participants by letter: " + e.getMessage();
        }
    }
}
