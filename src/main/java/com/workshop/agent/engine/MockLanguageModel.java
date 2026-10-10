package com.workshop.agent.engine;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

/**
 * Educational Mock LLM for offline workshop testing and demonstration when no Gemini API key is configured.
 * Simulates tool-calling decisions and final answer synthesis. Compatible with LangChain4j 1.22.0.
 */
public class MockLanguageModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(MockLanguageModel.class);

    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
        List<ChatMessage> messages = chatRequest.messages();
        ChatMessage lastMessage = messages.get(messages.size() - 1);

        // If the last message was a tool result, synthesize final answer
        if (lastMessage instanceof ToolExecutionResultMessage toolResult) {
            String finalAnswer = String.format(
                    "Based on the tool observation [%s: %s], here is the result: The requested operation completed successfully with observation: %s",
                    toolResult.toolName(),
                    toolResult.text(),
                    toolResult.text()
            );
            return ChatResponse.builder()
                    .aiMessage(AiMessage.builder()
                            .text(finalAnswer)
                            .thinking("Observed tool execution result for '" + toolResult.toolName() + "': " + toolResult.text() + ".\nSynthesizing final concise answer for the user.")
                            .build())
                    .tokenUsage(new TokenUsage(85, 45, 130))
                    .build();
        }

        // Check user prompt
        String userText = "";
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage u) {
                userText = u.singleText().toLowerCase();
                break;
            }
        }

        // Check available tool names
        List<String> availableToolNames = List.of();
        if (chatRequest.parameters() != null && chatRequest.parameters().toolSpecifications() != null) {
            availableToolNames = chatRequest.parameters().toolSpecifications().stream()
                    .map(ToolSpecification::name)
                    .toList();
        }

        // 1. Weather Queries
        if (userText.contains("weather") || userText.contains("forecast") || userText.contains("temperature") || userText.contains("rain")) {
            if (availableToolNames.contains("get_current_weather")) {
                String loc = "Berlin";
                if (userText.contains("tokyo")) loc = "Tokyo";
                else if (userText.contains("paris")) loc = "Paris";
                else if (userText.contains("new york")) loc = "New York";
                else if (userText.contains("london")) loc = "London";

                ToolExecutionRequest req = ToolExecutionRequest.builder()
                        .id(UUID.randomUUID().toString())
                        .name("get_current_weather")
                        .arguments("{\"location\": \"" + loc + "\"}")
                        .build();
                return ChatResponse.builder()
                        .aiMessage(AiMessage.builder()
                                .toolExecutionRequests(List.of(req))
                                .thinking("User is asking for weather information in " + loc + ". Invoking tool 'get_current_weather' to retrieve accurate atmospheric conditions.")
                                .build())
                        .tokenUsage(new TokenUsage(110, 30, 140))
                        .build();
            }
        }

        // 2. Calculations
        if (userText.contains("calculate") || userText.contains("+") || userText.contains("*") || userText.contains("multiply") || userText.contains("add")) {
            String toolName = availableToolNames.contains("calculate") ? "calculate" : "calculate";
            ToolExecutionRequest req = ToolExecutionRequest.builder()
                    .id(UUID.randomUUID().toString())
                    .name(toolName)
                    .arguments("{\"expression\": \"42 * 8\", \"operation\": \"MULTIPLY\", \"a\": 42, \"b\": 8}")
                    .build();
            return ChatResponse.builder()
                    .aiMessage(AiMessage.builder()
                            .toolExecutionRequests(List.of(req))
                            .thinking("User submitted an arithmetic calculation. Delegating calculation to deterministic 'calculate' tool.")
                            .build())
                    .tokenUsage(new TokenUsage(120, 30, 150))
                    .build();
        }

        // 3. System Info & Date/Time
        if (userText.contains("time") || userText.contains("date") || userText.contains("system") || userText.contains("os") || userText.contains("memory")) {
            String toolName = "getCurrentDateTime";
            if (availableToolNames.contains("get_system_info")) {
                toolName = "get_system_info";
            } else if (availableToolNames.contains("get_current_time")) {
                toolName = "get_current_time";
            } else if (availableToolNames.contains("getHostRuntimeMetrics")) {
                toolName = "getHostRuntimeMetrics";
            }

            ToolExecutionRequest req = ToolExecutionRequest.builder()
                    .id(UUID.randomUUID().toString())
                    .name(toolName)
                    .arguments("{\"timezone\": \"UTC\"}")
                    .build();
            return ChatResponse.builder()
                    .aiMessage(AiMessage.builder()
                            .toolExecutionRequests(List.of(req))
                            .thinking("User queried system or temporal metrics. Invoking tool '" + toolName + "' to inspect host state.")
                            .build())
                    .tokenUsage(new TokenUsage(90, 25, 115))
                    .build();
        }

        // 4. File / Directory Inspection
        if (userText.contains("file") || userText.contains("dir") || userText.contains("read") || userText.contains("workspace")) {
            String toolName = availableToolNames.contains("list_directory") ? "list_directory" : "listDirectory";
            ToolExecutionRequest req = ToolExecutionRequest.builder()
                    .id(UUID.randomUUID().toString())
                    .name(toolName)
                    .arguments("{\"relativePath\": \".\"}")
                    .build();
            return ChatResponse.builder()
                    .aiMessage(AiMessage.builder()
                            .toolExecutionRequests(List.of(req))
                            .thinking("User asked for repository filesystem contents. Invoking '" + toolName + "' to inspect directory structure.")
                            .build())
                    .tokenUsage(new TokenUsage(110, 35, 145))
                    .build();
        }

        // 5. Search
        if (userText.contains("search") || userText.contains("who") || userText.contains("what is")) {
            String toolName = availableToolNames.contains("search_workspace") ? "search_workspace" : "searchWeb";
            ToolExecutionRequest req = ToolExecutionRequest.builder()
                    .id(UUID.randomUUID().toString())
                    .name(toolName)
                    .arguments("{\"query\": \"" + userText.replace("\"", "") + "\"}")
                    .build();
            return ChatResponse.builder()
                    .aiMessage(AiMessage.builder()
                            .toolExecutionRequests(List.of(req))
                            .thinking("User initiated an external knowledge query. Calling search tool '" + toolName + "'.")
                            .build())
                    .tokenUsage(new TokenUsage(100, 30, 130))
                    .build();
        }

        // Default direct response
        String reply = "Hello! I am running in Offline Educational Mock Mode. " +
                "Try asking me to calculate something (e.g. 'calculate 42 * 8'), check the system time ('what time is it?'), " +
                "list workspace files ('list directory'), or search ('search weather in Berlin') to see how tool calling works!";
        return ChatResponse.builder()
                .aiMessage(AiMessage.builder()
                        .text(reply)
                        .thinking("Determined user query is a general greeting or prompt. Direct informative answer suffices without tool invocation.")
                        .build())
                .tokenUsage(new TokenUsage(50, 40, 90))
                .build();
    }
}
