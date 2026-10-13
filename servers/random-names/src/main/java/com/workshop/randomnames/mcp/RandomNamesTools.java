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
                    "[MCP Server: random-names-server (Port 9000)] Selected pair: Driver = '%s', Navigator = '%s' (from pool of 10 participants)",
                    pair.get(0), pair.get(1)
            );
        } catch (Exception e) {
            return "Error picking pair duo: " + e.getMessage();
        }
    }
}
