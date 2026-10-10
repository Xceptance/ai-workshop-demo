package com.workshop.agent.model;

import java.time.Duration;

/**
 * Tracks granular token usage and latency metrics per step and across entire sessions.
 */
public record TokenUsageStats(
        int inputTokens,
        int outputTokens,
        int cachedTokens,
        int totalTokens,
        long durationMs,
        double estimatedCostUsd
) {
    public static TokenUsageStats empty() {
        return new TokenUsageStats(0, 0, 0, 0, 0, 0.0);
    }

    public static TokenUsageStats of(int input, int output, long durationMs) {
        return of(input, output, 0, durationMs);
    }

    public static TokenUsageStats of(int input, int output, int cached, long durationMs) {
        int total = input + output;
        // Gemini Flash ballpark pricing: ~$0.075 / 1M input, ~$0.01875 / 1M cached prompt (~75% cache discount), ~$0.30 / 1M output
        int nonCachedInput = Math.max(0, input - cached);
        double promptCost = (nonCachedInput / 1_000_000.0) * 0.075;
        double cachedCost = (cached / 1_000_000.0) * 0.01875;
        double completionCost = (output / 1_000_000.0) * 0.30;
        double cost = promptCost + cachedCost + completionCost;
        return new TokenUsageStats(input, output, cached, total, durationMs, cost);
    }

    public TokenUsageStats add(TokenUsageStats other) {
        if (other == null) return this;
        return new TokenUsageStats(
                this.inputTokens + other.inputTokens,
                this.outputTokens + other.outputTokens,
                this.cachedTokens + other.cachedTokens,
                this.totalTokens + other.totalTokens,
                this.durationMs + other.durationMs,
                this.estimatedCostUsd + other.estimatedCostUsd
        );
    }

    public boolean hasCachedTokens() {
        return cachedTokens > 0;
    }

    public int cacheHitPercentage() {
        if (inputTokens <= 0) return 0;
        return (int) Math.round((cachedTokens * 100.0) / inputTokens);
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
