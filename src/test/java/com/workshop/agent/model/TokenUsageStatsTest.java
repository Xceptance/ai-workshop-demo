package com.workshop.agent.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenUsageStatsTest {

    @Test
    void testCachedTokensAndHitPercentage() {
        TokenUsageStats stats = TokenUsageStats.of(1000, 200, 750, 1500L);

        assertEquals(1000, stats.inputTokens());
        assertEquals(200, stats.outputTokens());
        assertEquals(750, stats.cachedTokens());
        assertEquals(1200, stats.totalTokens());
        assertTrue(stats.hasCachedTokens());
        // 750 / 1000 = 75%
        assertEquals(75, stats.cacheHitPercentage());
    }

    @Test
    void testZeroCachedTokens() {
        TokenUsageStats stats = TokenUsageStats.of(500, 100, 0, 800L);

        assertEquals(0, stats.cachedTokens());
        assertFalse(stats.hasCachedTokens());
        assertEquals(0, stats.cacheHitPercentage());
    }

    @Test
    void testCostCalculationWithCacheDiscount() {
        // Full un-cached 1,000 input tokens: 1,000 * $0.000000075 = $0.000075
        TokenUsageStats noCache = TokenUsageStats.of(1000, 0, 0, 1000L);
        assertEquals(0.000075, noCache.estimatedCostUsd(), 0.000001);

        // 1,000 input tokens where 800 are cached:
        // nonCached = 200 * $0.000000075 = $0.000015
        // cached = 800 * $0.00000001875 = $0.000015
        // Total = $0.000030
        TokenUsageStats withCache = TokenUsageStats.of(1000, 0, 800, 1000L);
        assertEquals(0.000030, withCache.estimatedCostUsd(), 0.000001);
        assertTrue(withCache.estimatedCostUsd() < noCache.estimatedCostUsd());
    }

    @Test
    void testAccumulateWithCachedTokens() {
        TokenUsageStats s1 = TokenUsageStats.of(500, 50, 200, 1000L);
        TokenUsageStats s2 = TokenUsageStats.of(600, 70, 400, 1200L);

        TokenUsageStats accumulated = s1.add(s2);
        assertEquals(1100, accumulated.inputTokens());
        assertEquals(120, accumulated.outputTokens());
        assertEquals(600, accumulated.cachedTokens());
        assertEquals(1220, accumulated.totalTokens());
        assertEquals(2200L, accumulated.durationMs());
        // 600 / 1100 = 55%
        assertEquals(55, accumulated.cacheHitPercentage());
    }
}
