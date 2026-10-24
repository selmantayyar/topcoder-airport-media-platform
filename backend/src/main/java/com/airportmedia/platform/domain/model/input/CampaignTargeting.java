package com.airportmedia.platform.domain.model.input;

import com.airportmedia.platform.common.enums.Haul;
import com.airportmedia.platform.common.enums.SpendTier;
import java.util.Set;

/**
 * A campaign's targeting and scoring preferences, parsed from its three JSON columns
 * (placement, hard filters, segment weights). The decision pipeline owns this shape.
 *
 * @param gates          placement: gate labels this campaign may play near (empty = any gate)
 * @param haul           hard filter: required flight haul near the screen (null = any)
 * @param destinations   hard filter: required destinations near the screen (empty = any)
 * @param minCrowd       hard filter: minimum estimated zone headcount
 * @param minDwellMinutes hard filter: minimum expected crowd dwell
 * @param restricted     brand-safety tag, e.g. "alcohol" (null = unrestricted)
 * @param spendTarget    scoring: the spend tier this offer is pitched at (null = MEDIUM)
 * @param weightBusiness scoring: how much the campaign wants business travellers
 * @param weightHoliday  scoring: how much it wants leisure/holiday travellers
 * @param weightFamily   scoring: how much it wants families
 */
public record CampaignTargeting(
        Set<String> gates,
        Haul haul,
        Set<String> destinations,
        int minCrowd,
        int minDwellMinutes,
        String restricted,
        SpendTier spendTarget,
        double weightBusiness,
        double weightHoliday,
        double weightFamily) {

    public SpendTier spendTargetOrDefault() {
        return spendTarget != null ? spendTarget : SpendTier.MEDIUM;
    }

    /** Sum of the three segment weights, used to normalise the crowd-fit dot product. */
    public double weightTotal() {
        return weightBusiness + weightHoliday + weightFamily;
    }
}
