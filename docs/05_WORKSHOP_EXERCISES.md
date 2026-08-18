# 05. Hands-On Workshop Exercises (With Solutions)

This guide contains 5 progressive coding exercises designed for workshop attendees.

---

## Exercise 1: Build a Custom Currency Converter Tool

### 🎯 Objective:
Create a new Spring component `CurrencyConverterTool.java` annotated with `@Tool` that converts amounts between USD, EUR, GBP, and JPY.

### Starter Template (`src/main/java/com/workshop/agent/tools/CurrencyConverterTool.java`):
```java
package com.workshop.agent.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

@Component
public class CurrencyConverterTool {

    // TODO: Annotate with @Tool and implement conversion rates
    public String convertCurrency(
            String fromCurrency,
            String toCurrency,
            double amount
    ) {
        // TODO: Implement conversion logic
        return "";
    }
}
```

### ✅ Solution:
```java
package com.workshop.agent.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class CurrencyConverterTool {

    private static final Map<String, Double> USD_RATES = Map.of(
            "USD", 1.0,
            "EUR", 0.92,
            "GBP", 0.78,
            "JPY", 155.40
    );

    @Tool("Converts a monetary amount between currencies (USD, EUR, GBP, JPY).")
    public String convertCurrency(
            @P("The source 3-letter currency code (e.g. USD, EUR)") String fromCurrency,
            @P("The target 3-letter currency code (e.g. JPY, GBP)") String toCurrency,
            @P("The numeric amount to convert") double amount
    ) {
        String from = fromCurrency.toUpperCase().trim();
        String to = toCurrency.toUpperCase().trim();

        if (!USD_RATES.containsKey(from) || !USD_RATES.containsKey(to)) {
            return "Unsupported currency. Supported currencies: " + USD_RATES.keySet();
        }

        double amountInUsd = amount / USD_RATES.get(from);
        double converted = amountInUsd * USD_RATES.get(to);

        return String.format("%.2f %s is approximately %.2f %s", amount, from, converted, to);
    }
}
```
*Don't forget to register it in `ToolRegistry.java` constructor if needed, or allow Spring autowiring!*

---

## Exercise 2: Implementing a Context Window Sliding Limit

### 🎯 Objective:
Modify `TransparentAgentEngine.java` to prevent context explosion by retaining only the last 6 messages when constructing the LLM request payload.

### ✅ Solution in `TransparentAgentEngine.java`:
```java
// Keep system prompt + last N messages
int maxHistoryToKeep = 6;
List<ChatMessageDto> allMsgs = session.getMessages();
List<ChatMessageDto> recentMsgs = allMsgs.size() > maxHistoryToKeep
        ? allMsgs.subList(allMsgs.size() - maxHistoryToKeep, allMsgs.size())
        : allMsgs;

for (ChatMessageDto prev : recentMsgs) {
    if ("user".equalsIgnoreCase(prev.role()) && !prev.id().equals(userMsgId)) {
        conversationMessages.add(UserMessage.from(prev.content()));
    } else if ("assistant".equalsIgnoreCase(prev.role())) {
        conversationMessages.add(AiMessage.from(prev.content()));
    }
}
```

---

## Exercise 3: Adding Human-in-the-Loop Confirmation for High-Risk Tools

### 🎯 Objective:
Create a tool execution filter that detects high-risk actions (e.g., file writes or system shutdowns) and requires explicit human approval before proceeding.

### Concept:
In `ToolRegistry.java`:
```java
if (toolName.startsWith("delete") || toolName.startsWith("write")) {
    // Check if user confirmed
    if (!isConfirmedByHuman(request)) {
        return new ToolExecutionResult(toolName, argumentsJson, 
            "HALTED: This action requires human confirmation. Please ask the user for confirmation.", 
            0, false, "Awaiting confirmation");
    }
}
```

---

## Exercise 4: Inspecting Disk Logs & Calculating Total Workshop Cost

### 🎯 Objective:
Open the generated `./logs/sessions/` directory in your terminal. Write a short bash one-liner or Python snippet to sum the total tokens used across all sessions.

```bash
# Sum total tokens across all session summaries
jq -s 'map(.tokenStats.totalTokens) | add' logs/sessions/*/summary.json
```

---

## Exercise 5: Multi-Step Chained Reasoning Challenge

### 🎯 Objective:
Submit this multi-step query into the Agent UI:
> *"What is the current server time in UTC? Then calculate what 24 * 365 is. Finally, list what documentation files exist in the 'docs' directory."*

Watch the right-hand **Glass-Box Inspector** execute:
1. `getCurrentDateTime(timezone="UTC")`
2. `calculate(operation="MULTIPLY", a=24, b=365)`
3. `listDirectory(relativePath="docs")`
4. Synthesize all three observations into one coherent final response!
