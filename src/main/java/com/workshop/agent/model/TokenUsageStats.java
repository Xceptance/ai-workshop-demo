package com.workshop.agent.model;

import java.time.Duration;

/**
 * Tracks granular token usage and latency metrics per step and across entire sessions.
 */
public record TokenUsageStats(
        int inputTokens,
        int outputTokens,
        int totalTokens,
        long durationMs,
        double estimatedCostUsd
) {
    public static TokenUsageStats empty() {
        return new TokenUsageStats(0, 0, 0, 0, 0.0);
    }

    public static TokenUsageStats of(int input, int output, long durationMs) {
        int total = input + output;
        // Gemini 1.5/2.0/3.7 Flash ballpark pricing (~$0.075 / 1M prompt tokens, ~$0.30 / 1M completion tokens)
        double promptCost = (input / 1_000_000.0) * 0.075;
        double completionCost = (output / 1_000_000.0) * 0.30;
        double cost = promptCost + completionCost;
        return new TokenUsageStats(input, output, total, durationMs, cost);
    }

    public TokenUsageStats add(TokenUsageStats other) {
        if (other == null) return this;
        return new TokenUsageStats(
                this.inputTokens + other.inputTokens,
                this.outputTokens + other.outputTokens,
                this.totalTokens + other.totalTokens,
                this.durationMs + other.durationMs,
                this.estimatedCostUsd + other.estimatedCostUsd
        );
    }

    public String formattedDuration() {
        if (durationMs < 1000) {
            return durationMs + " ms";
        }
        return String.format("%.2f s", durationMs / 1000.0);
    }

    public String formattedCost() {
        if (estimatedCostUsd < 0.0001) {
            return String.format("< $0.0001");
        }
        return String.format("$%.5f", estimatedCostUsd);
    }
}
