package com.airportmedia.platform.domain.model.input;

import com.airportmedia.platform.common.enums.Haul;
import com.airportmedia.platform.common.enums.SpendTier;
import java.util.Set;

/**
 * A campaign's targeting and scoring preferences, parsed from its three JSON columns
 * (placement, hard filters, segment weights). The decision pipeline owns this shape. A screen matches
 * placement if it sits near a named gate, in a named zone, or is a named screen.
 *
 * @param gates          placement: gate labels this campaign may play near
 * @param zoneIds        placement: zones this campaign may play in
 * @param screenIds      placement: individual screens this campaign may play on
 * @param haul           hard filter: required flight haul near the screen (null = any)
 * @param destinations   hard filter: required destinations near the screen (empty = any)
 * @param minCrowd       hard filter: minimum estimated zone headcount
 * @param minDwellMinutes hard filter: minimum expected crowd dwell
 * @param minDelayMinutes hard filter: only play when the crowd's flights are delayed at least this long
 * @param restricted     brand-safety tag, e.g. "alcohol" (null = unrestricted)
 * @param spendTarget    scoring: the spend tier this offer is pitched at (null = MEDIUM)
 * @param weightBusiness scoring: how much the campaign wants business travellers
 * @param weightHoliday  scoring: how much it wants leisure/holiday travellers
 * @param weightFamily   scoring: how much it wants families
 */
public record CampaignTargeting(
        Set<String> gates,
        Set<Long> zoneIds,
        Set<Long> screenIds,
        Haul haul,
        Set<String> destinations,
        int minCrowd,
        int minDwellMinutes,
        int minDelayMinutes,
        String restricted,
        SpendTier spendTarget,
        double weightBusiness,
        double weightHoliday,
        double weightFamily) {

    /**
     * Whether this placement lets the campaign play on a screen: unrestricted placement covers every
     * screen; otherwise the screen must sit near a named gate, in a named zone, or be a named screen.
     */
    public boolean coversScreen(long screenId, Long zoneId, Set<String> screenGates) {
        return placementUnrestricted()
                || screenGates.stream().anyMatch(gates::contains)
                || (zoneId != null && zoneIds.contains(zoneId))
                || screenIds.contains(screenId);
    }

    /** Placement is unrestricted when no gate, zone or screen is named. */
    public boolean placementUnrestricted() {
        return gates.isEmpty() && zoneIds.isEmpty() && screenIds.isEmpty();
    }

    public SpendTier spendTargetOrDefault() {
        return spendTarget != null ? spendTarget : SpendTier.MEDIUM;
    }

    /** Sum of the three segment weights, used to normalise the crowd-fit dot product. */
    public double weightTotal() {
        return weightBusiness + weightHoliday + weightFamily;
    }
}
