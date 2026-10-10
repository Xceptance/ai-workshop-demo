package com.workshop.energy.domain;

import java.util.List;

public final class MarketModels {

    private MarketModels() {}

    public record HourlySpotPrice(
            int hour,
            double dayAheadPriceEurPerMwh,
            double intradayPriceEurPerMwh,
            boolean isNegative
    ) {}

    public record SpotPriceResult(
            String zone,
            String date,
            double averageDayAheadPrice,
            double averageIntradayPrice,
            double maxPrice,
            double minPrice,
            int negativePriceHoursCount,
            List<HourlySpotPrice> hourlyPrices
    ) {}

    public record DailyHistoricalSummary(
            String date,
            double averagePriceEurPerMwh,
            double minPrice,
            double maxPrice,
            int negativePriceHoursCount
    ) {}

    public record HistoricalPricesResult(
            String zone,
            String startDate,
            String endDate,
            double overallAveragePrice,
            double overallMinPrice,
            double overallMaxPrice,
            int totalNegativePriceHours,
            List<DailyHistoricalSummary> dailySummaries
    ) {}

    public record HourlyRenewableForecast(
            int hour,
            double solarPvMw,
            double windOnshoreMw,
            double windOffshoreMw,
            double totalRenewableMw,
            double gridLoadMw,
            double renewableSharePercent
    ) {}

    public record RenewableForecastResult(
            String zone,
            String date,
            double totalGenerationMwh,
            double averageGridLoadMw,
            double maxRenewableSharePercent,
            List<HourlyRenewableForecast> hourlyForecasts
    ) {}

    public record HourlyDispatchSchedule(
            int hour,
            double priceEurPerMwh,
            String action, // CHARGE, DISCHARGE, IDLE
            double powerMw,
            double energyFlowMwh,
            double stateOfChargeMwh
    ) {}

    public record DispatchEvaluationResult(
            String date,
            double capacityMwh,
            double maxPowerMw,
            double roundTripEfficiency,
            double totalChargedMwh,
            double totalDischargedMwh,
            double netEnergyLossMwh,
            double chargingCostEur,
            double dischargingRevenueEur,
            double netProfitEur,
            List<HourlyDispatchSchedule> schedule
    ) {}

    public record GridBalanceSnapshot(
            String zone,
            String timestamp,
            double currentFrequencyHz,
            double netBalanceMw,
            double renewableSharePercent,
            String status
    ) {}
}
