package com.airportmedia.platform.domain.model.input;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * A retailer issuing a promo code for a campaign: optionally linked to a flight (the code then dies
 * when that flight's boarding closes) and time-boxed to {@code validMinutes} (default 120).
 */
public record PromoRequest(Long flightId, @Min(5) @Max(1440) Integer validMinutes) {

    public int validMinutesOrDefault() {
        return validMinutes != null ? validMinutes : 120;
    }
}
