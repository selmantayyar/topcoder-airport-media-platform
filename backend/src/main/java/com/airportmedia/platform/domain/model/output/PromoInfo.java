package com.airportmedia.platform.domain.model.output;

import java.time.Instant;

/** What the mobile claim page shows about a promo before (and after) the passenger claims it. */
public record PromoInfo(
        String code,
        Long campaignId,
        String campaignName,
        String advertiserName,
        String headline,
        String offerText,
        Instant validUntil,
        boolean redeemed,
        boolean expired,
        long secondsRemaining) {}
