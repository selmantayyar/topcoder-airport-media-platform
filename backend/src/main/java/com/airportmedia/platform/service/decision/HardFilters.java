package com.airportmedia.platform.service.decision;

import com.airportmedia.platform.common.enums.CampaignStatus;
import com.airportmedia.platform.common.enums.PartyType;
import com.airportmedia.platform.domain.model.input.CampaignTargeting;
import com.airportmedia.platform.domain.model.input.CampaignView;
import com.airportmedia.platform.domain.model.input.DecisionInputs;
import com.airportmedia.platform.domain.model.output.CrowdProfile;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * The hard-filter stage: a campaign that fails any one of these is categorically ineligible and
 * never scored. Filters split into <em>static</em> ones (approval, schedule, placement by gate/zone/
 * screen, budget — trustworthy regardless of live data) and <em>dynamic</em> ones (haul, destination,
 * brand-safety, crowd, dwell, delay — derived from flight/crowd data). The split lets the rule-based
 * fallback reuse only the static filters when the dynamic inputs can't be trusted.
 */
@ApplicationScoped
public class HardFilters {

    private static final int ALCOHOL_FAMILY_LIMIT_PCT = 40;

    /** @return the first reason the campaign is ineligible, or {@code null} if it passes everything. */
    public String firstFailure(CampaignView campaign, DecisionInputs inputs) {
        String staticFailure = firstStaticFailure(campaign, inputs);
        if (staticFailure != null) {
            return staticFailure;
        }
        CampaignTargeting t = campaign.targeting();

        if (t.haul() != null && inputs.nearbyFlights().stream().noneMatch(f -> f.haul() == t.haul())) {
            return "no " + t.haul() + " flights nearby";
        }
        if (!t.destinations().isEmpty()
                && inputs.nearbyFlights().stream().noneMatch(f -> t.destinations().contains(f.destination()))) {
            return "no targeted destination nearby";
        }

        CrowdProfile profile = inputs.profile();
        if ("alcohol".equalsIgnoreCase(t.restricted()) && profile != null) {
            int familyPct = profile.partyType().getOrDefault(PartyType.FAMILY, 0);
            if (familyPct > ALCOHOL_FAMILY_LIMIT_PCT) {
                return "brand-safety: family-heavy crowd (" + familyPct + "%)";
            }
        }
        if (inputs.headcount() < t.minCrowd()) {
            return "below minimum crowd (" + inputs.headcount() + " < " + t.minCrowd() + ")";
        }
        if (profile != null && t.minDwellMinutes() > 0 && profile.dwellMinutes() < t.minDwellMinutes()) {
            return "below minimum dwell (" + profile.dwellMinutes() + " < " + t.minDwellMinutes() + "m)";
        }
        if (t.minDelayMinutes() > 0) {
            int delay = profile != null ? profile.delayMinutes() : 0;
            if (delay < t.minDelayMinutes()) {
                return "crowd not delayed enough (" + delay + " < " + t.minDelayMinutes() + "m)";
            }
        }
        return null;
    }

    /** Only the filters that don't depend on live flight/crowd data — the fallback's eligibility test. */
    public String firstStaticFailure(CampaignView campaign, DecisionInputs inputs) {
        if (campaign.status() != CampaignStatus.RUNNING) {
            return "not running (" + campaign.status() + ")";
        }
        if (inputs.now().isBefore(campaign.scheduleStart()) || inputs.now().isAfter(campaign.scheduleEnd())) {
            return "outside schedule window";
        }
        CampaignTargeting t = campaign.targeting();
        if (!t.placementUnrestricted() && !placementMatches(t, inputs)) {
            return "no placement at this screen";
        }
        if (campaign.budget() != null && campaign.spentToDate() != null
                && campaign.spentToDate().compareTo(campaign.budget()) >= 0) {
            return "budget exhausted";
        }
        return null;
    }

    /** A screen is placed if it sits near a named gate, in a named zone, or is itself a named screen. */
    private static boolean placementMatches(CampaignTargeting t, DecisionInputs inputs) {
        return inputs.screenGates().stream().anyMatch(t.gates()::contains)
                || (inputs.zoneId() != null && t.zoneIds().contains(inputs.zoneId()))
                || t.screenIds().contains(inputs.screenId());
    }
}
