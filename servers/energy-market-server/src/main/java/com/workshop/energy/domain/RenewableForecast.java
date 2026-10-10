package com.workshop.energy.domain;

public record RenewableForecast(
        String biddingZone,
        String date,
        int hour,
        double solarMw,
        double windOnshoreMw,
        double windOffshoreMw,
        double totalRenewableMw,
        double systemLoadMw,
        double renewableSharePercent
) {
}
