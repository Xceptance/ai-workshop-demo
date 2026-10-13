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

@RestController
public class RandomNamesRestController {

    private final NameService nameService;

    public RandomNamesRestController(NameService nameService) {
        this.nameService = nameService;
    }

    @GetMapping({"/", "/api/status"})
    public ResponseEntity<Map<String, Object>> getStatus() {
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
                        "resources", List.of("names://participants/list", "names://participants/random", "names://participants/stats"),
                        "prompts", List.of("pick_next_presenter", "setup_pair_programming", "inspect_mcp_capabilities")
                ),
                "participantsCount", nameService.getNamesCount(),
                "participants", nameService.getNames()
        ));
    }

    @GetMapping("/api/names/random")
    public ResponseEntity<Map<String, Object>> getRandomName(@RequestParam(value = "exclude", required = false) String exclude) {
        var result = nameService.selectRandomName(exclude);
        return ResponseEntity.ok(Map.of(
                "selectedName", result.selectedName(),
                "poolSize", result.poolSize(),
                "confirmation", result.confirmationMessage()
        ));
    }

    @GetMapping("/api/names")
    public ResponseEntity<Map<String, Object>> getNames() {
        return ResponseEntity.ok(Map.of(
                "total", nameService.getNamesCount(),
                "names", nameService.getNames()
        ));
    }

    @PostMapping("/api/names")
    public ResponseEntity<Map<String, Object>> addName(@RequestParam("name") String name) {
        boolean added = nameService.addName(name);
        return ResponseEntity.ok(Map.of(
                "success", added,
                "name", name != null ? name.trim() : "",
                "total", nameService.getNamesCount(),
                "names", nameService.getNames()
        ));
    }

    @DeleteMapping("/api/names/{name}")
    public ResponseEntity<Map<String, Object>> removeName(@PathVariable("name") String name) {
        boolean removed = nameService.removeName(name);
        return ResponseEntity.ok(Map.of(
                "success", removed,
                "name", name != null ? name.trim() : "",
                "total", nameService.getNamesCount(),
                "names", nameService.getNames()
        ));
    }

    @GetMapping("/api/names/search")
    public ResponseEntity<Map<String, Object>> searchNames(@RequestParam("letter") String letter) {
        List<String> matches = nameService.searchByStartingLetter(letter);
        return ResponseEntity.ok(Map.of(
                "query", letter != null ? letter.trim() : "",
                "count", matches.size(),
                "matches", matches
        ));
    }
}
