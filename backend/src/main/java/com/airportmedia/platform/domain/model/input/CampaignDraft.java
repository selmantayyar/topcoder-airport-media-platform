package com.airportmedia.platform.domain.model.input;

import com.airportmedia.platform.common.enums.Haul;
import com.airportmedia.platform.common.enums.SpendTier;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

/**
 * A campaign as the retailer builds it: creative (a library pick via {@code creativeId}, or new copy),
 * placement (gates/zones/screens), audience rules, scoring preferences, schedule and budget. The same
 * draft drives the live audience-match preview and the save, so what's previewed is what runs.
 */
public record CampaignDraft(
        @NotNull Long advertiserId,
        @NotBlank @Size(max = 120) String name,
        Long creativeId,
        @Size(max = 200) String headline,
        @Size(max = 200) String offerText,
        @Size(max = 1000) String body,
        @Size(max = 8) String language,
        Set<String> gates,
        Set<Long> zoneIds,
        Set<Long> screenIds,
        Set<String> destinations,
        Haul haul,
        @PositiveOrZero int minDwellMinutes,
        @PositiveOrZero int minDelayMinutes,
        @PositiveOrZero int minCrowd,
        @PositiveOrZero double weightBusiness,
        @PositiveOrZero double weightHoliday,
        @PositiveOrZero double weightFamily,
        SpendTier spendTarget,
        @NotNull Instant scheduleStart,
        @NotNull Instant scheduleEnd,
        @NotNull @Positive BigDecimal budget,
        @Positive BigDecimal dailyCap,
        @Min(1) @Max(50) int frequencyCapPerScreen) {

    @AssertTrue(message = "scheduleEnd must be after scheduleStart")
    public boolean isScheduleOrdered() {
        return scheduleStart == null || scheduleEnd == null || scheduleEnd.isAfter(scheduleStart);
    }

    @AssertTrue(message = "pick a library creative or provide a headline")
    public boolean isCreativeProvided() {
        return creativeId != null || (headline != null && !headline.isBlank());
    }
}
