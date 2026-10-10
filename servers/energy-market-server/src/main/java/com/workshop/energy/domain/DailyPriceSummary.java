package com.workshop.energy.domain;

import java.util.List;

public record DailyPriceSummary(
        String biddingZone,
        String date,
        double averagePriceEurPerMWh,
        double minPriceEurPerMWh,
        double maxPriceEurPerMWh,
        int negativePriceHoursCount,
        List<SpotPricePoint> hourlyPrices
) {
}
