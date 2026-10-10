package com.workshop.energy.domain;

import java.util.List;

public record DispatchSchedule(
        String assetType,
        double capacityMWh,
        double maxPowerMw,
        double roundTripEfficiency,
        String date,
        double totalCostEur,
        double totalRevenueEur,
        double netProfitEur,
        List<DispatchAction> actions,
        String rationale
) {
    public record DispatchAction(
            int hour,
            String timeWindow,
            String action, // CHARGE, DISCHARGE, IDLE
            double priceEurPerMWh,
            double powerMw,
            double energyMWh,
            double stateOfChargeMWh,
            double costOrRevenueEur
    ) {}
}
