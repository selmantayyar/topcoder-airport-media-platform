package com.airportmedia.platform.domain.model.input;

import com.airportmedia.platform.common.enums.AdvertiserKind;
import com.airportmedia.platform.common.enums.CampaignStatus;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Everything the pipeline needs to know about one candidate campaign, assembled from the
 * campaign row plus its live counters. Kept as a flat, immutable view so scoring is a pure
 * function of logged inputs (no lazy entity access during scoring).
 *
 * @param impressionsOnScreenInWindow plays on this screen inside the frequency-cap window
 * @param spentToDate                 total attributed spend so far (for budget + pacing)
 * @param impressionsTotal            lifetime plays (engagement-prior denominator)
 * @param redemptionsTotal            lifetime redemptions (engagement-prior numerator)
 */
public record CampaignView(
        long campaignId,
        String campaignName,
        String advertiserName,
        AdvertiserKind advertiserKind,
        CampaignStatus status,
        Instant scheduleStart,
        Instant scheduleEnd,
        BigDecimal budget,
        BigDecimal dailyCap,
        BigDecimal spentToDate,
        int frequencyCapPerScreen,
        int impressionsOnScreenInWindow,
        long impressionsTotal,
        long redemptionsTotal,
        CampaignTargeting targeting) {}
