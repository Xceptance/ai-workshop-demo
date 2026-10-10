package com.workshop.agent.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.workshop.agent.config.AgentProperties;
import com.workshop.agent.logging.ConsoleAnsiLogger;
import com.workshop.agent.logging.SessionDiskLogger;
import com.workshop.agent.model.*;
import com.workshop.agent.tools.ToolRegistry;
import com.workshop.agent.util.MarkdownRenderer;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.output.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Transparent Agent Engine - The Core Glass-Box ReAct Execution Loop
 *
 * Exposes every internal phase of an AI agent:
 * 1. User Input Handling
 * 2. Context Window & Tool Schema Assembly
 * 3. Raw Model Invocations
 * 4. Model Reasoning & Tool Execution Decisions
 * 5. Local Java Method Reflection & Execution
 * 6. Observation Feedback Loop
 * 7. Final Response Synthesis
 * 8. Real-time Disk Logging & Token Cost Accounting
 */
@Service
public class TransparentAgentEngine {

    private static final Logger log = LoggerFactory.getLogger(TransparentAgentEngine.class);

    private final ChatModel chatModel;
    private final DynamicChatModelService dynamicChatModelService;
    private final ToolRegistry toolRegistry;
    private final AgentProperties agentProperties;
    private final SessionDiskLogger sessionDiskLogger;
    private final ConsoleAnsiLogger consoleAnsiLogger;
    private final MarkdownRenderer markdownRenderer;
    private final ObjectMapper objectMapper;

    // Active sessions cache
    private final Map<String, AgentSession> sessions = new ConcurrentHashMap<>();
    // Real-time event listeners per session (e.g. SSE web streams) - Set prevents duplicate listener registrations
    private final Map<String, Set<Consumer<AgentStepEvent>>> sessionListeners = new ConcurrentHashMap<>();

    public TransparentAgentEngine(
            ChatModel chatModel,
            ToolRegistry toolRegistry,
            AgentProperties agentProperties,
            SessionDiskLogger sessionDiskLogger,
            ConsoleAnsiLogger consoleAnsiLogger,
            MarkdownRenderer markdownRenderer,
            ObjectMapper objectMapper
    ) {
        this(chatModel, null, toolRegistry, agentProperties, sessionDiskLogger, consoleAnsiLogger, markdownRenderer, objectMapper);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public TransparentAgentEngine(
            ChatModel chatModel,
            @org.springframework.beans.factory.annotation.Autowired(required = false) DynamicChatModelService dynamicChatModelService,
            ToolRegistry toolRegistry,
            AgentProperties agentProperties,
            SessionDiskLogger sessionDiskLogger,
            ConsoleAnsiLogger consoleAnsiLogger,
            MarkdownRenderer markdownRenderer,
            ObjectMapper objectMapper
    ) {
        this.chatModel = chatModel;
        this.dynamicChatModelService = dynamicChatModelService;
        this.toolRegistry = toolRegistry;
        this.agentProperties = agentProperties;
        this.sessionDiskLogger = sessionDiskLogger;
        this.consoleAnsiLogger = consoleAnsiLogger;
        this.markdownRenderer = markdownRenderer;
        this.objectMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public AgentSession getOrCreateSession(String sessionId) {
        return sessions.computeIfAbsent(sessionId, id -> new AgentSession(
                id,
                "Workshop Session " + id.substring(0, Math.min(6, id.length())),
                "gemini-3.7-flash",
                agentProperties.getSystemPrompt(),
                0.2
        ));
    }

    public Collection<AgentSession> getAllSessions() {
        return sessions.values();
    }

    public void registerEventListener(String sessionId, Consumer<AgentStepEvent> listener) {
        sessionListeners.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(listener);
    }

    public void removeEventListener(String sessionId, Consumer<AgentStepEvent> listener) {
        Set<Consumer<AgentStepEvent>> set = sessionListeners.get(sessionId);
        if (set != null) {
            set.remove(listener);
        }
    }

    /**
     * Executes a user turn against the agent, emitting granular events for each stage.
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
                "Received User Query",
                "User query: \"" + truncate(userPrompt, 100) + "\"",
                userPrompt,
                TokenUsageStats.empty(),
                Map.of("queryLength", userPrompt.length())
        );
        publishStep(session, inputEvent);

        // 2. Assemble Context (System Prompt, History, Active Tools)
        List<ChatMessage> conversationMessages = new ArrayList<>();
        String effectiveSystemPrompt = session.getSystemPrompt();
        String thinkingLevel = session.getThinkingLevel();
        if (thinkingLevel != null && !"dynamic".equalsIgnoreCase(thinkingLevel)) {
            String thinkingGuidance = switch (thinkingLevel.toLowerCase()) {
                case "off", "none" -> "\n\nThinking Budget: OFF. Provide concise, direct answers without verbose internal deliberation.";
                case "low" -> "\n\nThinking Budget: LOW. Keep reasoning brief, step directly to actions or conclusions.";
                case "medium" -> "\n\nThinking Budget: MEDIUM. Balance clear reasoning and step-by-step verification.";
                case "high" -> "\n\nThinking Budget: HIGH. Deliberate deeply, verify hypotheses, and double-check all tool arguments and observations before concluding.";
                default -> "";
            };
            effectiveSystemPrompt += thinkingGuidance;
        }
        conversationMessages.add(SystemMessage.from(effectiveSystemPrompt));

        // Add previous message history
        for (ChatMessageDto prev : session.getMessages()) {
            if ("user".equalsIgnoreCase(prev.role()) && !prev.id().equals(userMsgId)) {
                conversationMessages.add(UserMessage.from(prev.content()));
            } else if ("assistant".equalsIgnoreCase(prev.role())) {
                conversationMessages.add(AiMessage.from(prev.content()));
            }
        }
        conversationMessages.add(UserMessage.from(userPrompt));

        List<ToolSpecification> toolSpecs = toolRegistry.getAllSpecifications();
        String contextSummary = String.format("Assembled %d messages in context window with %d active tools.",
                conversationMessages.size(), toolSpecs.size());

        Map<String, Object> contextMetadata = new LinkedHashMap<>();
        contextMetadata.put("systemPromptLength", session.getSystemPrompt().length());
        contextMetadata.put("model", session.getModelName());
        contextMetadata.put("thinkingLevel", session.getThinkingLevel());
        contextMetadata.put("historyMessageCount", conversationMessages.size());
        contextMetadata.put("activeTools", toolSpecs.stream().map(ToolSpecification::name).toList());

        Map<String, Object> contextAssemblyPayload = new LinkedHashMap<>();
        contextAssemblyPayload.put("toolSpecifications", formatToolSpecifications(toolSpecs));
        contextAssemblyPayload.put("messages", formatMessagesForAudit(conversationMessages));

        AgentStepEvent contextEvent = AgentStepEvent.of(
                sessionId,
                currentStepNumber++,
                StepType.CONTEXT_ASSEMBLY,
                "Context Window & Tools Assembly",
                contextSummary,
                toJson(contextAssemblyPayload),
                TokenUsageStats.empty(),
                contextMetadata
        );
        publishStep(session, contextEvent);

        // 3. Enter ReAct Loop
        int maxSteps = agentProperties.getMaxSteps();
        int loopIteration = 0;
        List<AgentStepEvent> turnStepEvents = new ArrayList<>();
        TokenUsageStats turnTotalStats = TokenUsageStats.empty();

        while (loopIteration < maxSteps) {
            loopIteration++;
            long requestStartTime = System.currentTimeMillis();

            // Format full request payload sent to Gemini including context & tool schemas
            Map<String, Object> modelRequestPayload = new LinkedHashMap<>();
            modelRequestPayload.put("model", session.getModelName());
            modelRequestPayload.put("thinkingLevel", session.getThinkingLevel());
            modelRequestPayload.put("temperature", session.getTemperature());
            if (!toolSpecs.isEmpty()) {
                modelRequestPayload.put("toolSpecifications", formatToolSpecifications(toolSpecs));
            }
            modelRequestPayload.put("messages", formatMessagesForAudit(conversationMessages));

            // Execute LLM Generation with LangChain4j 1.0 ChatRequest
            dev.langchain4j.model.chat.request.ChatRequest chatRequest;
            if (toolSpecs.isEmpty()) {
                chatRequest = dev.langchain4j.model.chat.request.ChatRequest.builder()
                        .messages(conversationMessages)
                        .build();
            } else {
                chatRequest = dev.langchain4j.model.chat.request.ChatRequest.builder()
                        .messages(conversationMessages)
                        .parameters(dev.langchain4j.model.chat.request.ChatRequestParameters.builder()
                                .toolSpecifications(toolSpecs)
                                .build())
                        .build();
            }

            ChatModel activeModel = (dynamicChatModelService != null)
                    ? dynamicChatModelService.getModel(session.getModelName())
                    : this.chatModel;

            dev.langchain4j.model.chat.response.ChatResponse response;
            try {
                response = activeModel.chat(chatRequest);
            } catch (Exception e) {
                log.error("LLM Generation failed on iteration {}: {}", loopIteration, e.getMessage(), e);
                AgentStepEvent errorEvent = AgentStepEvent.of(
                        sessionId,
                        currentStepNumber++,
                        StepType.AGENT_ERROR,
                        "LLM Execution Error (Iteration " + loopIteration + ")",
                        "Failed to call Gemini API: " + e.getMessage(),
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
            int inputTokens = tokenUsage != null ? tokenUsage.inputTokenCount() : 0;
            int outputTokens = tokenUsage != null ? tokenUsage.outputTokenCount() : 0;

            // Capture cached prompt tokens (real Gemini API or mock simulation)
            int cachedTokens = com.workshop.agent.logging.GeminiResponseCapture.consumeLastCachedTokens();
            if (cachedTokens == 0 && inputTokens > 0) {
                if (activeModel instanceof MockLanguageModel) {
                    if (loopIteration > 1 || session.getMessages().size() > 1) {
                        cachedTokens = Math.max(0, (int) (inputTokens * 0.75));
                    }
                }
            }

            TokenUsageStats stepStats = TokenUsageStats.of(inputTokens, outputTokens, cachedTokens, requestDuration);
            turnTotalStats = turnTotalStats.add(stepStats);

            AiMessage aiMessage = response.aiMessage();

            // Check if Model Decided to Execute Tools
            if (aiMessage.hasToolExecutionRequests()) {
                List<ToolExecutionRequest> toolRequests = aiMessage.toolExecutionRequests();
                List<Object> formattedToolRequests = toolRequests.stream()
                        .map(this::formatToolExecutionRequest)
                        .toList();

                Map<String, Object> unifiedPayload = new LinkedHashMap<>();
                unifiedPayload.put("request", modelRequestPayload);
                unifiedPayload.put("response", Map.of(
                        "text", aiMessage.text() != null ? aiMessage.text() : "",
                        "toolRequests", formattedToolRequests
                ));

                rawPayloadAuditList.add(Map.of(
                        "iteration", loopIteration,
                        "requestPayload", modelRequestPayload,
                        "responseAiMessage", formatMessageForAudit(aiMessage),
                        "tokenUsage", tokenUsage != null ? tokenUsage.toString() : "N/A"
                ));

                String requestJson = toJson(modelRequestPayload);
                String responseJson = toJson(Map.of(
                        "text", aiMessage.text() != null ? aiMessage.text() : "",
                        "toolRequests", formattedToolRequests
                ));

                String cachedSummary = stepStats.cachedTokens() > 0
                        ? " [" + stepStats.cachedTokens() + " cached / " + stepStats.cacheHitPercentage() + "% hit]"
                        : "";
                String stepSummary = String.format("Sent context (%d messages, %d tools). Model requested: %s%s",
                        conversationMessages.size(), toolSpecs.size(), toolRequests.stream().map(ToolExecutionRequest::name).toList(), cachedSummary);

                AgentStepEvent reasoningEvent = AgentStepEvent.of(
                        sessionId,
                        currentStepNumber++,
                        StepType.TOOL_CALL_REQUEST,
                        "LLM Reasoning (Iteration " + loopIteration + "): Decided to Execute " + toolRequests.size() + " Tool(s)",
                        stepSummary,
                        toJson(unifiedPayload),
                        requestJson,
                        responseJson,
                        stepStats,
                        Map.of(
                                "iteration", loopIteration,
                                "model", session.getModelName(),
                                "thinkingLevel", session.getThinkingLevel(),
                                "toolCount", toolRequests.size(),
                                "cachedTokens", stepStats.cachedTokens(),
                                "text", aiMessage.text() != null ? aiMessage.text() : ""
                        )
                );
                publishStep(session, reasoningEvent);
                turnStepEvents.add(reasoningEvent);

                // Add assistant tool request to conversation messages
                conversationMessages.add(aiMessage);

                // Execute each requested tool
                for (ToolExecutionRequest toolRequest : toolRequests) {
                    Object parsedArgs;
                    try {
                        parsedArgs = objectMapper.readTree(toolRequest.arguments());
                    } catch (Exception e) {
                        parsedArgs = toolRequest.arguments();
                    }

                    AgentStepEvent execStartEvent = AgentStepEvent.of(
                            sessionId,
                            currentStepNumber++,
                            StepType.TOOL_EXECUTION,
                            "Executing Java Tool: " + toolRequest.name() + "()",
                            "Arguments: " + toolRequest.arguments(),
                            toJson(Map.of("tool", toolRequest.name(), "arguments", parsedArgs)),
                            TokenUsageStats.empty(),
                            Map.of("toolName", toolRequest.name())
                    );
                    publishStep(session, execStartEvent);
                    turnStepEvents.add(execStartEvent);

                    // Local Method Dispatch
                    ToolRegistry.ToolExecutionResult execResult = toolRegistry.execute(toolRequest);
                    String resultText = execResult.isSuccess() ? execResult.resultText() : "ERROR: " + execResult.errorMessage();

                    AgentStepEvent obsEvent = AgentStepEvent.of(
                            sessionId,
                            currentStepNumber++,
                            StepType.TOOL_OBSERVATION,
                            "Tool Observation: " + toolRequest.name(),
                            "Execution took " + execResult.executionTimeMs() + " ms. Output length: " + resultText.length() + " chars.",
                            resultText,
                            TokenUsageStats.of(0, 0, execResult.executionTimeMs()),
                            Map.of("success", execResult.isSuccess(), "durationMs", execResult.executionTimeMs())
                    );
                    publishStep(session, obsEvent);
                    turnStepEvents.add(obsEvent);

                    // Create observation message to feed back to LLM
                    ToolExecutionResultMessage toolResultMsg = ToolExecutionResultMessage.from(toolRequest, resultText);
                    conversationMessages.add(toolResultMsg);
                    rawPayloadAuditList.add(Map.of("toolObservation", formatMessageForAudit(toolResultMsg)));
                }

                // Loop continues to let model reason over observations!
            } else {
                // Final Synthesis Reached!
                String finalText = aiMessage.text() != null ? aiMessage.text() : "(No text response generated)";
                String formattedHtml = markdownRenderer.renderHtml(finalText);

                Map<String, Object> unifiedPayload = new LinkedHashMap<>();
                unifiedPayload.put("request", modelRequestPayload);
                unifiedPayload.put("response", Map.of(
                        "text", finalText
                ));

                rawPayloadAuditList.add(Map.of(
                        "iteration", loopIteration,
                        "requestPayload", modelRequestPayload,
                        "responseAiMessage", formatMessageForAudit(aiMessage),
                        "tokenUsage", tokenUsage != null ? tokenUsage.toString() : "N/A"
                ));

                String requestJson = toJson(modelRequestPayload);
                String responseJson = toJson(Map.of(
                        "finalAnswer", finalText
                ));

                String cachedSummary = stepStats.cachedTokens() > 0
                        ? " [" + stepStats.cachedTokens() + " cached / " + stepStats.cacheHitPercentage() + "% hit]"
                        : "";
                String finalSummary = String.format("Sent context (%d messages, %d tools). Completed reasoning cycle in %d iteration(s).%s",
                        conversationMessages.size(), toolSpecs.size(), loopIteration, cachedSummary);

                AgentStepEvent finalEvent = AgentStepEvent.of(
                        sessionId,
                        currentStepNumber++,
                        StepType.FINAL_SYNTHESIS,
                        "LLM Reasoning (Iteration " + loopIteration + "): Final Answer Synthesized",
                        finalSummary,
                        toJson(unifiedPayload),
                        requestJson,
                        responseJson,
                        stepStats,
                        Map.of(
                                "iteration", loopIteration,
                                "model", session.getModelName(),
                                "thinkingLevel", session.getThinkingLevel(),
                                "cachedTokens", stepStats.cachedTokens(),
                                "finalTextLength", finalText.length()
                        )
                );
                publishStep(session, finalEvent);
                turnStepEvents.add(finalEvent);

                String assistantMsgId = UUID.randomUUID().toString();
                ChatMessageDto assistantDto = ChatMessageDto.assistant(
                        assistantMsgId,
                        finalText,
                        formattedHtml,
                        turnStepEvents,
                        turnTotalStats
                );
                session.addMessage(assistantDto);

                // Save full audit trail to disk
                sessionDiskLogger.saveSessionSnapshot(session, rawPayloadAuditList);
                return assistantDto;
            }
        }

        // Fallback if max steps reached
        String fallbackMsg = "Agent reached maximum allowed reasoning steps (" + maxSteps + ") without reaching final conclusion.";
        ChatMessageDto fallbackDto = ChatMessageDto.assistant(
                UUID.randomUUID().toString(),
                fallbackMsg,
                "<p class='text-danger'>" + fallbackMsg + "</p>",
                turnStepEvents,
                turnTotalStats
        );
        session.addMessage(fallbackDto);
        sessionDiskLogger.saveSessionSnapshot(session, rawPayloadAuditList);
        return fallbackDto;
    }

    public void publishStep(AgentSession session, AgentStepEvent event) {
        session.addStepEvent(event);
        consoleAnsiLogger.logStep(event);
        sessionDiskLogger.appendStepEvent(session, event);

        // Notify SSE listeners
        Set<Consumer<AgentStepEvent>> listeners = sessionListeners.get(session.getId());
        if (listeners != null) {
            for (Consumer<AgentStepEvent> listener : listeners) {
                try {
                    listener.accept(event);
                } catch (Exception e) {
                    log.debug("Error delivering step event to listener: {}", e.getMessage());
                }
            }
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
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
        } else if (element instanceof dev.langchain4j.model.chat.request.json.JsonEnumSchema e) {
            map.put("type", "string");
            if (e.description() != null) map.put("description", e.description());
            if (e.enumValues() != null) map.put("enum", e.enumValues());
        } else {
            map.put("type", "string");
        }
        return map;
    }

    public Object formatToolExecutionRequest(ToolExecutionRequest req) {
        if (req == null) return null;
        Map<String, Object> map = new LinkedHashMap<>();
        if (req.id() != null && !req.id().isBlank()) {
            map.put("id", req.id());
        }
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

    private String truncate(String str, int max) {
        if (str == null) return "";
        if (str.length() <= max) return str;
        return str.substring(0, max) + "...";
    }
}
