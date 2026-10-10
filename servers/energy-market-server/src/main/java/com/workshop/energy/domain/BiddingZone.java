package com.workshop.energy.domain;

import java.util.Arrays;

public enum BiddingZone {
    DE_LU("DE-LU", "Germany / Luxembourg", 1.0),
    FR("FR", "France", 1.04),
    NL("NL", "Netherlands", 0.98),
    AT("AT", "Austria", 1.02);

    private final String code;
    private final String description;
    private final double priceFactor;

    BiddingZone(String code, String description, double priceFactor) {
        this.code = code;
        this.description = description;
        this.priceFactor = priceFactor;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public double getPriceFactor() {
        return priceFactor;
    }

    public static BiddingZone fromString(String input) {
        if (input == null || input.isBlank()) {
            return DE_LU;
        }
        String normalized = input.trim().toUpperCase().replace('_', '-');
        for (BiddingZone zone : values()) {
            if (zone.code.equalsIgnoreCase(normalized) || zone.name().equalsIgnoreCase(normalized)) {
                return zone;
            }
        }
        throw new IllegalArgumentException("Unsupported bidding zone: '" + input +
                "'. Supported zones: " + Arrays.toString(Arrays.stream(values()).map(BiddingZone::getCode).toArray()));
    }
}
