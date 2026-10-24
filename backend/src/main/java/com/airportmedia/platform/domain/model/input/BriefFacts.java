package com.airportmedia.platform.domain.model.input;

import com.airportmedia.platform.common.enums.Haul;
import java.util.List;

/**
 * The facts a creative brief actually states — the only facts generated ad copy may repeat.
 *
 * @param product         what is advertised, as the retailer wrote it (e.g. "Festive sweets")
 * @param discountPercent the stated discount, or null when the brief offers none
 * @param haul            the audience's flight type when the brief names one, else null
 * @param numbers         every number written in the brief, in order; generated copy may cite no other
 * @param briefText       the brief as written, for checking which offer claims it makes
 */
public record BriefFacts(
        String product,
        Integer discountPercent,
        boolean families,
        boolean business,
        Haul haul,
        List<String> numbers,
        String briefText) {}
