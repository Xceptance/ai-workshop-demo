package com.workshop.randomnames.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.workshop.randomnames.domain.NameService;
import com.workshop.randomnames.domain.RandomNameResult;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * ============================================================================
 * MCP Concept 2: Resources (Passive Contextual Data)
 * ============================================================================
 *
 * What are MCP Resources?
 * -----------------------
 * In the Model Context Protocol, "Resources" are passive, read-only data sources
 * identified by uniform resource identifiers (URIs). Unlike Tools (which execute
 * dynamic actions that may take arbitrary input arguments and mutate state),
 * Resources function analogously to GET endpoints or file descriptors.
 *
 * Key Characteristics of Resources:
 * ---------------------------------
 * 1. Addressing: Every resource has a unique URI scheme (e.g. 'names://participants/list').
 * 2. MIME Typing: Resources declare their payload content type (e.g. 'application/json'
 *    or 'text/plain'), informing clients and models how to interpret the byte contents.
 * 3. Discoverability: Clients query 'resources/list' to learn what documents or contextual
 *    datasets are available from the connected server.
 * 4. Reading: Clients fetch the body using 'resources/read' with the URI parameter.
 *
 * Tools vs. Resources in Agent Design:
 * -----------------------------------
 * - Use a Tool when the agent needs to perform an action (e.g. 'calculate()',
 *   'add_workshop_name()', or 'search_names_by_letter()').
 * - Use a Resource when the agent needs background domain knowledge, reference data,
 *   or configuration snapshots (e.g. reading 'names://participants/stats' to understand
 *   pool demographics without invoking a computational tool).
 */
@Component
public class RandomNamesResources
{
    private final NameService nameService;
    private final ObjectMapper objectMapper;

    /**
     * Constructs the MCP resources component with domain service and serializer dependencies.
     *
     * @param nameService  Domain service managing participant names.
     * @param objectMapper Jackson serializer configured for pretty-printed JSON.
     */
    public RandomNamesResources(NameService nameService, ObjectMapper objectMapper)
    {
        this.nameService = nameService;
        this.objectMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * MCP Resource: Read-only JSON view of the entire workshop participant pool.
     *
     * <p>URI: {@code names://participants/list}
     *
     * @return Formatted JSON string containing the count, port, and participant array.
     */
    @McpResource(
            uri = "names://participants/list",
            name = "Workshop Participants List",
            description = "Complete JSON list of all registered workshop participants",
            mimeType = "application/json"
    )
    public String getParticipantsList()
    {
        try
        {
            return objectMapper.writeValueAsString(Map.of(
                    "source", "random-names-server",
                    "port", 9000,
                    "count", nameService.getNamesCount(),
                    "participants", nameService.getNames()
            ));
        }
        catch (Exception e)
        {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    /**
     * MCP Resource: Dynamic instantaneous snapshot of a single random participant.
     *
     * <p>URI: {@code names://participants/random}
     *
     * @return JSON serialization of an immutable {@link RandomNameResult}.
     */
    @McpResource(
            uri = "names://participants/random",
            name = "Live Random Participant Snapshot",
            description = "Dynamic snapshot selecting a single random participant on read",
            mimeType = "application/json"
    )
    public String getRandomParticipantSnapshot()
    {
        try
        {
            RandomNameResult result = nameService.selectRandomName(null);
            return objectMapper.writeValueAsString(result);
        }
        catch (Exception e)
        {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    /**
     * MCP Resource: Statistical summary of the workshop pool.
     *
     * <p>URI: {@code names://participants/stats}
     *
     * @return JSON string detailing pool cardinality and lexicographical bounds.
     */
    @McpResource(
            uri = "names://participants/stats",
            name = "Workshop Pool Statistics",
            description = "Statistical metadata regarding the workshop participant pool",
            mimeType = "application/json"
    )
    public String getParticipantStats()
    {
        try
        {
            return objectMapper.writeValueAsString(Map.of(
                    "serverName", "random-names-server",
                    "port", 9000,
                    "poolSize", nameService.getNamesCount(),
                    "alphabeticalFirst", nameService.getNames().stream().sorted().findFirst().orElse(""),
                    "alphabeticalLast", nameService.getNames().stream().sorted((a, b) -> b.compareTo(a)).findFirst().orElse("")
            ));
        }
        catch (Exception e)
        {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }
}
