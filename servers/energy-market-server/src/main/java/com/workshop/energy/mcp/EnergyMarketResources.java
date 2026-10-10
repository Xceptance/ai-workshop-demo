package com.workshop.energy.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workshop.energy.domain.*;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class EnergyMarketResources {

    private final EnergyMarketEngine engine;
    private final ObjectMapper objectMapper;

    public EnergyMarketResources(EnergyMarketEngine engine, ObjectMapper objectMapper) {
        this.engine = engine;
        this.objectMapper = objectMapper;
    }

    @McpResource(
            uri = "energy://market/prices/today",
            name = "Today's Spot Electricity Prices",
            description = "Real-time snapshot of today's hourly spot electricity prices for DE-LU in JSON",
            mimeType = "application/json"
    )
    public String getTodayPricesResource() {
        try {
            DailyPriceSummary summary = engine.getDailyPriceSummary(BiddingZone.DE_LU, LocalDate.now());
            return objectMapper.writeValueAsString(summary);
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    @McpResource(
            uri = "energy://market/prices/history/de-lu",
            name = "Recent Spot Price History",
            description = "Multi-day historical spot electricity price points for DE-LU in JSON",
            mimeType = "application/json"
    )
    public String getHistoricalPricesResource() {
        try {
            LocalDate end = LocalDate.now();
            LocalDate start = end.minusDays(6);
            List<DailyPriceSummary> history = engine.getHistoricalPrices(BiddingZone.DE_LU, start, end);
            return objectMapper.writeValueAsString(history);
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    @McpResource(
            uri = "energy://grid/balance/de-lu",
            name = "Grid Balance and Renewable Status",
            description = "Live renewable generation (solar, wind) and load conditions for DE-LU in JSON",
            mimeType = "application/json"
    )
    public String getGridBalanceResource() {
        try {
            List<RenewableForecast> forecast = engine.getRenewableForecast(BiddingZone.DE_LU, LocalDate.now());
            return objectMapper.writeValueAsString(forecast);
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }
}
