package com.airportmedia.platform.domain.model.output;

import java.util.List;

/** A reusable ad from the retailer's creative library; {@code languages} lists every language it renders in. */
public record CreativeSummary(
        long id,
        Long campaignId,
        String campaignName,
        String headline,
        String offerText,
        String body,
        String language,
        String templateKey,
        List<String> languages) {}
