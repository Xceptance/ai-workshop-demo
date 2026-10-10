package com.workshop.energy.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.workshop.energy.domain.EnergyMarketEngine;
import com.workshop.energy.domain.MarketModels;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class EnergyMarketResources {

    private final EnergyMarketEngine engine;
    private final ObjectMapper objectMapper;

    public EnergyMarketResources(EnergyMarketEngine engine, ObjectMapper objectMapper) {
        this.engine = engine;
        this.objectMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
    }

    @McpResource(
            uri = "energy://market/prices/today",
            name = "Today Market Prices",
            description = "Real-time snapshot of today's hourly spot electricity prices and market trend for DE-LU",
            mimeType = "application/json"
    )
    public String getTodayPrices() {
        try {
            MarketModels.SpotPriceResult result = engine.calculateSpotPrices("DE-LU", LocalDate.now().toString());
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    @McpResource(
            uri = "energy://market/prices/history/de-lu",
            name = "Historical Prices (DE-LU)",
            description = "Recent 7-day historical electricity price overview for bidding zone DE-LU",
            mimeType = "application/json"
    )
    public String getHistoricalPricesDeLu() {
        try {
            LocalDate end = LocalDate.now();
            LocalDate start = end.minusDays(7);
            MarketModels.HistoricalPricesResult result = engine.calculateHistoricalPrices("DE-LU", start.toString(), end.toString());
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    @McpResource(
            uri = "energy://grid/balance/de-lu",
            name = "Grid Balance (DE-LU)",
            description = "Live status snapshot of renewable generation share and grid balance condition in DE-LU",
            mimeType = "application/json"
    )
    public String getGridBalanceDeLu() {
        try {
            MarketModels.GridBalanceSnapshot snapshot = engine.getGridBalanceSnapshot("DE-LU");
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }
}
