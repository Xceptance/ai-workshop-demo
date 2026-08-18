package com.workshop.agent.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Educational Tool: Calculator
 *
 * Demonstrates deterministic mathematical computations, parameter validation,
 * and how LangChain4j maps Java method parameters to JSON Schema descriptions for the LLM.
 */
@Component
public class CalculatorTool {

    private static final Logger log = LoggerFactory.getLogger(CalculatorTool.class);

    @Tool("Performs basic arithmetic operations: ADD, SUBTRACT, MULTIPLY, DIVIDE on two numbers.")
    public double calculate(
            @P("The mathematical operation to perform: ADD, SUBTRACT, MULTIPLY, or DIVIDE") String operation,
            @P("The first operand (a)") double a,
            @P("The second operand (b)") double b
    ) {
        log.info("[TOOL:Calculator] calculate(operation={}, a={}, b={})", operation, a, b);
        return switch (operation.toUpperCase().trim()) {
            case "ADD", "+" -> a + b;
            case "SUBTRACT", "-" -> a - b;
            case "MULTIPLY", "*" -> a * b;
            case "DIVIDE", "/" -> {
                if (b == 0) {
                    throw new IllegalArgumentException("Division by zero is undefined.");
                }
                yield a / b;
            }
            default -> throw new IllegalArgumentException("Unsupported operation: " + operation + ". Use ADD, SUBTRACT, MULTIPLY, or DIVIDE.");
        };
    }

    @Tool("Computes power (base raised to the exponent) or square root.")
    public double powerOrRoot(
            @P("The type of computation: 'POWER' (base^exp) or 'SQRT' (square root of base)") String type,
            @P("The base number") double base,
            @P("The exponent (required for POWER, ignored for SQRT)") double exponent
    ) {
        log.info("[TOOL:Calculator] powerOrRoot(type={}, base={}, exponent={})", type, base, exponent);
        return switch (type.toUpperCase().trim()) {
            case "POWER", "POW" -> Math.pow(base, exponent);
            case "SQRT", "ROOT" -> {
                if (base < 0) {
                    throw new IllegalArgumentException("Cannot compute square root of negative number: " + base);
                }
                yield Math.sqrt(base);
            }
            default -> throw new IllegalArgumentException("Unsupported type: " + type + ". Use POWER or SQRT.");
        };
    }
}
