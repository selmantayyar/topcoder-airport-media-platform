package com.airportmedia.platform.domain.model.output;

import java.math.BigDecimal;
import java.util.List;

/** An advertiser's standing: lifetime spend and results, running campaigns, and any that under-deliver. */
public record AdvertiserOverviewRow(
        long advertiserId,
        String name,
        String kind,
        BigDecimal spend,
        long impressions,
        long redemptions,
        int runningCampaigns,
        List<UnderDelivery> underDelivering) {

    /** A live campaign spending its budget too slowly for its schedule (see {@code DeliveryPacing}). */
    public record UnderDelivery(long campaignId, String name, double deliveredPct, double expectedPct, double pacePct) {}
}
