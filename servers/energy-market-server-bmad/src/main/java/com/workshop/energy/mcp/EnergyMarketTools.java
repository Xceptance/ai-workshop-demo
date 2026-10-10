package com.workshop.energy.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.workshop.energy.domain.EnergyMarketEngine;
import com.workshop.energy.domain.MarketModels;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class EnergyMarketTools {

    private final EnergyMarketEngine engine;
    private final ObjectMapper objectMapper;

    public EnergyMarketTools(EnergyMarketEngine engine, ObjectMapper objectMapper) {
        this.engine = engine;
        this.objectMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
    }

    @McpTool(
            name = "energy_get_spot_prices",
            description = "Query Day-Ahead and Intraday electricity spot prices in EUR/MWh for a European bidding zone (DE-LU, FR, NL, AT) on a specific date (YYYY-MM-DD)."
    )
    @Tool(
            name = "energy_get_spot_prices",
            description = "Query Day-Ahead and Intraday electricity spot prices in EUR/MWh for a European bidding zone (DE-LU, FR, NL, AT) on a specific date (YYYY-MM-DD)."
    )
    public String getSpotPrices(
            @McpToolParam(description = "European bidding zone code (DE-LU, FR, NL, AT). Defaults to DE-LU if omitted.", required = false)
            @ToolParam(description = "European bidding zone code (DE-LU, FR, NL, AT). Defaults to DE-LU if omitted.", required = false) String zone,
            @McpToolParam(description = "Target date in ISO format YYYY-MM-DD. Defaults to today if omitted.", required = false)
            @ToolParam(description = "Target date in ISO format YYYY-MM-DD. Defaults to today if omitted.", required = false) String date
    ) {
        try {
            MarketModels.SpotPriceResult result = engine.calculateSpotPrices(zone, date);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return "Error retrieving spot prices: " + e.getMessage();
        }
    }

    @McpTool(
            name = "energy_get_historical_prices",
            description = "Retrieve multi-day historical electricity spot prices across a date range (up to 90 days) for trend analysis and negative price detection."
    )
    @Tool(
            name = "energy_get_historical_prices",
            description = "Retrieve multi-day historical electricity spot prices across a date range (up to 90 days) for trend analysis and negative price detection."
    )
    public String getHistoricalPrices(
            @McpToolParam(description = "European bidding zone code (DE-LU, FR, NL, AT). Defaults to DE-LU if omitted.", required = false)
            @ToolParam(description = "European bidding zone code (DE-LU, FR, NL, AT). Defaults to DE-LU if omitted.", required = false) String zone,
            @McpToolParam(description = "Start date in ISO format YYYY-MM-DD.", required = true)
            @ToolParam(description = "Start date in ISO format YYYY-MM-DD.", required = true) String startDate,
            @McpToolParam(description = "End date in ISO format YYYY-MM-DD.", required = true)
            @ToolParam(description = "End date in ISO format YYYY-MM-DD.", required = true) String endDate
    ) {
        try {
            MarketModels.HistoricalPricesResult result = engine.calculateHistoricalPrices(zone, startDate, endDate);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return "Error retrieving historical prices: " + e.getMessage();
        }
    }

    @McpTool(
            name = "energy_get_renewable_forecast",
            description = "Retrieve hourly forecasts for solar PV, onshore wind, and offshore wind generation alongside expected electrical grid load."
    )
    @Tool(
            name = "energy_get_renewable_forecast",
            description = "Retrieve hourly forecasts for solar PV, onshore wind, and offshore wind generation alongside expected electrical grid load."
    )
    public String getRenewableForecast(
            @McpToolParam(description = "European bidding zone code (DE-LU, FR, NL, AT). Defaults to DE-LU if omitted.", required = false)
            @ToolParam(description = "European bidding zone code (DE-LU, FR, NL, AT). Defaults to DE-LU if omitted.", required = false) String zone,
            @McpToolParam(description = "Forecast date in ISO format YYYY-MM-DD. Defaults to today if omitted.", required = false)
            @ToolParam(description = "Forecast date in ISO format YYYY-MM-DD. Defaults to today if omitted.", required = false) String date
    ) {
        try {
            MarketModels.RenewableForecastResult result = engine.calculateRenewableForecast(zone, date);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return "Error retrieving renewable forecast: " + e.getMessage();
        }
    }

    @McpTool(
            name = "energy_evaluate_dispatch",
            description = "Calculate optimal charging, discharging, and load-shifting schedules for battery storage or flexible electrical assets based on spot market spreads."
    )
    @Tool(
            name = "energy_evaluate_dispatch",
            description = "Calculate optimal charging, discharging, and load-shifting schedules for battery storage or flexible electrical assets based on spot market spreads."
    )
    public String evaluateDispatch(
            @McpToolParam(description = "Total battery storage capacity in MWh.", required = true)
            @ToolParam(description = "Total battery storage capacity in MWh.", required = true) double capacityMwh,
            @McpToolParam(description = "Maximum charging and discharging power in MW.", required = true)
            @ToolParam(description = "Maximum charging and discharging power in MW.", required = true) double maxPowerMw,
            @McpToolParam(description = "Round-trip battery efficiency between 0.0 and 1.0 (e.g., 0.88 for 88%).", required = true)
            @ToolParam(description = "Round-trip battery efficiency between 0.0 and 1.0 (e.g., 0.88 for 88%).", required = true) double roundTripEfficiency,
            @McpToolParam(description = "Evaluation target date in ISO format YYYY-MM-DD. Defaults to today if omitted.", required = false)
            @ToolParam(description = "Evaluation target date in ISO format YYYY-MM-DD. Defaults to today if omitted.", required = false) String date
    ) {
        try {
            MarketModels.DispatchEvaluationResult result = engine.evaluateDispatch(capacityMwh, maxPowerMw, roundTripEfficiency, date);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return "Error evaluating asset dispatch: " + e.getMessage();
        }
    }
}
