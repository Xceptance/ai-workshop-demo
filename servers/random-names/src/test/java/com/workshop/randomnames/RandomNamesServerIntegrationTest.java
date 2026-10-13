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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class RandomNamesServerIntegrationTest {

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

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Nested
    @DisplayName("Domain NameService Tests")
    class NameServiceTests {

        @Test
        @DisplayName("Name pool contains exact 10 workshop participants")
        void testParticipantPool() {
            List<String> names = nameService.getNames();
            assertThat(names).containsExactly(
                    "Alex", "Rene", "Ray", "Jane", "Katja",
                    "Katrin", "Johannes", "Bernd", "Dude", "Mario"
            );
            assertThat(nameService.getNamesCount()).isEqualTo(10);
        }

        @Test
        @DisplayName("getRandomName picks from the pool")
        void testRandomSelection() {
            String name = nameService.getRandomName();
            assertThat(nameService.getNames()).contains(name);
        }

        @Test
        @DisplayName("getRandomName respects exclusion parameter")
        void testExclusion() {
            for (int i = 0; i < 20; i++) {
                String name = nameService.getRandomName("Mario");
                assertThat(name).isNotEqualTo("Mario");
            }
        }

        @Test
        @DisplayName("selectPair returns two distinct names")
        void testPairSelection() {
            List<String> pair = nameService.selectPair();
            assertThat(pair).hasSize(2);
            assertThat(pair.get(0)).isNotEqualTo(pair.get(1));
            assertThat(nameService.getNames()).contains(pair.get(0), pair.get(1));
        }
    }

    @Nested
    @DisplayName("Concept 1: MCP Tools (Dynamic Execution)")
    class ToolsTests {

        @Test
        @DisplayName("getRandomName tool returns formatted result with source port and name")
        void testGetRandomNameTool() {
            String output = tools.getRandomName(null);
            assertThat(output).contains("[MCP Server: random-names-server (Port 9000)]");
            assertThat(output).contains("Selected random name:");
        }

        @Test
        @DisplayName("listWorkshopNames tool returns all 10 participants")
        void testListWorkshopNamesTool() {
            String output = tools.listWorkshopNames();
            assertThat(output).contains("All 10 workshop participants");
            assertThat(output).contains("Alex", "Rene", "Ray", "Dude", "Mario");
        }

        @Test
        @DisplayName("pickPairProgrammingDuo tool returns Driver and Navigator")
        void testPickPairTool() {
            String output = tools.pickPairProgrammingDuo();
            assertThat(output).contains("Driver = '");
            assertThat(output).contains("Navigator = '");
        }
    }

    @Nested
    @DisplayName("Concept 2: MCP Resources (Contextual Data)")
    class ResourcesTests {

        @Test
        @DisplayName("names://participants/list returns JSON list of all 10 participants")
        void testParticipantsListResource() throws Exception {
            String json = resources.getParticipantsList();
            JsonNode root = objectMapper.readTree(json);
            assertThat(root.path("count").asInt()).isEqualTo(10);
            assertThat(root.path("port").asInt()).isEqualTo(9000);
            assertThat(root.path("participants")).hasSize(10);
        }

        @Test
        @DisplayName("names://participants/random returns live JSON snapshot")
        void testRandomSnapshotResource() throws Exception {
            String json = resources.getRandomParticipantSnapshot();
            JsonNode root = objectMapper.readTree(json);
            assertThat(root.has("selectedName")).isTrue();
            assertThat(root.path("port").asInt()).isEqualTo(9000);
            assertThat(root.path("poolSize").asInt()).isEqualTo(10);
        }

        @Test
        @DisplayName("names://participants/stats returns statistical metadata")
        void testStatsResource() throws Exception {
            String json = resources.getParticipantStats();
            JsonNode root = objectMapper.readTree(json);
            assertThat(root.path("poolSize").asInt()).isEqualTo(10);
            assertThat(root.path("alphabeticalFirst").asText()).isEqualTo("Alex");
            assertThat(root.path("alphabeticalLast").asText()).isEqualTo("Rene");
        }
    }

    @Nested
    @DisplayName("Concept 3: MCP Prompts (Pre-Packaged Templates)")
    class PromptsTests {

        @Test
        @DisplayName("pickNextPresenter prompt includes topic and guidance")
        void testPickPresenterPrompt() {
            String promptText = prompts.pickNextPresenter("Agent Memory");
            assertThat(promptText).contains("get_random_name");
            assertThat(promptText).contains("Agent Memory");
            assertThat(promptText).contains("port 9000");
        }

        @Test
        @DisplayName("setupPairProgramming prompt includes task and roles")
        void testSetupPairPrompt() {
            String promptText = prompts.setupPairProgramming("Building MCP Server");
            assertThat(promptText).contains("pick_pair_programming_duo");
            assertThat(promptText).contains("Driver");
            assertThat(promptText).contains("Navigator");
        }

        @Test
        @DisplayName("inspectMcpCapabilities prompt contrasts Tools, Resources, and Prompts")
        void testInspectPrompt() {
            String promptText = prompts.inspectMcpCapabilities();
            assertThat(promptText).contains("Tools:");
            assertThat(promptText).contains("Resources:");
            assertThat(promptText).contains("Prompts:");
        }
    }

    @Nested
    @DisplayName("HTTP REST Endpoints")
    class RestEndpointTests {

        @Test
        @DisplayName("GET / returns status UP, port 9000, and 10 names")
        void testStatusEndpoint() throws Exception {
            mockMvc.perform(get("/"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"))
                    .andExpect(jsonPath("$.port").value(9000))
                    .andExpect(jsonPath("$.participantsCount").value(10))
                    .andExpect(jsonPath("$.participants[0]").value("Alex"));
        }

        @Test
        @DisplayName("GET /api/names/random returns random participant")
        void testRandomNameRest() throws Exception {
            mockMvc.perform(get("/api/names/random"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.selectedName").isNotEmpty())
                    .andExpect(jsonPath("$.poolSize").value(10));
        }
    }

    @Nested
    @DisplayName("MCP Protocol Streamable HTTP Endpoint Tests")
    class McpProtocolTests {

        @Test
        @DisplayName("POST /mcp handles JSON-RPC initialize handshake")
        void testMcpInitialize() throws Exception {
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
        @DisplayName("POST /mcp handles tools/list and returns random names tools")
        void testMcpToolsList() throws Exception {
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

            if (sessionId != null) {
                reqBuilder.header("Mcp-Session-Id", sessionId);
            }

            String response = mockMvc.perform(reqBuilder)
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            assertThat(response).contains("get_random_name");
            assertThat(response).contains("list_workshop_names");
            assertThat(response).contains("pick_pair_programming_duo");
        }

        @Test
        @DisplayName("POST /mcp handles prompts/list and returns pre-packaged prompts")
        void testMcpPromptsList() throws Exception {
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

            if (sessionId != null) {
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
        @DisplayName("POST /mcp handles resources/list and returns names resources")
        void testMcpResourcesList() throws Exception {
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

            if (sessionId != null) {
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
