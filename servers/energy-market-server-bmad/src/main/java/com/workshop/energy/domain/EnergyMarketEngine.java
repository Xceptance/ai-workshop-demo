package com.workshop.energy.domain;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class EnergyMarketEngine {

    public static final Set<String> SUPPORTED_ZONES = Set.of("DE-LU", "FR", "NL", "AT");

    public String normalizeZone(String zone) {
        if (zone == null || zone.isBlank()) {
            return "DE-LU";
        }
        String normalized = zone.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_ZONES.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported bidding zone: '" + zone + 
                    "'. Supported zones are: " + String.join(", ", SUPPORTED_ZONES));
        }
        return normalized;
    }

    public LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(dateStr.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date format: '" + dateStr + "'. Expected ISO-8601 YYYY-MM-DD.", e);
        }
    }

    public MarketModels.SpotPriceResult calculateSpotPrices(String rawZone, String rawDate) {
        String zone = normalizeZone(rawZone);
        LocalDate date = parseDate(rawDate);

        List<MarketModels.HourlySpotPrice> hourly = new ArrayList<>(24);
        double sumDa = 0.0;
        double sumId = 0.0;
        double maxPrice = -Double.MAX_VALUE;
        double minPrice = Double.MAX_VALUE;
        int negativeHours = 0;

        long seed = date.toEpochDay() * 37L + zone.hashCode();
        Random rng = new Random(seed);

        for (int h = 0; h < 24; h++) {
            // Sinusoidal daily curve with morning and evening peaks, midday solar dip
            double morningPeak = 38.0 * Math.exp(-Math.pow((h - 8.5) / 2.2, 2));
            double eveningPeak = 52.0 * Math.exp(-Math.pow((h - 19.0) / 2.5, 2));
            double solarDip = -45.0 * Math.exp(-Math.pow((h - 13.0) / 2.0, 2));
            double nightDrop = (h < 6) ? -28.0 : 0.0;

            // Seasonal adjustment (higher prices in winter, lower in summer/spring due to solar)
            int dayOfYear = date.getDayOfYear();
            double seasonalShift = 15.0 * Math.cos(2 * Math.PI * (dayOfYear - 15) / 365.0);

            // Weekend effect
            int dayOfWeek = date.getDayOfWeek().getValue();
            double weekendFactor = (dayOfWeek >= 6) ? -18.0 : 0.0;

            // Zone specific base
            double zoneBase = switch (zone) {
                case "FR" -> 68.0;
                case "NL" -> 74.0;
                case "AT" -> 66.0;
                default -> 70.0; // DE-LU
            };

            double noise = (rng.nextDouble() - 0.5) * 12.0;

            double daPrice = round(zoneBase + seasonalShift + weekendFactor + morningPeak + eveningPeak + solarDip + nightDrop + noise);
            double idPrice = round(daPrice + (rng.nextDouble() - 0.48) * 8.0);

            boolean isNeg = daPrice < 0.0 || idPrice < 0.0;
            if (isNeg) {
                negativeHours++;
            }

            hourly.add(new MarketModels.HourlySpotPrice(h, daPrice, idPrice, isNeg));

            sumDa += daPrice;
            sumId += idPrice;
            if (daPrice > maxPrice) maxPrice = daPrice;
            if (daPrice < minPrice) minPrice = daPrice;
        }

        double avgDa = round(sumDa / 24.0);
        double avgId = round(sumId / 24.0);

        return new MarketModels.SpotPriceResult(
                zone,
                date.toString(),
                avgDa,
                avgId,
                round(maxPrice),
                round(minPrice),
                negativeHours,
                Collections.unmodifiableList(hourly)
        );
    }

    public MarketModels.HistoricalPricesResult calculateHistoricalPrices(String rawZone, String rawStart, String rawEnd) {
        String zone = normalizeZone(rawZone);
        LocalDate start = parseDate(rawStart);
        LocalDate end = parseDate(rawEnd);

        if (end.isBefore(start)) {
            throw new IllegalArgumentException("Invalid date range: endDate (" + end + ") is before startDate (" + start + ").");
        }

        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
        if (daysBetween > 90) {
            throw new IllegalArgumentException("Requested date range exceeds maximum allowed limit of 90 days (requested: " + daysBetween + " days).");
        }

        List<MarketModels.DailyHistoricalSummary> summaries = new ArrayList<>();
        double totalSum = 0.0;
        double overallMin = Double.MAX_VALUE;
        double overallMax = -Double.MAX_VALUE;
        int totalNegative = 0;

        LocalDate cur = start;
        while (!cur.isAfter(end)) {
            MarketModels.SpotPriceResult dayResult = calculateSpotPrices(zone, cur.toString());
            summaries.add(new MarketModels.DailyHistoricalSummary(
                    cur.toString(),
                    dayResult.averageDayAheadPrice(),
                    dayResult.minPrice(),
                    dayResult.maxPrice(),
                    dayResult.negativePriceHoursCount()
            ));

            totalSum += dayResult.averageDayAheadPrice();
            if (dayResult.minPrice() < overallMin) overallMin = dayResult.minPrice();
            if (dayResult.maxPrice() > overallMax) overallMax = dayResult.maxPrice();
            totalNegative += dayResult.negativePriceHoursCount();

            cur = cur.plusDays(1);
        }

        double overallAvg = round(totalSum / summaries.size());

        return new MarketModels.HistoricalPricesResult(
                zone,
                start.toString(),
                end.toString(),
                overallAvg,
                round(overallMin),
                round(overallMax),
                totalNegative,
                Collections.unmodifiableList(summaries)
        );
    }

    public MarketModels.RenewableForecastResult calculateRenewableForecast(String rawZone, String rawDate) {
        String zone = normalizeZone(rawZone);
        LocalDate date = parseDate(rawDate);

        long seed = date.toEpochDay() * 59L + zone.hashCode();
        Random rng = new Random(seed);

        List<MarketModels.HourlyRenewableForecast> hourly = new ArrayList<>(24);
        double totalGenMwh = 0.0;
        double sumLoad = 0.0;
        double maxShare = 0.0;

        double baseWindOn = switch (zone) {
            case "DE-LU" -> 22000.0;
            case "NL" -> 6000.0;
            case "FR" -> 11000.0;
            case "AT" -> 3500.0;
            default -> 15000.0;
        };

        double baseWindOff = switch (zone) {
            case "DE-LU" -> 7500.0;
            case "NL" -> 4500.0;
            default -> 0.0;
        };

        double baseSolar = switch (zone) {
            case "DE-LU" -> 34000.0;
            case "FR" -> 21000.0;
            case "NL" -> 14000.0;
            case "AT" -> 5500.0;
            default -> 15000.0;
        };

        // Day of week load factor
        double dayFactor = (date.getDayOfWeek().getValue() >= 6) ? 0.78 : 1.0;

        for (int h = 0; h < 24; h++) {
            // Solar curve
            double solarFactor = 0.0;
            if (h >= 6 && h <= 19) {
                solarFactor = Math.sin(Math.PI * (h - 6) / 13.0);
            }
            double solarPv = round(baseSolar * Math.max(0.0, solarFactor) * (0.85 + 0.3 * rng.nextDouble()));

            // Wind variations
            double windOn = round(baseWindOn * (0.7 + 0.6 * rng.nextDouble()));
            double windOff = round(baseWindOff * (0.8 + 0.4 * rng.nextDouble()));
            double totalRenewable = round(solarPv + windOn + windOff);

            // Grid Load
            double loadCurve = 42000.0 + 22000.0 * Math.exp(-Math.pow((h - 11.5) / 6.0, 2)) + (h > 17 && h < 22 ? 6000.0 : 0.0);
            double gridLoad = round(loadCurve * dayFactor * (0.95 + 0.1 * rng.nextDouble()));

            double share = round((totalRenewable / Math.max(gridLoad, 1000.0)) * 100.0);
            if (share > maxShare) maxShare = share;

            hourly.add(new MarketModels.HourlyRenewableForecast(
                    h,
                    solarPv,
                    windOn,
                    windOff,
                    totalRenewable,
                    gridLoad,
                    share
            ));

            totalGenMwh += totalRenewable;
            sumLoad += gridLoad;
        }

        return new MarketModels.RenewableForecastResult(
                zone,
                date.toString(),
                round(totalGenMwh),
                round(sumLoad / 24.0),
                round(maxShare),
                Collections.unmodifiableList(hourly)
        );
    }

    public MarketModels.DispatchEvaluationResult evaluateDispatch(
            double capacityMwh,
            double maxPowerMw,
            double roundTripEfficiency,
            String rawDate
    ) {
        if (capacityMwh <= 0) {
            throw new IllegalArgumentException("Battery capacity must be positive: capacityMwh=" + capacityMwh);
        }
        if (maxPowerMw <= 0) {
            throw new IllegalArgumentException("Maximum power rating must be positive: maxPowerMw=" + maxPowerMw);
        }
        if (roundTripEfficiency <= 0.0 || roundTripEfficiency > 1.0) {
            throw new IllegalArgumentException("Round-trip efficiency must be in range (0.0, 1.0]: roundTripEfficiency=" + roundTripEfficiency);
        }

        LocalDate date = parseDate(rawDate);
        MarketModels.SpotPriceResult spotResult = calculateSpotPrices("DE-LU", date.toString());
        List<MarketModels.HourlySpotPrice> hourly = spotResult.hourlyPrices();

        // 1-way efficiency = sqrt(roundTripEfficiency)
        double oneWayEff = Math.sqrt(roundTripEfficiency);

        // Sort hours by price to find arbitrage spreads
        record HourPrice(int hour, double price) {}
        List<HourPrice> sortedHours = new ArrayList<>();
        for (var h : hourly) {
            sortedHours.add(new HourPrice(h.hour(), h.dayAheadPriceEurPerMwh()));
        }
        sortedHours.sort(Comparator.comparingDouble(HourPrice::price));

        // Lowest 4 hours for charging, highest 4 hours for discharging
        int cycleHours = Math.min(4, (int) Math.ceil(capacityMwh / maxPowerMw));
        Set<Integer> chargeHours = new HashSet<>();
        Set<Integer> dischargeHours = new HashSet<>();

        for (int i = 0; i < cycleHours; i++) {
            chargeHours.add(sortedHours.get(i).hour());
            dischargeHours.add(sortedHours.get(sortedHours.size() - 1 - i).hour());
        }

        // Simulate Chronological 24-hour operation
        List<MarketModels.HourlyDispatchSchedule> schedule = new ArrayList<>();
        double currentSoc = 0.0;
        double totalCharged = 0.0;
        double totalDischarged = 0.0;
        double chargingCost = 0.0;
        double dischargingRevenue = 0.0;

        for (int h = 0; h < 24; h++) {
            double price = hourly.get(h).dayAheadPriceEurPerMwh();
            String action = "IDLE";
            double power = 0.0;
            double energyFlow = 0.0;

            if (chargeHours.contains(h) && currentSoc < capacityMwh) {
                action = "CHARGE";
                double room = capacityMwh - currentSoc;
                double maxEnergy = Math.min(maxPowerMw, room / oneWayEff);
                energyFlow = maxEnergy;
                power = maxEnergy;
                currentSoc += energyFlow * oneWayEff;
                totalCharged += energyFlow;
                chargingCost += energyFlow * price;
            } else if (dischargeHours.contains(h) && currentSoc > 0.0) {
                action = "DISCHARGE";
                double available = currentSoc;
                double maxEnergy = Math.min(maxPowerMw, available);
                energyFlow = maxEnergy;
                power = maxEnergy;
                currentSoc -= energyFlow;
                double delivered = energyFlow * oneWayEff;
                totalDischarged += delivered;
                dischargingRevenue += delivered * price;
            }

            schedule.add(new MarketModels.HourlyDispatchSchedule(
                    h,
                    price,
                    action,
                    round(power),
                    round(energyFlow),
                    round(currentSoc)
            ));
        }

        double netLoss = round(totalCharged - totalDischarged);
        double netProfit = round(dischargingRevenue - chargingCost);

        return new MarketModels.DispatchEvaluationResult(
                date.toString(),
                capacityMwh,
                maxPowerMw,
                roundTripEfficiency,
                round(totalCharged),
                round(totalDischarged),
                netLoss,
                round(chargingCost),
                round(dischargingRevenue),
                netProfit,
                Collections.unmodifiableList(schedule)
        );
    }

    public MarketModels.GridBalanceSnapshot getGridBalanceSnapshot(String rawZone) {
        String zone = normalizeZone(rawZone);
        return new MarketModels.GridBalanceSnapshot(
                zone,
                java.time.Instant.now().toString(),
                50.018,
                235.0,
                58.4,
                "NORMAL / BALANCED"
        );
    }

    private static double round(double val) {
        return BigDecimal.valueOf(val).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
