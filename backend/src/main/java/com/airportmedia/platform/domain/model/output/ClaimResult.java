package com.airportmedia.platform.domain.model.output;

import com.airportmedia.platform.common.enums.ClaimStatus;
import java.time.Instant;

/**
 * The result of attempting to claim a promo code: whether it was recorded now, was already
 * claimed, or had expired, plus the offer details the mobile claim page shows the passenger.
 */
public record ClaimResult(
        ClaimStatus status,
        String code,
        Long campaignId,
        String campaignName,
        String offerText,
        Instant validUntil,
        Instant redeemedAt,
        String segment,
        String message) {}
