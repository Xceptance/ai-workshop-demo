package com.workshop.energy.domain;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class EnergyMarketEngine {

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    /**
     * Generates a 24-hour deterministic European spot market price curve for the specified date and zone.
     * Incorporates typical European market dynamics: morning & evening demand peaks, solar midday dip.
     */
    public DailyPriceSummary getDailyPriceSummary(BiddingZone zone, LocalDate date) {
        List<SpotPricePoint> hourly = new ArrayList<>();
        long seed = (long) date.hashCode() * 31 + zone.ordinal();
        Random rng = new Random(seed);

        // Daily base shift between -10 and +15 EUR/MWh
        double dailyShift = (rng.nextDouble() * 25.0 - 10.0) * zone.getPriceFactor();
        // Solar intensity factor (0.0 to 1.5)
        double solarFactor = 0.5 + rng.nextDouble() * 1.0;

        double sum = 0.0;
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        int negativeHours = 0;

        for (int h = 0; h < 24; h++) {
            double basePrice;
            if (h >= 0 && h <= 5) {
                // Night valley
                basePrice = 45.0 + (h * 2.0);
            } else if (h >= 6 && h <= 9) {
                // Morning peak
                basePrice = 95.0 + (h - 6) * 12.0;
            } else if (h >= 10 && h <= 15) {
                // Midday / Solar depression - potential negative price when solar is high
                double dip = 65.0 * solarFactor;
                basePrice = 75.0 - dip;
            } else if (h >= 16 && h <= 20) {
                // Evening peak
                basePrice = 115.0 + (h == 19 ? 35.0 : 15.0);
            } else {
                // Late night ramp-down
                basePrice = 75.0 - (h - 21) * 8.0;
            }

            double price = (basePrice + dailyShift + (rng.nextDouble() * 10.0 - 5.0)) * zone.getPriceFactor();
            price = round(price);

            boolean isNeg = price < 0.0;
            if (isNeg) {
                negativeHours++;
            }
            sum += price;
            min = Math.min(min, price);
            max = Math.max(max, price);

            String timeLabel = String.format("%sT%02d:00:00", date.format(ISO_DATE), h);
            hourly.add(new SpotPricePoint(zone.getCode(), timeLabel, h, price, isNeg));
        }

        double avg = round(sum / 24.0);
        return new DailyPriceSummary(zone.getCode(), date.format(ISO_DATE), avg, min, max, negativeHours, hourly);
    }

    /**
     * Retrieves historical prices across a date range.
     */
    public List<DailyPriceSummary> getHistoricalPrices(BiddingZone zone, LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("endDate (" + end + ") cannot be before startDate (" + start + ")");
        }
        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(start, end);
        if (daysBetween > 90) {
            throw new IllegalArgumentException("Requested date range (" + daysBetween + " days) exceeds maximum limit of 90 days");
        }

        List<DailyPriceSummary> result = new ArrayList<>();
        LocalDate cur = start;
        while (!cur.isAfter(end)) {
            result.add(getDailyPriceSummary(zone, cur));
            cur = cur.plusDays(1);
        }
        return result;
    }

    /**
     * Forecasts renewable generation (solar, wind onshore, wind offshore) and grid load.
     */
    public List<RenewableForecast> getRenewableForecast(BiddingZone zone, LocalDate date) {
        List<RenewableForecast> list = new ArrayList<>();
        long seed = (long) date.hashCode() * 17 + zone.ordinal();
        Random rng = new Random(seed);

        double windBase = 12000 + rng.nextDouble() * 18000; // MW
        for (int h = 0; h < 24; h++) {
            // Solar curve peaks between 11:00 and 15:00
            double solar;
            if (h >= 6 && h <= 20) {
                double rad = Math.sin((h - 6) / 14.0 * Math.PI);
                solar = Math.max(0.0, rad * (25000 + rng.nextDouble() * 10000));
            } else {
                solar = 0.0;
            }

            double windOn = windBase * (0.8 + 0.4 * Math.sin(h / 24.0 * 2 * Math.PI));
            double windOff = windOn * 0.45;
            double totalRen = solar + windOn + windOff;

            // System load curve (morning and evening peaks)
            double load = 45000 + (h >= 7 && h <= 21 ? 18000 : 5000) + rng.nextDouble() * 3000;
            double renShare = (totalRen / load) * 100.0;

            list.add(new RenewableForecast(
                    zone.getCode(),
                    date.format(ISO_DATE),
                    h,
                    round(solar),
                    round(windOn),
                    round(windOff),
                    round(totalRen),
                    round(load),
                    round(renShare)
            ));
        }
        return list;
    }

    /**
     * Calculates an optimal battery storage charge/discharge schedule based on hourly price arbitrage.
     */
    public DispatchSchedule evaluateBatteryDispatch(double capacityMWh, double maxPowerMw, double roundTripEfficiency,
                                                    LocalDate date, BiddingZone zone) {
        if (capacityMWh <= 0) {
            throw new IllegalArgumentException("Battery capacityMWh must be strictly positive (got: " + capacityMWh + ")");
        }
        if (maxPowerMw <= 0) {
            throw new IllegalArgumentException("Max power MW must be strictly positive (got: " + maxPowerMw + ")");
        }
        double eff = (roundTripEfficiency <= 0 || roundTripEfficiency > 1.0) ? 0.88 : roundTripEfficiency;

        DailyPriceSummary daily = getDailyPriceSummary(zone, date);
        List<SpotPricePoint> prices = daily.hourlyPrices();

        // Find hours to charge (cheapest / negative) and discharge (most expensive)
        List<SpotPricePoint> sortedByPrice = new ArrayList<>(prices);
        sortedByPrice.sort(Comparator.comparingDouble(SpotPricePoint::priceEurPerMWh));

        Set<Integer> chargeHours = new HashSet<>();
        Set<Integer> dischargeHours = new HashSet<>();

        // Needed hours to fully charge: capacity / power (capped to e.g. 6 hours)
        int hoursToFull = (int) Math.ceil(capacityMWh / maxPowerMw);
        hoursToFull = Math.min(hoursToFull, 6);

        // Identify lowest and highest hours
        for (int i = 0; i < hoursToFull; i++) {
            chargeHours.add(sortedByPrice.get(i).hour());
        }
        for (int i = sortedByPrice.size() - 1; i >= sortedByPrice.size() - hoursToFull; i--) {
            dischargeHours.add(sortedByPrice.get(i).hour());
        }

        double currentSoc = 0.0;
        double totalCost = 0.0;
        double totalRevenue = 0.0;
        List<DispatchSchedule.DispatchAction> actions = new ArrayList<>();

        for (int h = 0; h < 24; h++) {
            double price = prices.get(h).priceEurPerMWh();
            String window = String.format("%02d:00-%02d:00", h, h + 1);
            String actionType = "IDLE";
            double power = 0.0;
            double energy = 0.0;
            double cashflow = 0.0;

            if (chargeHours.contains(h) && currentSoc < capacityMWh) {
                actionType = "CHARGE";
                energy = Math.min(maxPowerMw, capacityMWh - currentSoc);
                power = energy;
                currentSoc += energy * eff; // store with efficiency
                cashflow = -(energy * price); // negative cashflow is cost
                totalCost += (energy * price);
            } else if (dischargeHours.contains(h) && currentSoc > 0.0) {
                actionType = "DISCHARGE";
                energy = Math.min(maxPowerMw, currentSoc);
                power = energy;
                currentSoc -= energy;
                cashflow = energy * price; // positive cashflow is revenue
                totalRevenue += (energy * price);
            }

            actions.add(new DispatchSchedule.DispatchAction(
                    h,
                    window,
                    actionType,
                    price,
                    round(power),
                    round(energy),
                    round(currentSoc),
                    round(cashflow)
            ));
        }

        double netProfit = round(totalRevenue - totalCost);
        String rationale = String.format("Battery storage %.1f MWh (%.1f MW, %.0f%% eff) captures spread between min price %.2f and max %.2f EUR/MWh. Net arbitrage profit: %.2f EUR",
                capacityMWh, maxPowerMw, eff * 100, daily.minPriceEurPerMWh(), daily.maxPriceEurPerMWh(), netProfit);

        return new DispatchSchedule(
                "BATTERY_STORAGE",
                capacityMWh,
                maxPowerMw,
                eff,
                date.format(ISO_DATE),
                round(totalCost),
                round(totalRevenue),
                netProfit,
                actions,
                rationale
        );
    }

    private double round(double val) {
        return BigDecimal.valueOf(val).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
