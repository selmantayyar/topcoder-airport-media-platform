package com.airportmedia.platform.domain.model.output;

import com.airportmedia.platform.common.enums.AgeBand;
import com.airportmedia.platform.common.enums.PartyType;
import com.airportmedia.platform.common.enums.SpendTier;
import com.airportmedia.platform.common.enums.TripPurpose;
import java.util.Comparator;
import java.util.Map;

/**
 * An estimate of who makes up a crowd: the mix of trip purposes, party types, age bands,
 * spend tiers (each a percentage distribution summing to 100), the female share, a dominant
 * language/region, expected dwell and any delay context, plus the head count it describes.
 * Deterministically derived from a flight so a logged decision can be reproduced.
 */
public record CrowdProfile(
        int crowdSize,
        Map<TripPurpose, Integer> tripPurpose,
        Map<PartyType, Integer> partyType,
        Map<AgeBand, Integer> ageBand,
        int femalePct,
        Map<SpendTier, Integer> spendTier,
        String language,
        String region,
        int dwellMinutes,
        boolean delayed,
        int delayMinutes) {

    /** The key with the largest share of a distribution — the "skews X" summary. */
    public static <E extends Enum<E>> E dominant(Map<E, Integer> distribution) {
        return distribution.entrySet().stream()
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElseThrow();
    }
}
