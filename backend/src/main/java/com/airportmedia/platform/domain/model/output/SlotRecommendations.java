package com.airportmedia.platform.domain.model.output;

import java.time.LocalDate;
import java.util.List;

/** "Best 3 slots for this campaign tomorrow", with the method behind the prediction. */
public record SlotRecommendations(
        long campaignId, String campaignName, LocalDate day, List<SlotRecommendation> slots, String method) {}
