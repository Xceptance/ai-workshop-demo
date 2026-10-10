package com.workshop.energy.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.workshop.energy.domain.*;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

@Component
public class EnergyMarketTools {

    private final EnergyMarketEngine marketEngine;
    private final ObjectMapper objectMapper;

    public EnergyMarketTools(EnergyMarketEngine marketEngine) {
        this.marketEngine = marketEngine;
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Tool(
            name = "energy_get_spot_prices",
            description = "Retrieve Day-Ahead and Intraday electricity spot prices in EUR/MWh for a European bidding zone (DE-LU, FR, NL, AT), including averages, extremes, and negative pricing occurrences."
    )
    public String getSpotPrices(
            @ToolParam(description = "Bidding zone code: DE-LU (Germany/Luxembourg), FR (France), NL (Netherlands), AT (Austria)", required = true) String biddingZone,
            @ToolParam(description = "Optional target date (ISO format YYYY-MM-DD), defaults to today", required = false) String date
    ) {
        try {
            BiddingZone zone = BiddingZone.fromString(biddingZone);
            LocalDate targetDate = (date != null && !date.isBlank()) ? LocalDate.parse(date.trim()) : LocalDate.now();
            DailyPriceSummary summary = marketEngine.getDailyPriceSummary(zone, targetDate);

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Spot Electricity Prices for %s on %s:\n", zone.getDescription(), summary.date()));
            sb.append(String.format("- Average Price: %.2f EUR/MWh\n", summary.averagePriceEurPerMWh()));
            sb.append(String.format("- Min Price: %.2f EUR/MWh (Negative Hours: %d)\n", summary.minPriceEurPerMWh(), summary.negativePriceHoursCount()));
            sb.append(String.format("- Max Price: %.2f EUR/MWh\n", summary.maxPriceEurPerMWh()));
            sb.append("\nHourly Curve (EUR/MWh):\n");
            for (SpotPricePoint point : summary.hourlyPrices()) {
                String flag = point.isNegative() ? " [NEGATIVE PRICE ALERT]" : "";
                sb.append(String.format("  Hour %02d:00: %6.2f EUR/MWh%s\n", point.hour(), point.priceEurPerMWh(), flag));
            }
            return sb.toString();
        } catch (IllegalArgumentException | DateTimeParseException e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(
            name = "energy_get_historical_prices",
            description = "Query historical electricity spot prices across a date range (YYYY-MM-DD to YYYY-MM-DD) for a European bidding zone, reporting daily averages and negative price hours."
    )
    public String getHistoricalPrices(
            @ToolParam(description = "Bidding zone code (e.g. DE-LU, FR, NL, AT)", required = true) String biddingZone,
            @ToolParam(description = "Start date in ISO format YYYY-MM-DD", required = true) String startDate,
            @ToolParam(description = "End date in ISO format YYYY-MM-DD", required = true) String endDate
    ) {
        try {
            BiddingZone zone = BiddingZone.fromString(biddingZone);
            LocalDate start = LocalDate.parse(startDate.trim());
            LocalDate end = LocalDate.parse(endDate.trim());

            List<DailyPriceSummary> history = marketEngine.getHistoricalPrices(zone, start, end);
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Historical Spot Prices for %s from %s to %s (%d days):\n",
                    zone.getDescription(), start, end, history.size()));

            double totalSum = 0;
            int totalNegHours = 0;
            for (DailyPriceSummary day : history) {
                totalSum += day.averagePriceEurPerMWh();
                totalNegHours += day.negativePriceHoursCount();
                sb.append(String.format("  - %s: Avg: %6.2f EUR/MWh (Range: %6.2f to %6.2f, Neg Hours: %d)\n",
                        day.date(), day.averagePriceEurPerMWh(), day.minPriceEurPerMWh(), day.maxPriceEurPerMWh(), day.negativePriceHoursCount()));
            }
            double grandAvg = history.isEmpty() ? 0 : totalSum / history.size();
            sb.append(String.format("\nOverall Average: %.2f EUR/MWh, Total Negative Hours: %d\n", grandAvg, totalNegHours));
            return sb.toString();
        } catch (IllegalArgumentException | DateTimeParseException e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(
            name = "energy_get_renewable_forecast",
            description = "Retrieve forecasted renewable generation (wind onshore/offshore, solar PV) and grid load in MW for a specified date and European bidding zone."
    )
    public String getRenewableForecast(
            @ToolParam(description = "Bidding zone code (e.g. DE-LU, FR, NL, AT)", required = true) String biddingZone,
            @ToolParam(description = "Target forecast date in ISO format YYYY-MM-DD", required = false) String date
    ) {
        try {
            BiddingZone zone = BiddingZone.fromString(biddingZone);
            LocalDate targetDate = (date != null && !date.isBlank()) ? LocalDate.parse(date.trim()) : LocalDate.now();

            List<RenewableForecast> forecasts = marketEngine.getRenewableForecast(zone, targetDate);
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Renewable Generation Forecast for %s on %s:\n", zone.getDescription(), targetDate));
            sb.append(String.format("%-6s | %-10s | %-10s | %-10s | %-12s | %-10s | %-8s\n",
                    "Hour", "Solar(MW)", "WindOn(MW)", "WindOff(MW)", "TotalRen(MW)", "Load(MW)", "RenShare"));
            sb.append("-".repeat(78)).append("\n");

            for (RenewableForecast f : forecasts) {
                sb.append(String.format("%02d:00  | %10.1f | %10.1f | %10.1f | %12.1f | %10.1f | %7.1f%%\n",
                        f.hour(), f.solarMw(), f.windOnshoreMw(), f.windOffshoreMw(), f.totalRenewableMw(), f.systemLoadMw(), f.renewableSharePercent()));
            }
            return sb.toString();
        } catch (IllegalArgumentException | DateTimeParseException e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(
            name = "energy_evaluate_dispatch",
            description = "Calculate optimal battery storage or flexible asset charge/discharge schedules to maximize trading margin and grid balancing based on spot price spreads."
    )
    public String evaluateDispatch(
            @ToolParam(description = "Battery/Asset storage capacity in MWh", required = true) double capacityMWh,
            @ToolParam(description = "Maximum power charge/discharge rating in MW", required = true) double maxPowerMw,
            @ToolParam(description = "Round-trip efficiency (e.g. 0.88 for 88%), default 0.88", required = false) Double roundTripEfficiency,
            @ToolParam(description = "Target dispatch date (YYYY-MM-DD), default today", required = false) String date,
            @ToolParam(description = "Bidding zone code (e.g. DE-LU, FR, NL, AT), default DE-LU", required = false) String biddingZone
    ) {
        try {
            BiddingZone zone = BiddingZone.fromString(biddingZone);
            LocalDate targetDate = (date != null && !date.isBlank()) ? LocalDate.parse(date.trim()) : LocalDate.now();
            double eff = (roundTripEfficiency != null && roundTripEfficiency > 0.0) ? roundTripEfficiency : 0.88;

            DispatchSchedule schedule = marketEngine.evaluateBatteryDispatch(capacityMWh, maxPowerMw, eff, targetDate, zone);
            return objectMapper.writeValueAsString(schedule);
        } catch (IllegalArgumentException | DateTimeParseException | JsonProcessingException e) {
            return "Error: " + e.getMessage();
        }
    }
}
