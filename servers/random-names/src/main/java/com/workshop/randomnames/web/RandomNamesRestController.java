package com.workshop.randomnames.web;

import com.workshop.randomnames.domain.NameService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * ============================================================================
 * HTTP REST Controller: Observability & Out-of-Band Inspection
 * ============================================================================
 *
 * Why Both MCP and REST?
 * ----------------------
 * While the primary purpose of this microservice is to serve as an out-of-process
 * MCP server via the '/mcp' Streamable HTTP endpoint, having conventional REST
 * endpoints serves several critical educational and operational goals:
 *
 * 1. Immediate Human Verification:
 *    Instructors and attendees can verify server health in a standard browser
 *    or via curl ('curl http://localhost:9000/') without needing an MCP client.
 *
 * 2. Independent Ground Truth:
 *    During live workshop demos, participants can open 'http://localhost:9000/api/names'
 *    in their browser, watch an attendee get added or removed by the AI agent via MCP,
 *    and refresh the browser to verify that the agent's MCP action mutated real backend state.
 *
 * 3. Diagnostic Health Probes:
 *    Automated health checkers and integration tests can query the runtime status
 *    and advertised MCP capabilities.
 */
@RestController
public class RandomNamesRestController
{
    private final NameService nameService;

    /**
     * Constructs the REST controller with the injected domain service.
     *
     * @param nameService Service managing the workshop participant list.
     */
    public RandomNamesRestController(NameService nameService)
    {
        this.nameService = nameService;
    }

    /**
     * Health check and capability discovery endpoint.
     *
     * @return Metadata describing service status, port, protocol, and exposed MCP capabilities.
     */
    @GetMapping({"/", "/api/status"})
    public ResponseEntity<Map<String, Object>> getStatus()
    {
        return ResponseEntity.ok(Map.of(
                "service", "random-names-server",
                "status", "UP",
                "port", 9000,
                "protocol", "Model Context Protocol (MCP) Streamable HTTP",
                "mcpEndpoint", "/mcp",
                "capabilities", Map.of(
                        "tools", List.of(
                                "get_random_name",
                                "list_workshop_names",
                                "pick_pair_programming_duo",
                                "add_workshop_name",
                                "remove_workshop_name",
                                "search_names_by_letter"
                        ),
                        "resources", List.of(
                                "names://participants/list",
                                "names://participants/random",
                                "names://participants/stats"
                        ),
                        "prompts", List.of(
                                "pick_next_presenter",
                                "setup_pair_programming",
                                "inspect_mcp_capabilities"
                        )
                ),
                "participantsCount", nameService.getNamesCount(),
                "participants", nameService.getNames()
        ));
    }

    /**
     * Convenience REST endpoint for generating a random participant without an MCP client.
     *
     * @param exclude Optional participant name to exclude from the draw.
     * @return JSON response containing the selected participant and audit confirmation.
     */
    @GetMapping("/api/names/random")
    public ResponseEntity<Map<String, Object>> getRandomName(@RequestParam(value = "exclude", required = false) String exclude)
    {
        var result = nameService.selectRandomName(exclude);
        return ResponseEntity.ok(Map.of(
                "selectedName", result.selectedName(),
                "poolSize", result.poolSize(),
                "confirmation", result.confirmationMessage()
        ));
    }

    /**
     * Retrieves the entire participant list over REST.
     *
     * @return Total count and array of participant names.
     */
    @GetMapping("/api/names")
    public ResponseEntity<Map<String, Object>> getNames()
    {
        return ResponseEntity.ok(Map.of(
                "total", nameService.getNamesCount(),
                "names", nameService.getNames()
        ));
    }

    /**
     * Adds a new participant to the workshop pool via HTTP POST.
     *
     * @param name The participant's display name.
     * @return JSON response with success flag and updated pool contents.
     */
    @PostMapping("/api/names")
    public ResponseEntity<Map<String, Object>> addName(@RequestParam("name") String name)
    {
        boolean added = nameService.addName(name);
        return ResponseEntity.ok(Map.of(
                "success", added,
                "name", name != null ? name.trim() : "",
                "total", nameService.getNamesCount(),
                "names", nameService.getNames()
        ));
    }

    /**
     * Removes a participant from the workshop pool via HTTP DELETE.
     *
     * @param name The participant's name to remove.
     * @return JSON response with success flag and updated pool contents.
     */
    @DeleteMapping("/api/names/{name}")
    public ResponseEntity<Map<String, Object>> removeName(@PathVariable("name") String name)
    {
        boolean removed = nameService.removeName(name);
        return ResponseEntity.ok(Map.of(
                "success", removed,
                "name", name != null ? name.trim() : "",
                "total", nameService.getNamesCount(),
                "names", nameService.getNames()
        ));
    }

    /**
     * Searches for participants whose names start with the specified letter or prefix.
     *
     * @param letter Starting character or prefix query (e.g. 'K' or 'Ka').
     * @return JSON response containing search query, count, and matching names.
     */
    @GetMapping("/api/names/search")
    public ResponseEntity<Map<String, Object>> searchNames(@RequestParam("letter") String letter)
    {
        List<String> matches = nameService.searchByStartingLetter(letter);
        return ResponseEntity.ok(Map.of(
                "query", letter != null ? letter.trim() : "",
                "count", matches.size(),
                "matches", matches
        ));
    }
}
