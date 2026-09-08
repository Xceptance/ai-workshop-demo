package com.workshop.agent.mcp.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workshop.agent.mcp.model.McpProtocolMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

/**
 * Local MCP Server in Java offering similar tools as the initial demo:
 * Math, System Info, Workspace Directory/File inspection, and Workspace Search.
 *
 * Implements standard Model Context Protocol (MCP) JSON-RPC 2.0.
 * Can be invoked directly via Spring Bean or run as a standalone STDIO process via main().
 */
@Component
public class LocalMcpServer {

    private static final Logger log = LoggerFactory.getLogger(LocalMcpServer.class);
    private final ObjectMapper objectMapper;
    private final Path workspaceRoot;

    public LocalMcpServer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.workspaceRoot = Paths.get(".").toAbsolutePath().normalize();
    }

    public String getServerName() {
        return "local-workshop-tools";
    }

    public String getVersion() {
        return "1.0.0";
    }

    /**
     * Handles an incoming JSON-RPC MCP request and produces the JSON-RPC response.
     */
    public McpProtocolMessage handleRequest(McpProtocolMessage message) {
        if (message == null) {
            return McpProtocolMessage.error(null, -32600, "Invalid Request: null payload");
        }

        String method = message.method();
        if (method == null) {
            return McpProtocolMessage.error(message.id(), -32600, "Invalid Request: missing method");
        }

        switch (method) {
            case "initialize" -> {
                Map<String, Object> serverCapabilities = Map.of(
                        "tools", Map.of("listChanged", false)
                );
                Map<String, Object> serverInfo = Map.of(
                        "name", getServerName(),
                        "version", getVersion(),
                        "description", "Local Workshop Tools: Math, System Metrics, Files, and Search"
                );
                Map<String, Object> result = Map.of(
                        "protocolVersion", "2024-11-05",
                        "capabilities", serverCapabilities,
                        "serverInfo", serverInfo
                );
                return McpProtocolMessage.success(message.id(), result);
            }
            case "notifications/initialized" -> {
                log.info("[LocalMcpServer] Client sent notifications/initialized");
                return null; // Notifications don't receive a response
            }
            case "tools/list" -> {
                List<Map<String, Object>> tools = buildToolDefinitions();
                return McpProtocolMessage.success(message.id(), Map.of("tools", tools));
            }
            case "tools/call" -> {
                return executeToolCall(message);
            }
            case "ping" -> {
                return McpProtocolMessage.success(message.id(), Map.of());
            }
            default -> {
                log.warn("[LocalMcpServer] Unknown method requested: {}", method);
                return McpProtocolMessage.error(message.id(), -32601, "Method not found: " + method);
            }
        }
    }

    private List<Map<String, Object>> buildToolDefinitions() {
        List<Map<String, Object>> tools = new ArrayList<>();

        // 1. calculate
        tools.add(Map.of(
                "name", "calculate",
                "description", "Perform mathematical calculations (e.g. 42 * 10, sqrt(144), 2^8, or add/subtract/multiply/divide).",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "expression", Map.of(
                                        "type", "string",
                                        "description", "Mathematical expression to evaluate (e.g., '42 * 10', '15 + 27', 'sqrt(81)', '2^8')"
                                )
                        ),
                        "required", List.of("expression")
                )
        ));

        // 2. get_system_info
        tools.add(Map.of(
                "name", "get_system_info",
                "description", "Retrieve host JVM runtime metrics, operating system version, available CPU cores, and memory usage.",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of()
                )
        ));

        // 3. get_current_time
        tools.add(Map.of(
                "name", "get_current_time",
                "description", "Get the current date and time with timezone information.",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "timezone", Map.of(
                                        "type", "string",
                                        "description", "Optional timezone ID (e.g. 'UTC', 'Europe/Berlin', 'America/New_York'). Defaults to server timezone."
                                )
                        )
                )
        ));

        // 4. list_directory
        tools.add(Map.of(
                "name", "list_directory",
                "description", "Safely list files and folders within the project workspace.",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "relativePath", Map.of(
                                        "type", "string",
                                        "description", "Relative folder path (e.g., '.', 'docs', 'src'). Defaults to '.'."
                                )
                        )
                )
        ));

        // 5. read_file
        tools.add(Map.of(
                "name", "read_file",
                "description", "Safely read the text content of a file in the workspace (up to 100 lines).",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "filePath", Map.of(
                                        "type", "string",
                                        "description", "Path to the file to inspect relative to workspace root (e.g. 'README.md', 'pom.xml')."
                                )
                        ),
                        "required", List.of("filePath")
                )
        ));

        // 6. search_workspace
        tools.add(Map.of(
                "name", "search_workspace",
                "description", "Search project documentation, markdown files, and codebase for matching keywords.",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "query", Map.of(
                                        "type", "string",
                                        "description", "Keyword or phrase to search for."
                                )
                        ),
                        "required", List.of("query")
                )
        ));

        return tools;
    }

    @SuppressWarnings("unchecked")
    private McpProtocolMessage executeToolCall(McpProtocolMessage message) {
        Object paramsObj = message.params();
        String toolName = null;
        Map<String, Object> arguments = Map.of();

        if (paramsObj instanceof Map<?, ?> m) {
            toolName = (String) m.get("name");
            Object args = m.get("arguments");
            if (args instanceof Map<?, ?> am) {
                arguments = (Map<String, Object>) am;
            } else if (args instanceof JsonNode jn) {
                if (jn.isObject()) {
                    arguments = objectMapper.convertValue(jn, Map.class);
                }
            } else if (args instanceof String s && !s.isBlank()) {
                try {
                    arguments = objectMapper.readValue(s, Map.class);
                } catch (Exception ignored) {}
            }
        } else if (paramsObj instanceof JsonNode node) {
            toolName = node.path("name").asText(null);
            JsonNode argsNode = node.path("arguments");
            if (argsNode.isObject()) {
                arguments = objectMapper.convertValue(argsNode, Map.class);
            }
        }

        if (toolName == null || toolName.isBlank()) {
            return McpProtocolMessage.error(message.id(), -32602, "Missing 'name' in tool call parameters");
        }

        try {
            String resultText = switch (toolName) {
                case "calculate" -> executeCalculate(arguments);
                case "get_system_info" -> executeGetSystemInfo();
                case "get_current_time" -> executeGetCurrentTime(arguments);
                case "list_directory" -> executeListDirectory(arguments);
                case "read_file" -> executeReadFile(arguments);
                case "search_workspace" -> executeSearchWorkspace(arguments);
                default -> throw new IllegalArgumentException("Unknown tool: " + toolName);
            };

            Map<String, Object> resultPayload = Map.of(
                    "content", List.of(Map.of(
                            "type", "text",
                            "text", resultText
                    )),
                    "isError", false
            );
            return McpProtocolMessage.success(message.id(), resultPayload);
        } catch (Exception e) {
            log.error("[LocalMcpServer] Error executing tool {}: {}", toolName, e.getMessage(), e);
            Map<String, Object> resultPayload = Map.of(
                    "content", List.of(Map.of(
                            "type", "text",
                            "text", "Error executing tool '" + toolName + "': " + e.getMessage()
                    )),
                    "isError", true
            );
            return McpProtocolMessage.success(message.id(), resultPayload);
        }
    }

    private String executeCalculate(Map<String, Object> args) {
        String expr = String.valueOf(args.getOrDefault("expression", "")).trim();
        if (expr.isEmpty() && args.containsKey("operation")) {
            String op = String.valueOf(args.get("operation")).toUpperCase();
            double a = parseDouble(args.get("a"));
            double b = parseDouble(args.get("b"));
            double res = switch (op) {
                case "ADD", "PLUS", "+" -> a + b;
                case "SUBTRACT", "MINUS", "-" -> a - b;
                case "MULTIPLY", "*" -> a * b;
                case "DIVIDE", "/" -> {
                    if (b == 0) throw new ArithmeticException("Division by zero");
                    yield a / b;
                }
                default -> throw new IllegalArgumentException("Unsupported operation: " + op);
            };
            return String.format("%s %s %s = %s", formatNumber(a), op, formatNumber(b), formatNumber(res));
        }

        if (expr.isEmpty() || "null".equalsIgnoreCase(expr)) {
            throw new IllegalArgumentException("Missing or empty 'expression' parameter in calculate tool. Received arguments: " + args);
        }

        double val = evaluateMathExpression(expr);
        return expr + " = " + formatNumber(val);
    }

    /**
     * Recursive descent evaluator for mathematical expressions with standard precedence:
     * - Parentheses: ()
     * - Exponents: ^
     * - Multiplicative: * /
     * - Additive: + -
     * - Functions: sqrt, abs, cbrt, round, floor, ceil
     */
    private double evaluateMathExpression(final String str) {
        return new Object() {
            int pos = -1, ch;

            void nextChar() {
                ch = (++pos < str.length()) ? str.charAt(pos) : -1;
            }

            boolean eat(int charToEat) {
                while (ch == ' ') nextChar();
                if (ch == charToEat) {
                    nextChar();
                    return true;
                }
                return false;
            }

            double parse() {
                nextChar();
                double x = parseExpression();
                while (ch == ' ') nextChar();
                if (pos < str.length()) {
                    throw new IllegalArgumentException("Unexpected character '" + (char) ch + "' at position " + pos);
                }
                return x;
            }

            double parseExpression() {
                double x = parseTerm();
                for (;;) {
                    if (eat('+')) x += parseTerm();
                    else if (eat('-')) x -= parseTerm();
                    else return x;
                }
            }

            double parseTerm() {
                double x = parseFactor();
                for (;;) {
                    if (eat('*') || eat('x') || eat('×')) x *= parseFactor();
                    else if (eat('/') || eat('÷')) {
                        double divisor = parseFactor();
                        if (divisor == 0.0) throw new ArithmeticException("Division by zero");
                        x /= divisor;
                    } else {
                        return x;
                    }
                }
            }

            double parseFactor() {
                if (eat('+')) return +parseFactor();
                if (eat('-')) return -parseFactor();

                double x;
                int startPos = this.pos;
                if (eat('(')) {
                    x = parseExpression();
                    if (!eat(')')) {
                        throw new IllegalArgumentException("Missing closing parenthesis ')' in expression: " + str);
                    }
                } else if ((ch >= '0' && ch <= '9') || ch == '.') {
                    while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                    x = Double.parseDouble(str.substring(startPos, this.pos));
                } else if (Character.isLetter(ch)) {
                    while (Character.isLetter(ch)) nextChar();
                    String func = str.substring(startPos, this.pos).toLowerCase();
                    if (eat('(')) {
                        x = parseExpression();
                        if (!eat(')')) {
                            throw new IllegalArgumentException("Missing closing parenthesis after " + func + "() in: " + str);
                        }
                    } else {
                        x = parseFactor();
                    }
                    x = switch (func) {
                        case "sqrt" -> {
                            if (x < 0) throw new ArithmeticException("Square root of negative number: " + x);
                            yield Math.sqrt(x);
                        }
                        case "abs" -> Math.abs(x);
                        case "cbrt" -> Math.cbrt(x);
                        case "ceil" -> Math.ceil(x);
                        case "floor" -> Math.floor(x);
                        case "round" -> (double) Math.round(x);
                        default -> throw new IllegalArgumentException("Unsupported math function: " + func);
                    };
                } else {
                    throw new IllegalArgumentException("Unexpected character '" + (char) ch + "' in expression: " + str);
                }

                if (eat('^')) {
                    x = Math.pow(x, parseFactor());
                }

                return x;
            }
        }.parse();
    }

    private String executeGetSystemInfo() {
        Runtime rt = Runtime.getRuntime();
        long mb = 1024 * 1024;
        long totalMemory = rt.totalMemory() / mb;
        long freeMemory = rt.freeMemory() / mb;
        long maxMemory = rt.maxMemory() / mb;
        long usedMemory = totalMemory - freeMemory;

        return String.format("""
                Host System Metrics (from Local MCP Server):
                - OS: %s (version: %s, arch: %s)
                - Java: %s (%s)
                - Available Processors: %d cores
                - JVM Heap Memory: %d MB used / %d MB total (Max: %d MB)
                - Transport: Local Model Context Protocol (MCP JSON-RPC 2.0)
                """,
                System.getProperty("os.name"),
                System.getProperty("os.version"),
                System.getProperty("os.arch"),
                System.getProperty("java.version"),
                System.getProperty("java.vendor"),
                rt.availableProcessors(),
                usedMemory, totalMemory, maxMemory
        );
    }

    private String executeGetCurrentTime(Map<String, Object> args) {
        String tz = String.valueOf(args.getOrDefault("timezone", "")).trim();
        ZoneId zoneId;
        try {
            zoneId = tz.isEmpty() ? ZoneId.systemDefault() : ZoneId.of(tz);
        } catch (Exception e) {
            zoneId = ZoneId.systemDefault();
        }

        ZonedDateTime now = ZonedDateTime.now(zoneId);
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
        return "Current Server Date/Time (Local MCP): " + now.format(dtf) + " (" + zoneId.getId() + ")";
    }

    private String executeListDirectory(Map<String, Object> args) {
        String relPath = String.valueOf(args.getOrDefault("relativePath", ".")).trim();
        if (relPath.isEmpty()) relPath = ".";

        Path target = workspaceRoot.resolve(relPath).normalize();
        if (!target.startsWith(workspaceRoot)) {
            return "ERROR: Access denied outside workspace root.";
        }
        if (!Files.exists(target)) {
            return "Path does not exist: " + relPath;
        }

        try (Stream<Path> stream = Files.list(target)) {
            List<String> items = stream
                    .sorted(Comparator.comparing(p -> !Files.isDirectory(p)))
                    .map(p -> {
                        String name = p.getFileName().toString();
                        if (Files.isDirectory(p)) {
                            return "[DIR]  " + name;
                        } else {
                            try {
                                return String.format("[FILE] %-30s (%d bytes)", name, Files.size(p));
                            } catch (IOException e) {
                                return "[FILE] " + name;
                            }
                        }
                    })
                    .limit(50)
                    .toList();

            return "Directory listing for '" + relPath + "':\n" + String.join("\n", items);
        } catch (IOException e) {
            return "Failed to list directory: " + e.getMessage();
        }
    }

    private String executeReadFile(Map<String, Object> args) {
        String filePath = String.valueOf(args.getOrDefault("filePath", "")).trim();
        if (filePath.isEmpty()) {
            return "ERROR: No filePath specified.";
        }

        Path target = workspaceRoot.resolve(filePath).normalize();
        if (!target.startsWith(workspaceRoot)) {
            return "ERROR: Access denied outside workspace root.";
        }
        if (!Files.exists(target) || Files.isDirectory(target)) {
            return "File not found or is a directory: " + filePath;
        }

        try {
            List<String> lines = Files.readAllLines(target);
            int limit = Math.min(lines.size(), 100);
            StringBuilder sb = new StringBuilder();
            sb.append("=== Content of ").append(filePath).append(" (Showing ").append(limit).append("/").append(lines.size()).append(" lines) ===\n");
            for (int i = 0; i < limit; i++) {
                sb.append(String.format("%3d: %s\n", i + 1, lines.get(i)));
            }
            if (lines.size() > 100) {
                sb.append("... [truncated ").append(lines.size() - 100).append(" lines] ...\n");
            }
            return sb.toString();
        } catch (IOException e) {
            return "Failed to read file: " + e.getMessage();
        }
    }

    private String executeSearchWorkspace(Map<String, Object> args) {
        String query = String.valueOf(args.getOrDefault("query", "")).toLowerCase().trim();
        if (query.isEmpty()) {
            return "ERROR: Empty search query.";
        }

        List<String> matches = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(workspaceRoot, 3)) {
            walk.filter(p -> Files.isRegularFile(p) && (p.toString().endsWith(".md") || p.toString().endsWith(".xml") || p.toString().endsWith(".yml")))
                    .forEach(p -> {
                        try {
                            List<String> lines = Files.readAllLines(p);
                            for (int i = 0; i < lines.size(); i++) {
                                if (lines.get(i).toLowerCase().contains(query)) {
                                    String rel = workspaceRoot.relativize(p).toString();
                                    matches.add(String.format("%s:%d: %s", rel, i + 1, lines.get(i).trim()));
                                    if (matches.size() >= 15) break;
                                }
                            }
                        } catch (Exception ignored) {}
                    });
        } catch (IOException e) {
            return "Search error: " + e.getMessage();
        }

        if (matches.isEmpty()) {
            return "No matches found in workspace for: '" + query + "'";
        }
        return "Workspace search results for '" + query + "':\n" + String.join("\n", matches);
    }

    private double parseDouble(Object val) {
        if (val instanceof Number n) return n.doubleValue();
        if (val == null) return 0.0;
        return Double.parseDouble(val.toString());
    }

    private String formatNumber(double val) {
        if (val == (long) val) {
            return String.format("%d", (long) val);
        }
        return String.format(Locale.US, "%.4f", val).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    /**
     * Standalone CLI runner for process-isolated STDIO MCP communication.
     * Reads JSON-RPC requests line-by-line from stdin, outputs responses to stdout.
     */
    public static void main(String[] args) {
        ObjectMapper mapper = new ObjectMapper();
        LocalMcpServer server = new LocalMcpServer(mapper);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(System.out))) {

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                try {
                    McpProtocolMessage request = mapper.readValue(line, McpProtocolMessage.class);
                    McpProtocolMessage response = server.handleRequest(request);
                    if (response != null) {
                        String respJson = mapper.writeValueAsString(response);
                        writer.write(respJson);
                        writer.newLine();
                        writer.flush();
                    }
                } catch (Exception e) {
                    McpProtocolMessage err = McpProtocolMessage.error(null, -32700, "Parse error: " + e.getMessage());
                    writer.write(mapper.writeValueAsString(err));
                    writer.newLine();
                    writer.flush();
                }
            }
        } catch (IOException e) {
            System.err.println("LocalMcpServer Stdio loop terminated: " + e.getMessage());
        }
    }
}
