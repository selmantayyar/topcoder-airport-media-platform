package com.airportmedia.platform.domain.model.output;

import java.time.Instant;

/**
 * A promo code just issued — by the retailer for a campaign, or as a passenger's share-to-family copy.
 * {@code expiresAt} is the effective expiry: the time box, or the linked flight's boarding close if sooner.
 */
public record PromoIssued(
        String code,
        String parentCode,
        Long campaignId,
        Instant validUntil,
        Instant expiresAt,
        String linkedFlightNumber,
        String claimPath) {}
