package com.airportmedia.platform.domain.model.output;

import java.math.BigDecimal;
import java.time.Instant;

/** One campaign in the retailer's history, with spend, results and ROI (see {@code RetailEconomics}). */
public record CampaignHistoryRow(
        long campaignId,
        String name,
        String status,
        Instant scheduleStart,
        Instant scheduleEnd,
        BigDecimal budget,
        BigDecimal spend,
        long impressions,
        long redemptions,
        BigDecimal revenue,
        double roi) {}
