package com.workshop.randomnames;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workshop.randomnames.domain.NameService;
import com.workshop.randomnames.mcp.RandomNamesPrompts;
import com.workshop.randomnames.mcp.RandomNamesResources;
import com.workshop.randomnames.mcp.RandomNamesTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ============================================================================
 * WORKSHOP DEMO: Comprehensive Integration Test Suite for MCP Server
 * ============================================================================
 *
 * <p><b>Educational Purpose:</b></p>
 * This test suite provides end-to-end and component-level verification for all
 * facets of our standalone Model Context Protocol (MCP) server. When developing
 * custom MCP servers for production or workshop demonstrations, automated testing
 * must address four distinct verification layers:
 *
 * <ol>
 *   <li><b>Layer 1 - Domain Logic ({@link NameService}):</b>
 *       Verifies thread-safe collection mutation, case-insensitive uniqueness,
 *       prefix matching, and non-repeating pair selection independently of any
 *       network or protocol overhead.</li>
 *
 *   <li><b>Layer 2 - Direct MCP Component Invocations (Tools, Resources, Prompts):</b>
 *       Validates that Spring beans decorated with {@code @McpTool},
 *       {@code @McpResource}, and {@code @McpPrompt} return properly structured,
 *       audit-friendly responses with metadata tags when invoked directly by
 *       Spring test fixtures.</li>
 *
 *   <li><b>Layer 3 - Parallel Human REST Verification API ({@code /api/names}):</b>
 *       Confirms that workshop attendees and instructors can independently inspect,
 *       query, add, and delete participants via standard HTTP endpoints using their
 *       browser or curl.</li>
 *
 *   <li><b>Layer 4 - MCP Wire Protocol Testing (JSON-RPC 2.0 over Streamable HTTP):</b>
 *       Simulates an actual AI Agent client (such as Claude Desktop or Antigravity)
 *       initiating the MCP session handshake ({@code initialize}), negotiating
 *       protocol versions, receiving session tracking headers ({@code Mcp-Session-Id}),
 *       and requesting schema catalogs via {@code tools/list}, {@code resources/list},
 *       and {@code prompts/list}.</li>
 * </ol>
 *
 * <p><b>Coding Style:</b></p>
 * Written in strict Allman indentation style (opening brace on a separate line)
 * with thorough inline documentation for workshop participants.
 */
@SpringBootTest
class RandomNamesServerIntegrationTest
{
    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NameService nameService;

    @Autowired
    private RandomNamesTools tools;

    @Autowired
    private RandomNamesResources resources;

    @Autowired
    private RandomNamesPrompts prompts;

    /**
     * Resets the participant pool to the initial 10 workshop attendees before every test
     * to guarantee test isolation and idempotent assertions, and initializes {@link MockMvc}.
     */
    @BeforeEach
    void setUp()
    {
        nameService.resetNames();
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    // ========================================================================
    // LAYER 1: DOMAIN LOGIC VERIFICATION
    // ========================================================================

    @Nested
    @DisplayName("Domain NameService Tests")
    class NameServiceTests
    {
        @Test
        @DisplayName("Name pool contains exact 10 initial workshop participants")
        void testParticipantPool()
        {
            // Verify the initial immutable seed collection matches workshop attendees
            List<String> names = nameService.getNames();
            assertThat(names).containsExactly(
                    "Alex", "Rene", "Ray", "Jane", "Katja",
                    "Katrin", "Johannes", "Bernd", "Dude", "Mario"
            );
            assertThat(nameService.getNamesCount()).isEqualTo(10);
        }

        @Test
        @DisplayName("addName adds participant and prevents duplicates case-insensitively")
        void testAddName()
        {
            // Adding a unique participant should succeed and increment count
            assertThat(nameService.addName("Zoe")).isTrue();
            assertThat(nameService.getNamesCount()).isEqualTo(11);
            assertThat(nameService.getNames()).contains("Zoe");

            // Adding duplicate with different casing must be rejected (case-insensitive deduplication)
            assertThat(nameService.addName("zoe")).isFalse();
            assertThat(nameService.getNamesCount()).isEqualTo(11);

            // Adding blank/whitespace name must throw IllegalArgumentException
            assertThatThrownBy(() -> nameService.addName("   "))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(nameService.getNamesCount()).isEqualTo(11);
        }

        @Test
        @DisplayName("removeName removes participant and handles non-existent lookups")
        void testRemoveName()
        {
            // Successfully removing an existing participant
            assertThat(nameService.removeName("Dude")).isTrue();
            assertThat(nameService.getNamesCount()).isEqualTo(9);
            assertThat(nameService.getNames()).doesNotContain("Dude");

            // Attempting to remove an already removed name must return false
            assertThat(nameService.removeName("Dude")).isFalse();

            // Attempting to remove a completely non-existent name must return false
            assertThat(nameService.removeName("NonExistent")).isFalse();
        }

        @Test
        @DisplayName("searchByStartingLetter matches case-insensitively and filters correctly")
        void testSearchByStartingLetter()
        {
            // Lowercase query matches 'Katja' and 'Katrin'
            List<String> kMatches = nameService.searchByStartingLetter("k");
            assertThat(kMatches).containsExactly("Katja", "Katrin");

            // Uppercase query matches 'Bernd'
            List<String> bMatches = nameService.searchByStartingLetter("B");
            assertThat(bMatches).containsExactly("Bernd");

            // Non-matching character returns empty list without error
            List<String> noMatches = nameService.searchByStartingLetter("Z");
            assertThat(noMatches).isEmpty();

            // Empty or null string returns empty list safely
            List<String> emptySearch = nameService.searchByStartingLetter("");
            assertThat(emptySearch).isEmpty();
        }

        @Test
        @DisplayName("getRandomName picks from the active participant pool")
        void testRandomSelection()
        {
            String name = nameService.getRandomName();
            assertThat(nameService.getNames()).contains(name);
        }

        @Test
        @DisplayName("getRandomName respects exclusion parameter to prevent self-selection")
        void testExclusion()
        {
            // Execute multiple rounds to statistically ensure exclusion is strictly honored
            for (int i = 0; i < 20; i++)
            {
                String name = nameService.getRandomName("Mario");
                assertThat(name).isNotEqualTo("Mario");
                assertThat(nameService.getNames()).contains(name);
            }
        }

        @Test
        @DisplayName("selectPair returns two distinct names for driver/navigator roles")
        void testPairSelection()
        {
            List<String> pair = nameService.selectPair();
            assertThat(pair).hasSize(2);
            assertThat(pair.get(0)).isNotEqualTo(pair.get(1));
            assertThat(nameService.getNames()).contains(pair.get(0), pair.get(1));
        }
    }

    // ========================================================================
    // LAYER 2: MCP CONCEPT 1 - TOOLS (DYNAMIC EXECUTION)
    // ========================================================================

    @Nested
    @DisplayName("Concept 1: MCP Tools (Dynamic Execution)")
    class ToolsTests
    {
        @Test
        @DisplayName("getRandomName tool returns formatted result with source port and name")
        void testGetRandomNameTool()
        {
            String output = tools.getRandomName(null);
            assertThat(output).contains("[MCP Server: random-names-server (Port 9000)]");
            assertThat(output).contains("Selected random name:");
        }

        @Test
        @DisplayName("listWorkshopNames tool returns all 10 participants")
        void testListWorkshopNamesTool()
        {
            String output = tools.listWorkshopNames();
            assertThat(output).contains("All 10 workshop participants");
            assertThat(output).contains("Alex", "Rene", "Ray", "Dude", "Mario");
        }

        @Test
        @DisplayName("pickPairProgrammingDuo tool returns Driver and Navigator roles")
        void testPickPairTool()
        {
            String output = tools.pickPairProgrammingDuo();
            assertThat(output).contains("Driver = '");
            assertThat(output).contains("Navigator = '");
        }

        @Test
        @DisplayName("addWorkshopName tool adds participant and reports updated pool size")
        void testAddWorkshopNameTool()
        {
            String output = tools.addWorkshopName("Linus");
            assertThat(output).contains("Successfully added 'Linus'");
            assertThat(output).contains("Participant pool now has 11 names");
            assertThat(nameService.getNames()).contains("Linus");

            // Attempting to add duplicate participant reports clear feedback
            String duplicate = tools.addWorkshopName("Linus");
            assertThat(duplicate).contains("Participant 'Linus' already exists in the pool");
        }

        @Test
        @DisplayName("removeWorkshopName tool removes participant and handles not found")
        void testRemoveWorkshopNameTool()
        {
            String output = tools.removeWorkshopName("Mario");
            assertThat(output).contains("Successfully removed 'Mario'");
            assertThat(output).contains("Participant pool now has 9 names");

            // Attempting to remove non-existent participant reports helpful error
            String notFound = tools.removeWorkshopName("Mario");
            assertThat(notFound).contains("Participant 'Mario' was not found in the pool");
        }

        @Test
        @DisplayName("searchNamesByLetter tool returns matching participants or clear empty notice")
        void testSearchNamesByLetterTool()
        {
            String kResult = tools.searchNamesByLetter("k");
            assertThat(kResult).contains("Found 2 participant(s) starting with 'k'");
            assertThat(kResult).contains("Katja, Katrin");

            String zResult = tools.searchNamesByLetter("Z");
            assertThat(zResult).contains("No participants found starting with 'Z'");
        }
    }

    // ========================================================================
    // LAYER 2: MCP CONCEPT 2 - RESOURCES (CONTEXTUAL DATA)
    // ========================================================================

    @Nested
    @DisplayName("Concept 2: MCP Resources (Contextual Data)")
    class ResourcesTests
    {
        @Test
        @DisplayName("names://participants/list returns JSON list of all 10 participants")
        void testParticipantsListResource() throws Exception
        {
            String json = resources.getParticipantsList();
            JsonNode root = objectMapper.readTree(json);
            assertThat(root.path("count").asInt()).isEqualTo(10);
            assertThat(root.path("port").asInt()).isEqualTo(9000);
            assertThat(root.path("participants")).hasSize(10);
        }

        @Test
        @DisplayName("names://participants/random returns live JSON snapshot")
        void testRandomSnapshotResource() throws Exception
        {
            String json = resources.getRandomParticipantSnapshot();
            JsonNode root = objectMapper.readTree(json);
            assertThat(root.has("selectedName")).isTrue();
            assertThat(root.path("port").asInt()).isEqualTo(9000);
            assertThat(root.path("poolSize").asInt()).isEqualTo(10);
        }

        @Test
        @DisplayName("names://participants/stats returns statistical metadata")
        void testStatsResource() throws Exception
        {
            String json = resources.getParticipantStats();
            JsonNode root = objectMapper.readTree(json);
            assertThat(root.path("poolSize").asInt()).isEqualTo(10);
            assertThat(root.path("alphabeticalFirst").asText()).isEqualTo("Alex");
            assertThat(root.path("alphabeticalLast").asText()).isEqualTo("Rene");
        }
    }

    // ========================================================================
    // LAYER 2: MCP CONCEPT 3 - PROMPTS (PRE-PACKAGED TEMPLATES)
    // ========================================================================

    @Nested
    @DisplayName("Concept 3: MCP Prompts (Pre-Packaged Templates)")
    class PromptsTests
    {
        @Test
        @DisplayName("pickNextPresenter prompt includes topic and guidance")
        void testPickPresenterPrompt()
        {
            String promptText = prompts.pickNextPresenter("Agent Memory");
            assertThat(promptText).contains("get_random_name");
            assertThat(promptText).contains("Agent Memory");
            assertThat(promptText).contains("port 9000");
        }

        @Test
        @DisplayName("setupPairProgramming prompt includes task and roles")
        void testSetupPairPrompt()
        {
            String promptText = prompts.setupPairProgramming("Building MCP Server");
            assertThat(promptText).contains("pick_pair_programming_duo");
            assertThat(promptText).contains("Driver");
            assertThat(promptText).contains("Navigator");
        }

        @Test
        @DisplayName("inspectMcpCapabilities prompt contrasts Tools, Resources, and Prompts")
        void testInspectPrompt()
        {
            String promptText = prompts.inspectMcpCapabilities();
            assertThat(promptText).contains("Tools:");
            assertThat(promptText).contains("Resources:");
            assertThat(promptText).contains("Prompts:");
        }
    }

    // ========================================================================
    // LAYER 3: PARALLEL REST VERIFICATION API
    // ========================================================================

    @Nested
    @DisplayName("HTTP REST Endpoints")
    class RestEndpointTests
    {
        @Test
        @DisplayName("GET / returns status UP, port 9000, and 10 names")
        void testStatusEndpoint() throws Exception
        {
            mockMvc.perform(get("/"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"))
                    .andExpect(jsonPath("$.port").value(9000))
                    .andExpect(jsonPath("$.participantsCount").value(10))
                    .andExpect(jsonPath("$.participants[0]").value("Alex"));
        }

        @Test
        @DisplayName("GET /api/names/random returns random participant")
        void testRandomNameRest() throws Exception
        {
            mockMvc.perform(get("/api/names/random"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.selectedName").isNotEmpty())
                    .andExpect(jsonPath("$.poolSize").value(10));
        }

        @Test
        @DisplayName("POST /api/names adds a new participant")
        void testAddNameRest() throws Exception
        {
            mockMvc.perform(post("/api/names").param("name", "Ada"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.total").value(11));
        }

        @Test
        @DisplayName("DELETE /api/names/{name} removes participant")
        void testRemoveNameRest() throws Exception
        {
            mockMvc.perform(delete("/api/names/Alex"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.total").value(9));
        }

        @Test
        @DisplayName("GET /api/names/search filters participants by letter")
        void testSearchNamesRest() throws Exception
        {
            mockMvc.perform(get("/api/names/search").param("letter", "K"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.count").value(2))
                    .andExpect(jsonPath("$.matches[0]").value("Katja"))
                    .andExpect(jsonPath("$.matches[1]").value("Katrin"));
        }
    }

    // ========================================================================
    // LAYER 4: MCP PROTOCOL STREAMABLE HTTP ENDPOINT TESTS (JSON-RPC 2.0)
    // ========================================================================

    @Nested
    @DisplayName("MCP Protocol Streamable HTTP Endpoint Tests")
    class McpProtocolTests
    {
        @Test
        @DisplayName("POST /mcp handles JSON-RPC initialize handshake")
        void testMcpInitialize() throws Exception
        {
            String initPayload = """
                    {
                      "jsonrpc": "2.0",
                      "id": 1,
                      "method": "initialize",
                      "params": {
                        "protocolVersion": "2024-11-05",
                        "capabilities": {},
                        "clientInfo": {
                          "name": "workshop-test-client",
                          "version": "1.0.0"
                        }
                      }
                    }
                    """;

            var result = mockMvc.perform(post("/mcp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                            .content(initPayload))
                    .andExpect(status().isOk())
                    .andReturn();

            String response = result.getResponse().getContentAsString();
            assertThat(response).contains("random-names-server");
        }

        @Test
        @DisplayName("POST /mcp handles tools/list and returns all 6 registered tools")
        void testMcpToolsList() throws Exception
        {
            String initPayload = """
                    {
                      "jsonrpc": "2.0",
                      "id": 1,
                      "method": "initialize",
                      "params": {
                        "protocolVersion": "2024-11-05",
                        "capabilities": {},
                        "clientInfo": {
                          "name": "workshop-test-client",
                          "version": "1.0.0"
                        }
                      }
                    }
                    """;

            var initResult = mockMvc.perform(post("/mcp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                            .content(initPayload))
                    .andExpect(status().isOk())
                    .andReturn();

            String sessionId = initResult.getResponse().getHeader("Mcp-Session-Id");

            String listPayload = """
                    {
                      "jsonrpc": "2.0",
                      "id": 2,
                      "method": "tools/list",
                      "params": {}
                    }
                    """;

            var reqBuilder = post("/mcp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                    .content(listPayload);

            if (sessionId != null)
            {
                reqBuilder.header("Mcp-Session-Id", sessionId);
            }

            String response = mockMvc.perform(reqBuilder)
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            // Verify all 6 tools are discovered dynamically by the AI agent
            assertThat(response).contains("get_random_name");
            assertThat(response).contains("list_workshop_names");
            assertThat(response).contains("pick_pair_programming_duo");
            assertThat(response).contains("add_workshop_name");
            assertThat(response).contains("remove_workshop_name");
            assertThat(response).contains("search_names_by_letter");
        }

        @Test
        @DisplayName("POST /mcp handles prompts/list and returns pre-packaged prompts")
        void testMcpPromptsList() throws Exception
        {
            String initPayload = """
                    {
                      "jsonrpc": "2.0",
                      "id": 1,
                      "method": "initialize",
                      "params": {
                        "protocolVersion": "2024-11-05",
                        "capabilities": {},
                        "clientInfo": {
                          "name": "workshop-test-client",
                          "version": "1.0.0"
                        }
                      }
                    }
                    """;

            var initResult = mockMvc.perform(post("/mcp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                            .content(initPayload))
                    .andExpect(status().isOk())
                    .andReturn();

            String sessionId = initResult.getResponse().getHeader("Mcp-Session-Id");

            String listPromptsPayload = """
                    {
                      "jsonrpc": "2.0",
                      "id": 3,
                      "method": "prompts/list",
                      "params": {}
                    }
                    """;

            var reqBuilder = post("/mcp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                    .content(listPromptsPayload);

            if (sessionId != null)
            {
                reqBuilder.header("Mcp-Session-Id", sessionId);
            }

            String response = mockMvc.perform(reqBuilder)
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            assertThat(response).contains("pick_next_presenter");
            assertThat(response).contains("setup_pair_programming");
        }

        @Test
        @DisplayName("POST /mcp handles resources/list and returns registered context resources")
        void testMcpResourcesList() throws Exception
        {
            String initPayload = """
                    {
                      "jsonrpc": "2.0",
                      "id": 1,
                      "method": "initialize",
                      "params": {
                        "protocolVersion": "2024-11-05",
                        "capabilities": {},
                        "clientInfo": {
                          "name": "workshop-test-client",
                          "version": "1.0.0"
                        }
                      }
                    }
                    """;

            var initResult = mockMvc.perform(post("/mcp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                            .content(initPayload))
                    .andExpect(status().isOk())
                    .andReturn();

            String sessionId = initResult.getResponse().getHeader("Mcp-Session-Id");

            String listResourcesPayload = """
                    {
                      "jsonrpc": "2.0",
                      "id": 4,
                      "method": "resources/list",
                      "params": {}
                    }
                    """;

            var reqBuilder = post("/mcp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                    .content(listResourcesPayload);

            if (sessionId != null)
            {
                reqBuilder.header("Mcp-Session-Id", sessionId);
            }

            String response = mockMvc.perform(reqBuilder)
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            assertThat(response).contains("names://participants/list");
            assertThat(response).contains("names://participants/random");
        }
    }
}
