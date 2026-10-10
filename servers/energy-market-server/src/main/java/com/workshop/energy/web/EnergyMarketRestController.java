package com.workshop.energy.web;

import com.workshop.energy.domain.BiddingZone;
import com.workshop.energy.domain.DailyPriceSummary;
import com.workshop.energy.domain.EnergyMarketEngine;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class EnergyMarketRestController {

    private final EnergyMarketEngine engine;

    public EnergyMarketRestController(EnergyMarketEngine engine) {
        this.engine = engine;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "energy-market-server",
                "mcpEndpoint", "/mcp",
                "docs", "/api/prices/historical"
        ));
    }

    @GetMapping("/prices/historical")
    public ResponseEntity<?> getHistoricalPrices(
            @RequestParam(name = "zone", defaultValue = "DE-LU") String zoneStr,
            @RequestParam(name = "startDate") String startDateStr,
            @RequestParam(name = "endDate") String endDateStr
    ) {
        try {
            BiddingZone zone = BiddingZone.fromString(zoneStr);
            LocalDate start = LocalDate.parse(startDateStr.trim());
            LocalDate end = LocalDate.parse(endDateStr.trim());

            List<DailyPriceSummary> history = engine.getHistoricalPrices(zone, start, end);

            double sum = 0;
            int totalNegHours = 0;
            for (DailyPriceSummary day : history) {
                sum += day.averagePriceEurPerMWh();
                totalNegHours += day.negativePriceHoursCount();
            }
            double grandAverage = history.isEmpty() ? 0 : Math.round((sum / history.size()) * 100.0) / 100.0;

            Map<String, Object> response = Map.of(
                    "biddingZone", zone.getCode(),
                    "biddingZoneDescription", zone.getDescription(),
                    "startDate", start.toString(),
                    "endDate", end.toString(),
                    "totalDays", history.size(),
                    "overallAveragePriceEurPerMWh", grandAverage,
                    "totalNegativePriceHours", totalNegHours,
                    "days", history
            );

            return ResponseEntity.ok(response);
        } catch (DateTimeParseException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", "Invalid date format: " + e.getMessage() + ". Required format: YYYY-MM-DD"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }
}
