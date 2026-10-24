package com.airportmedia.platform.domain.model.output;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** A campaign awaiting the Media Manager's approval, with its creative and any QA problems in it. */
public record ApprovalItem(
        long campaignId,
        String name,
        String advertiserName,
        Instant createdAt,
        Instant scheduleStart,
        Instant scheduleEnd,
        BigDecimal budget,
        String headline,
        String offerText,
        String language,
        List<String> creativeProblems) {}
