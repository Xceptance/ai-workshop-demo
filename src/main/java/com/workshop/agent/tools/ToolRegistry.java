package com.workshop.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Discovers and registers all Spring-managed `@Tool` components.
 * Generates tool specifications and provides reflection-based execution dispatch with timing.
 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    public record ToolDto(
            String name,
            String targetClassName,
            String description,
            String parameterSchema
    ) {}

    public record RegisteredTool(
            Object targetInstance,
            Method method,
            ToolSpecification specification,
            String description
    ) {}

    public record ToolExecutionResult(
            String toolName,
            String argumentsJson,
            String resultText,
            long executionTimeMs,
            boolean isSuccess,
            String errorMessage
    ) {}

    private final Map<String, RegisteredTool> toolsByName = new LinkedHashMap<>();
    private final List<Object> toolBeans = new ArrayList<>();
    private final ObjectMapper objectMapper;

    public ToolRegistry(
            CalculatorTool calculatorTool,
            SystemInfoTool systemInfoTool,
            FileInspectionTool fileInspectionTool,
            WebSearchMockTool webSearchMockTool,
            ObjectMapper objectMapper
    ) {
        this.objectMapper = objectMapper;
        registerToolBean(calculatorTool);
        registerToolBean(systemInfoTool);
        registerToolBean(fileInspectionTool);
        registerToolBean(webSearchMockTool);
    }

    public void registerToolBean(Object bean) {
        toolBeans.add(bean);
        List<ToolSpecification> specs = ToolSpecifications.toolSpecificationsFrom(bean.getClass());

        for (Method method : bean.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(Tool.class)) {
                Tool toolAnnotation = method.getAnnotation(Tool.class);
                String toolName = method.getName();
                String desc = toolAnnotation.value().length > 0 ? String.join(" ", toolAnnotation.value()) : toolAnnotation.name();

                // Find corresponding LangChain4j spec
                ToolSpecification matchingSpec = specs.stream()
                        .filter(s -> s.name().equals(toolName))
                        .findFirst()
                        .orElse(null);

                toolsByName.put(toolName, new RegisteredTool(bean, method, matchingSpec, desc));
                log.info("Registered Agent Tool: [{}] -> {}.{}()", toolName, bean.getClass().getSimpleName(), method.getName());
            }
        }
    }

    public List<ToolSpecification> getAllSpecifications() {
        return toolsByName.values().stream()
                .map(RegisteredTool::specification)
                .filter(Objects::nonNull)
                .toList();
    }

    public List<RegisteredTool> getRegisteredTools() {
        return new ArrayList<>(toolsByName.values());
    }

    public List<ToolDto> getToolDtos() {
        return toolsByName.values().stream()
                .map(r -> new ToolDto(
                        r.method().getName(),
                        r.targetInstance().getClass().getSimpleName(),
                        r.description(),
                        r.specification() != null ? formatSchemaForDisplay(r.specification()) : "{}"
                ))
                .toList();
    }

    public String formatSchemaForDisplay(ToolSpecification spec) {
        if (spec == null || spec.parameters() == null) return "{}";
        try {
            Map<String, Object> schemaMap = formatSchemaElement(spec.parameters());
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(schemaMap);
        } catch (Exception e) {
            return spec.parameters().toString();
        }
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

    public List<Object> getToolBeans() {
        return new ArrayList<>(toolBeans);
    }

    public ToolExecutionResult execute(ToolExecutionRequest request) {
        String toolName = request.name();
        String argumentsJson = request.arguments();
        long startTime = System.currentTimeMillis();

        RegisteredTool registered = toolsByName.get(toolName);
        if (registered == null) {
            long duration = System.currentTimeMillis() - startTime;
            String error = "Tool not found: '" + toolName + "'. Available tools: " + toolsByName.keySet();
            log.error("[TOOL-EXEC-FAILED] {}", error);
            return new ToolExecutionResult(toolName, argumentsJson, null, duration, false, error);
        }

        try {
            Method method = registered.method();
            Object target = registered.targetInstance();
            Object[] args = parseArguments(method, argumentsJson);

            method.setAccessible(true);
            Object output = method.invoke(target, args);
            long duration = System.currentTimeMillis() - startTime;
            String resultStr = output != null ? output.toString() : "null";

            log.info("[TOOL-EXEC-SUCCESS] {} ({} ms) -> {}", toolName, duration, summarize(resultStr));
            return new ToolExecutionResult(toolName, argumentsJson, resultStr, duration, true, null);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            String error = "Error executing tool '" + toolName + "': " + cause.getMessage();
            log.error("[TOOL-EXEC-ERROR] {} ({} ms): {}", toolName, duration, error, cause);
            return new ToolExecutionResult(toolName, argumentsJson, null, duration, false, error);
        }
    }

    @SuppressWarnings("unchecked")
    private Object[] parseArguments(Method method, String argumentsJson) throws Exception {
        if (argumentsJson == null || argumentsJson.isBlank() || argumentsJson.equals("{}")) {
            if (method.getParameterCount() == 0) {
                return new Object[0];
            }
        }

        Map<String, Object> jsonMap = objectMapper.readValue(argumentsJson, Map.class);
        var parameters = method.getParameters();
        Object[] args = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            var param = parameters[i];
            String paramName = param.getName();
            Class<?> type = param.getType();

            Object rawValue = jsonMap.get(paramName);
            if (rawValue == null) {
                // Try case-insensitive matching if exact name fails
                for (Map.Entry<String, Object> entry : jsonMap.entrySet()) {
                    if (entry.getKey().equalsIgnoreCase(paramName)) {
                        rawValue = entry.getValue();
                        break;
                    }
                }
            }

            if (rawValue == null) {
                args[i] = getDefaultPrimitiveValue(type);
            } else {
                args[i] = convertValue(rawValue, type);
            }
        }
        return args;
    }

    private Object convertValue(Object raw, Class<?> targetType) {
        if (raw == null) return getDefaultPrimitiveValue(targetType);
        if (targetType.isAssignableFrom(raw.getClass())) return raw;

        if (targetType == String.class) {
            return raw.toString();
        } else if (targetType == int.class || targetType == Integer.class) {
            if (raw instanceof Number n) return n.intValue();
            return Integer.parseInt(raw.toString());
        } else if (targetType == long.class || targetType == Long.class) {
            if (raw instanceof Number n) return n.longValue();
            return Long.parseLong(raw.toString());
        } else if (targetType == double.class || targetType == Double.class) {
            if (raw instanceof Number n) return n.doubleValue();
            return Double.parseDouble(raw.toString());
        } else if (targetType == boolean.class || targetType == Boolean.class) {
            if (raw instanceof Boolean b) return b;
            return Boolean.parseBoolean(raw.toString());
        }
        return objectMapper.convertValue(raw, targetType);
    }

    private Object getDefaultPrimitiveValue(Class<?> type) {
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0;
        if (type == boolean.class) return false;
        return null;
    }

    private String summarize(String str) {
        if (str == null) return "null";
        if (str.length() <= 80) return str.replace("\n", " ");
        return str.substring(0, 77).replace("\n", " ") + "...";
    }
}
