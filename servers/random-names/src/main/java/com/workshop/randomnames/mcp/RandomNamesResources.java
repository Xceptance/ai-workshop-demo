package com.workshop.randomnames.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.workshop.randomnames.domain.NameService;
import com.workshop.randomnames.domain.RandomNameResult;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MCP Resources Concept: Contextual Data
 * Passive readable data sources attached via URI that agents can read for context.
 */
@Component
public class RandomNamesResources {

    private final NameService nameService;
    private final ObjectMapper objectMapper;

    public RandomNamesResources(NameService nameService, ObjectMapper objectMapper) {
        this.nameService = nameService;
        this.objectMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
    }

    @McpResource(
            uri = "names://participants/list",
            name = "Workshop Participants List",
            description = "Complete JSON list of all 10 registered workshop participants",
            mimeType = "application/json"
    )
    public String getParticipantsList() {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "source", "random-names-server",
                    "port", 9000,
                    "count", nameService.getNamesCount(),
                    "participants", nameService.getNames()
            ));
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    @McpResource(
            uri = "names://participants/random",
            name = "Live Random Participant Snapshot",
            description = "Dynamic snapshot selecting a single random participant on read",
            mimeType = "application/json"
    )
    public String getRandomParticipantSnapshot() {
        try {
            RandomNameResult result = nameService.selectRandomName(null);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    @McpResource(
            uri = "names://participants/stats",
            name = "Workshop Pool Statistics",
            description = "Statistical metadata regarding the workshop participant pool",
            mimeType = "application/json"
    )
    public String getParticipantStats() {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "serverName", "random-names-server",
                    "port", 9000,
                    "poolSize", nameService.getNamesCount(),
                    "alphabeticalFirst", nameService.getNames().stream().sorted().findFirst().orElse(""),
                    "alphabeticalLast", nameService.getNames().stream().sorted((a, b) -> b.compareTo(a)).findFirst().orElse("")
            ));
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }
}
