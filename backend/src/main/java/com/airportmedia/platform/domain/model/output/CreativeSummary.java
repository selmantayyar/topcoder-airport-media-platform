package com.airportmedia.platform.domain.model.output;

/** A reusable ad from the retailer's creative library. */
public record CreativeSummary(
        long id,
        Long campaignId,
        String campaignName,
        String headline,
        String offerText,
        String body,
        String language,
        String templateKey) {}
