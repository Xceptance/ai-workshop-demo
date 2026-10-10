package com.workshop.energy.domain;

public record SpotPricePoint(
        String biddingZone,
        String timestamp,
        int hour,
        double priceEurPerMWh,
        boolean isNegative
) {
}
