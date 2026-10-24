package com.airportmedia.platform.service;

import com.airportmedia.platform.domain.entity.PromoCode;
import java.time.Duration;
import java.time.Instant;

/**
 * When a promo code stops being claimable. Every code is time-boxed ({@code validUntil}); a code linked
 * to a flight also dies when that flight's boarding closes ({@value #BOARDING_CLOSES_BEFORE_DEPARTURE_MINUTES}
 * minutes before its current estimated departure), whichever comes first. Evaluated live, so a delay
 * pushes the close back.
 */
public final class PromoPolicy {

    public static final long BOARDING_CLOSES_BEFORE_DEPARTURE_MINUTES = 15;

    private PromoPolicy() {}

    public static Instant boardingCloses(Instant departure) {
        return departure.minus(Duration.ofMinutes(BOARDING_CLOSES_BEFORE_DEPARTURE_MINUTES));
    }

    public static Instant effectiveExpiry(Instant validUntil, Instant linkedDeparture) {
        if (linkedDeparture == null) {
            return validUntil;
        }
        Instant boardingClose = boardingCloses(linkedDeparture);
        return boardingClose.isBefore(validUntil) ? boardingClose : validUntil;
    }

    public static Instant effectiveExpiry(PromoCode promo) {
        return effectiveExpiry(promo.validUntil,
                promo.linkedFlight != null ? promo.linkedFlight.departureTime() : null);
    }

}
