package com.workshop.agent.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.workshop.agent.config.AgentProperties;
import com.workshop.agent.logging.ConsoleAnsiLogger;
import com.workshop.agent.logging.SessionDiskLogger;
import com.workshop.agent.mcp.client.McpClientManager;
import com.workshop.agent.mcp.model.McpWireRecord;
import com.workshop.agent.model.*;
import com.workshop.agent.util.MarkdownRenderer;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Transparent MCP Agent Engine.
 *
 * Exposes the full lifecycle of an agent interacting with external tools
 * via the Model Context Protocol (MCP) JSON-RPC 2.0 wire specification:
 * 1. User Input Handling
 * 2. Dynamic MCP Tool Discovery (`tools/list` over wire)
 * 3. Schema Translation & Context Assembly
 * 4. LLM Generation & Reasoning
 * 5. Out-of-Process MCP JSON-RPC Dispatch (`tools/call`) with exact wire latency
 * 6. Observation Feedback Loop
 * 7. Final Response Synthesis
 * 8. Full Audit Logging including `mcp_traffic.jsonl`
 */
@Service
public class McpAgentEngine {

    private static final Logger log = LoggerFactory.getLogger(McpAgentEngine.class);

    private final ChatLanguageModel chatLanguageModel;
    private final McpClientManager mcpClientManager;
    private final TransparentAgentEngine agentEngine;
    private final AgentProperties agentProperties;
    private final SessionDiskLogger sessionDiskLogger;
    private final MarkdownRenderer markdownRenderer;
    private final ObjectMapper objectMapper;

    public McpAgentEngine(
            ChatLanguageModel chatLanguageModel,
            McpClientManager mcpClientManager,
            TransparentAgentEngine agentEngine,
            AgentProperties agentProperties,
            SessionDiskLogger sessionDiskLogger,
            MarkdownRenderer markdownRenderer,
            ObjectMapper objectMapper
    ) {
        this.chatLanguageModel = chatLanguageModel;
        this.mcpClientManager = mcpClientManager;
        this.agentEngine = agentEngine;
        this.agentProperties = agentProperties;
        this.sessionDiskLogger = sessionDiskLogger;
        this.markdownRenderer = markdownRenderer;
        this.objectMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT).findAndRegisterModules();
    }

    public AgentSession getOrCreateSession(String sessionId) {
        AgentSession s = agentEngine.getOrCreateSession(sessionId);
        s.setMode("mcp");
        return s;
    }

    public void registerEventListener(String sessionId, java.util.function.Consumer<AgentStepEvent> listener) {
        agentEngine.registerEventListener(sessionId, listener);
    }

    public void removeEventListener(String sessionId, java.util.function.Consumer<AgentStepEvent> listener) {
        agentEngine.removeEventListener(sessionId, listener);
    }

    /**
     * Executes a user turn against the MCP-powered Agent.
     */
    public ChatMessageDto processTurn(String sessionId, String userPrompt) {
        AgentSession session = getOrCreateSession(sessionId);
        List<Object> rawPayloadAuditList = new ArrayList<>();
        int currentStepNumber = session.getStepEvents().size() + 1;

        // 1. Record User Message
        String userMsgId = UUID.randomUUID().toString();
        ChatMessageDto userMsgDto = ChatMessageDto.user(userMsgId, userPrompt);
        session.addMessage(userMsgDto);

        AgentStepEvent inputEvent = AgentStepEvent.of(
                sessionId,
                currentStepNumber++,
                StepType.USER_INPUT,
                "Received User Query (MCP Mode)",
                "User query: \"" + truncate(userPrompt, 100) + "\"",
                userPrompt,
                TokenUsageStats.empty(),
                Map.of("queryLength", userPrompt.length(), "mode", "mcp")
        );
        publishStep(session, inputEvent);

        // 2. Dynamic MCP Tool Discovery Phase
        mcpClientManager.refreshAllServers(sessionId);
        List<ToolSpecification> mcpToolSpecs = mcpClientManager.getDiscoveredToolSpecifications();
        Map<String, Map<String, Object>> rawToolDefs = mcpClientManager.getToolRawDefinitions();

        AgentStepEvent discoveryEvent = AgentStepEvent.of(
                sessionId,
                currentStepNumber++,
                StepType.MCP_DISCOVERY,
                "MCP Dynamic Tool Discovery (JSON-RPC: tools/list)",
                "Discovered " + mcpToolSpecs.size() + " active tools from MCP servers (Local Tools + Open-Meteo Weather).",
                toJson(Map.of(
                        "servers", mcpClientManager.getServerConfigs(),
                        "discoveredTools", rawToolDefs
                )),
                TokenUsageStats.empty(),
                Map.of("toolCount", mcpToolSpecs.size(), "tools", mcpToolSpecs.stream().map(ToolSpecification::name).toList())
        );
        publishStep(session, discoveryEvent);

        // 3. Assemble Context
        List<ChatMessage> conversationMessages = new ArrayList<>();
        conversationMessages.add(SystemMessage.from(session.getSystemPrompt()));

        for (ChatMessageDto prev : session.getMessages()) {
            if ("user".equalsIgnoreCase(prev.role()) && !prev.id().equals(userMsgId)) {
                conversationMessages.add(UserMessage.from(prev.content()));
            } else if ("assistant".equalsIgnoreCase(prev.role())) {
                conversationMessages.add(AiMessage.from(prev.content()));
            }
        }
        conversationMessages.add(UserMessage.from(userPrompt));

        Map<String, Object> contextPayload = new LinkedHashMap<>();
        contextPayload.put("messages", formatMessagesForAudit(conversationMessages));
        contextPayload.put("mcpToolSpecifications", formatToolSpecifications(mcpToolSpecs));

        AgentStepEvent contextEvent = AgentStepEvent.of(
                sessionId,
                currentStepNumber++,
                StepType.CONTEXT_ASSEMBLY,
                "Context Window & MCP Schema Injection",
                "Assembled " + conversationMessages.size() + " messages with " + mcpToolSpecs.size() + " MCP tool schemas for Gemini.",
                toJson(contextPayload),
                TokenUsageStats.empty(),
                Map.of("messageCount", conversationMessages.size(), "toolCount", mcpToolSpecs.size())
        );
        publishStep(session, contextEvent);

        // 4. Enter ReAct Loop with MCP Wire Calling
        int maxSteps = agentProperties.getMaxSteps();
        int loopIteration = 0;
        List<AgentStepEvent> turnStepEvents = new ArrayList<>();
        TokenUsageStats turnTotalStats = TokenUsageStats.empty();

        while (loopIteration < maxSteps) {
            loopIteration++;
            long requestStartTime = System.currentTimeMillis();

            Map<String, Object> modelRequestPayload = new LinkedHashMap<>();
            modelRequestPayload.put("model", session.getModelName());
            modelRequestPayload.put("temperature", session.getTemperature());
            modelRequestPayload.put("messages", formatMessagesForAudit(conversationMessages));
            if (!mcpToolSpecs.isEmpty()) {
                modelRequestPayload.put("toolSpecifications", formatToolSpecifications(mcpToolSpecs));
            }

            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(conversationMessages)
                    .parameters(ChatRequestParameters.builder()
                            .toolSpecifications(mcpToolSpecs)
                            .build())
                    .build();

            ChatResponse response;
            try {
                response = chatLanguageModel.chat(chatRequest);
            } catch (Exception e) {
                log.error("[MCP-ENGINE] LLM generation failed on iteration {}: {}", loopIteration, e.getMessage(), e);
                AgentStepEvent errorEvent = AgentStepEvent.of(
                        sessionId,
                        currentStepNumber++,
                        StepType.AGENT_ERROR,
                        "LLM Execution Error (Iteration " + loopIteration + ")",
                        "Failed to call model: " + e.getMessage(),
                        toJson(Map.of("request", modelRequestPayload, "error", e.getMessage())),
                        TokenUsageStats.empty(),
                        Map.of("error", e.getMessage())
                );
                publishStep(session, errorEvent);
                turnStepEvents.add(errorEvent);
                break;
            }

            long requestDuration = System.currentTimeMillis() - requestStartTime;
            TokenUsage tokenUsage = response.tokenUsage();
            int inToks = tokenUsage != null ? tokenUsage.inputTokenCount() : 0;
            int outToks = tokenUsage != null ? tokenUsage.outputTokenCount() : 0;
            TokenUsageStats stepStats = TokenUsageStats.of(inToks, outToks, requestDuration);
            turnTotalStats = turnTotalStats.add(stepStats);

            AiMessage aiMessage = response.aiMessage();

            // Check if Model Decided to Invoke MCP Tools
            if (aiMessage.hasToolExecutionRequests()) {
                List<ToolExecutionRequest> toolRequests = aiMessage.toolExecutionRequests();

                String requestJson = toJson(modelRequestPayload);
                String responseJson = toJson(Map.of(
                        "text", aiMessage.text() != null ? aiMessage.text() : "",
                        "toolRequests", toolRequests.stream().map(this::formatToolExecutionRequest).toList()
                ));

                AgentStepEvent reasoningEvent = AgentStepEvent.of(
                        sessionId,
                        currentStepNumber++,
                        StepType.TOOL_CALL_REQUEST,
                        "LLM Reasoning (Iteration " + loopIteration + "): Requested " + toolRequests.size() + " MCP Tool(s)",
                        "Decided to call external MCP tools: " + toolRequests.stream().map(ToolExecutionRequest::name).toList(),
                        responseJson,
                        requestJson,
                        responseJson,
                        stepStats,
                        Map.of("toolCount", toolRequests.size())
                );
                publishStep(session, reasoningEvent);
                turnStepEvents.add(reasoningEvent);

                conversationMessages.add(aiMessage);

                // Dispatch each tool request over Model Context Protocol (JSON-RPC 2.0)
                for (ToolExecutionRequest toolRequest : toolRequests) {
                    // Dispatch via McpClientManager
                    McpClientManager.McpToolExecutionResult execResult = mcpClientManager.executeToolCall(
                            sessionId,
                            toolRequest.name(),
                            toolRequest.arguments()
                    );

                    // 1. Emit MCP Wire Tool Call Event (with dual request/response drawers)
                    AgentStepEvent mcpWireEvent = AgentStepEvent.of(
                            sessionId,
                            currentStepNumber++,
                            StepType.MCP_TOOL_CALL,
                            "MCP Wire Call: " + toolRequest.name() + "() -> [" + execResult.serverName() + "]",
                            String.format("Dispatched JSON-RPC tools/call over %s. Wire round-trip: %d ms.",
                                    execResult.transport(), execResult.latencyMs()),
                            execResult.responseJson(),
                            execResult.requestJson(),
                            execResult.responseJson(),
                            TokenUsageStats.of(0, 0, execResult.latencyMs()),
                            Map.of(
                                    "server", execResult.serverName(),
                                    "transport", execResult.transport(),
                                    "latencyMs", execResult.latencyMs(),
                                    "isSuccess", execResult.isSuccess()
                            )
                    );
                    publishStep(session, mcpWireEvent);
                    turnStepEvents.add(mcpWireEvent);

                    // 2. Emit Tool Observation Event (content fed back to LLM context)
                    String obsText = execResult.resultText();
                    AgentStepEvent obsEvent = AgentStepEvent.of(
                            sessionId,
                            currentStepNumber++,
                            StepType.TOOL_OBSERVATION,
                            "MCP Tool Observation: " + toolRequest.name(),
                            "Received observation payload (" + obsText.length() + " chars).",
                            obsText,
                            TokenUsageStats.empty(),
                            Map.of("length", obsText.length(), "success", execResult.isSuccess())
                    );
                    publishStep(session, obsEvent);
                    turnStepEvents.add(obsEvent);

                    // Feed observation back to model
                    ToolExecutionResultMessage resultMsg = ToolExecutionResultMessage.from(toolRequest, obsText);
                    conversationMessages.add(resultMsg);
                    rawPayloadAuditList.add(Map.of("mcpObservation", formatMessageForAudit(resultMsg)));
                }

            } else {
                // Final Synthesis Reached!
                String finalText = aiMessage.text() != null ? aiMessage.text() : "(No response synthesized)";
                String formattedHtml = markdownRenderer.renderHtml(finalText);

                String requestJson = toJson(modelRequestPayload);
                String responseJson = toJson(Map.of("finalAnswer", finalText));

                AgentStepEvent finalEvent = AgentStepEvent.of(
                        sessionId,
                        currentStepNumber++,
                        StepType.FINAL_SYNTHESIS,
                        "LLM Reasoning (Iteration " + loopIteration + "): Final Answer Synthesized",
                        "Completed MCP reasoning cycle in " + loopIteration + " iteration(s).",
                        responseJson,
                        requestJson,
                        responseJson,
                        stepStats,
                        Map.of("iteration", loopIteration, "length", finalText.length())
                );
                publishStep(session, finalEvent);
                turnStepEvents.add(finalEvent);

                ChatMessageDto assistantDto = ChatMessageDto.assistant(
                        UUID.randomUUID().toString(),
                        finalText,
                        formattedHtml,
                        turnStepEvents,
                        turnTotalStats
                );
                session.addMessage(assistantDto);

                // Save session disk audit snapshot & MCP traffic
                sessionDiskLogger.saveSessionSnapshot(session, rawPayloadAuditList);
                saveMcpTrafficToDisk(sessionId);
                return assistantDto;
            }
        }

        // Fallback if max steps reached
        String fallbackMsg = "MCP Agent reached maximum reasoning steps (" + maxSteps + ").";
        ChatMessageDto fallbackDto = ChatMessageDto.assistant(
                UUID.randomUUID().toString(),
                fallbackMsg,
                "<p class='text-danger'>" + fallbackMsg + "</p>",
                turnStepEvents,
                turnTotalStats
        );
        session.addMessage(fallbackDto);
        sessionDiskLogger.saveSessionSnapshot(session, rawPayloadAuditList);
        saveMcpTrafficToDisk(sessionId);
        return fallbackDto;
    }

    private void saveMcpTrafficToDisk(String sessionId) {
        try {
            java.nio.file.Path sessionDir = sessionDiskLogger.getSessionDir(sessionId);
            java.nio.file.Path mcpTrafficFile = sessionDir.resolve("mcp_traffic.jsonl");
            List<McpWireRecord> traffic = mcpClientManager.getSessionTraffic(sessionId);

            StringBuilder sb = new StringBuilder();
            for (McpWireRecord record : traffic) {
                sb.append(objectMapper.writeValueAsString(record)).append("\n");
            }
            java.nio.file.Files.writeString(mcpTrafficFile, sb.toString());
            log.info("[MCP-AUDIT] Saved {} MCP wire records to {}", traffic.size(), mcpTrafficFile);
        } catch (Exception e) {
            log.warn("[MCP-AUDIT] Could not save mcp_traffic.jsonl: {}", e.getMessage());
        }
    }

    private void publishStep(AgentSession session, AgentStepEvent event) {
        agentEngine.publishStep(session, event);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }

    private String truncate(String str, int max) {
        if (str == null) return "";
        if (str.length() <= max) return str;
        return str.substring(0, max) + "...";
    }

    public List<Object> formatMessagesForAudit(List<ChatMessage> messages) {
        if (messages == null) return List.of();
        return messages.stream().map(this::formatMessageForAudit).toList();
    }

    public Object formatMessageForAudit(ChatMessage msg) {
        if (msg == null) return null;
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", msg.type().name());
        if (msg instanceof SystemMessage sm) {
            map.put("text", sm.text());
        } else if (msg instanceof UserMessage um) {
            map.put("text", um.singleText());
        } else if (msg instanceof AiMessage am) {
            map.put("text", am.text());
            if (am.hasToolExecutionRequests()) {
                map.put("toolExecutionRequests", am.toolExecutionRequests().stream().map(r -> Map.of(
                        "id", r.id() != null ? r.id() : "",
                        "name", r.name() != null ? r.name() : "",
                        "arguments", r.arguments() != null ? r.arguments() : "{}"
                )).toList());
            }
        } else if (msg instanceof ToolExecutionResultMessage tm) {
            map.put("toolName", tm.toolName());
            map.put("text", tm.text());
        } else {
            map.put("raw", msg.toString());
        }
        return map;
    }

    public List<Object> formatToolSpecifications(List<ToolSpecification> specs) {
        if (specs == null) return List.of();
        return specs.stream().map(this::formatToolSpecification).toList();
    }

    public Object formatToolSpecification(ToolSpecification spec) {
        if (spec == null) return null;
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", spec.name());
        map.put("description", spec.description() != null ? spec.description() : "");
        map.put("parameters", spec.parameters() != null ? formatSchemaElement(spec.parameters()) : Map.of("type", "object", "properties", Map.of()));
        return map;
    }

    private Map<String, Object> formatSchemaElement(dev.langchain4j.model.chat.request.json.JsonSchemaElement element) {
        if (element == null) return Map.of();
        Map<String, Object> map = new LinkedHashMap<>();

        if (element instanceof dev.langchain4j.model.chat.request.json.JsonStringSchema s) {
            map.put("type", "string");
            if (s.description() != null) map.put("description", s.description());
        } else if (element instanceof dev.langchain4j.model.chat.request.json.JsonIntegerSchema i) {
            map.put("type", "integer");
            if (i.description() != null) map.put("description", i.description());
        } else if (element instanceof dev.langchain4j.model.chat.request.json.JsonNumberSchema n) {
            map.put("type", "number");
            if (n.description() != null) map.put("description", n.description());
        } else if (element instanceof dev.langchain4j.model.chat.request.json.JsonBooleanSchema b) {
            map.put("type", "boolean");
            if (b.description() != null) map.put("description", b.description());
        } else if (element instanceof dev.langchain4j.model.chat.request.json.JsonObjectSchema o) {
            map.put("type", "object");
            if (o.description() != null) map.put("description", o.description());
            if (o.properties() != null) {
                Map<String, Object> props = new LinkedHashMap<>();
                o.properties().forEach((k, v) -> props.put(k, formatSchemaElement(v)));
                map.put("properties", props);
            }
            if (o.required() != null && !o.required().isEmpty()) {
                map.put("required", o.required());
            }
        } else if (element instanceof dev.langchain4j.model.chat.request.json.JsonArraySchema a) {
            map.put("type", "array");
            if (a.description() != null) map.put("description", a.description());
            if (a.items() != null) {
                map.put("items", formatSchemaElement(a.items()));
            }
        }
        return map;
    }

    public Object formatToolExecutionRequest(ToolExecutionRequest req) {
        if (req == null) return null;
        Map<String, Object> map = new LinkedHashMap<>();
        if (req.id() != null && !req.id().isBlank()) map.put("id", req.id());
        map.put("name", req.name());
        if (req.arguments() != null && !req.arguments().isBlank()) {
            try {
                map.put("arguments", objectMapper.readTree(req.arguments()));
            } catch (Exception e) {
                map.put("arguments", req.arguments());
            }
        } else {
            map.put("arguments", Map.of());
        }
        return map;
    }
}
