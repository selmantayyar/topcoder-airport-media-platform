package com.airportmedia.platform.service.decision;

import com.airportmedia.platform.common.enums.AdvertiserKind;
import com.airportmedia.platform.common.enums.AgeBand;
import com.airportmedia.platform.common.enums.CampaignStatus;
import com.airportmedia.platform.common.enums.Haul;
import com.airportmedia.platform.common.enums.PartyType;
import com.airportmedia.platform.common.enums.SpendTier;
import com.airportmedia.platform.common.enums.TripPurpose;
import com.airportmedia.platform.domain.model.input.CampaignTargeting;
import com.airportmedia.platform.domain.model.input.CampaignView;
import com.airportmedia.platform.domain.model.input.DecisionInputs;
import com.airportmedia.platform.domain.model.input.DecisionInputs.NearbyFlight;
import com.airportmedia.platform.domain.model.output.CrowdProfile;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builders for decision-pipeline unit tests so each test states only what it varies. */
final class DecisionFixtures {

    static final Instant NOW = Instant.parse("2026-10-23T14:30:00Z");
    static final Instant DAY_START = Instant.parse("2026-10-23T00:00:00Z");
    static final Instant DAY_END = Instant.parse("2026-10-24T00:00:00Z");

    private DecisionFixtures() {}

    static CampaignTargeting targeting(
            Set<String> gates, Haul haul, Set<String> destinations, int minCrowd, int minDwell,
            String restricted, SpendTier spend, double wBusiness, double wHoliday, double wFamily) {
        return new CampaignTargeting(gates, Set.of(), Set.of(), haul, destinations, minCrowd, minDwell, 0,
                restricted, spend, wBusiness, wHoliday, wFamily);
    }

    static CampaignTargeting placementTargeting(Set<String> gates, Set<Long> zoneIds, Set<Long> screenIds) {
        return new CampaignTargeting(gates, zoneIds, screenIds, null, Set.of(), 0, 0, 0,
                null, SpendTier.MEDIUM, 1, 1, 1);
    }

    static CampaignTargeting delayTargeting(int minDelayMinutes) {
        return new CampaignTargeting(Set.of("42"), Set.of(), Set.of(), null, Set.of(), 0, 0, minDelayMinutes,
                null, SpendTier.MEDIUM, 1, 1, 1);
    }

    /** The default fixture profile with its flights delayed by {@code delayMinutes}. */
    static CrowdProfile delayedProfile(int delayMinutes) {
        CrowdProfile p = profile(400, 20, 55, 25, 30, 45, 30, 25, 130);
        return new CrowdProfile(p.crowdSize(), p.tripPurpose(), p.partyType(), p.ageBand(), p.femalePct(),
                p.spendTier(), p.language(), p.region(), p.dwellMinutes(), delayMinutes > 0, delayMinutes);
    }

    static CampaignTargeting defaultTargeting() {
        return targeting(Set.of("42"), null, Set.of(), 0, 0, null, SpendTier.MEDIUM, 1, 1, 1);
    }

    /** Mutable builder for a {@link CampaignView}; call {@link #build()} to freeze it. */
    static final class Camp {
        long id = 1;
        String name = "Campaign";
        String advertiser = "Advertiser";
        AdvertiserKind kind = AdvertiserKind.RETAILER;
        CampaignStatus status = CampaignStatus.RUNNING;
        Instant start = DAY_START.minusSeconds(3600);
        Instant end = DAY_END.plusSeconds(3600);
        BigDecimal budget = new BigDecimal("50000");
        BigDecimal dailyCap = new BigDecimal("5000");
        BigDecimal spent = BigDecimal.ZERO;
        int freqCap = 4;
        int impWindow = 0;
        long impTotal = 0;
        long redTotal = 0;
        CampaignTargeting targeting = defaultTargeting();

        Camp id(long v) { this.id = v; return this; }
        Camp name(String v) { this.name = v; return this; }
        Camp advertiser(String v) { this.advertiser = v; return this; }
        Camp kind(AdvertiserKind v) { this.kind = v; return this; }
        Camp status(CampaignStatus v) { this.status = v; return this; }
        Camp schedule(Instant s, Instant e) { this.start = s; this.end = e; return this; }
        Camp budget(String v) { this.budget = new BigDecimal(v); return this; }
        Camp spent(String v) { this.spent = new BigDecimal(v); return this; }
        Camp freqCap(int v) { this.freqCap = v; return this; }
        Camp impWindow(int v) { this.impWindow = v; return this; }
        Camp history(long impressions, long redemptions) { this.impTotal = impressions; this.redTotal = redemptions; return this; }
        Camp targeting(CampaignTargeting v) { this.targeting = v; return this; }

        CampaignView build() {
            return new CampaignView(id, name, advertiser, kind, status, start, end, budget, dailyCap,
                    spent, freqCap, impWindow, impTotal, redTotal, targeting);
        }
    }

    static Camp camp() {
        return new Camp();
    }

    /**
     * A crowd profile stated by its score-relevant shares (each 0..100). Party is filled so FAMILY
     * carries {@code familyPct} with the rest as solo; age is a neutral spread.
     */
    static CrowdProfile profile(
            int crowd, int business, int leisure, int vfr, int familyPct,
            int lowSpend, int medSpend, int highSpend, int dwellMinutes) {
        return new CrowdProfile(
                crowd,
                Map.of(TripPurpose.BUSINESS, business, TripPurpose.LEISURE, leisure, TripPurpose.VFR, vfr),
                Map.of(PartyType.SOLO, Math.max(0, 100 - familyPct - 20), PartyType.COUPLE, 20,
                        PartyType.FAMILY, familyPct, PartyType.GROUP, 0),
                Map.of(AgeBand.A18_24, 20, AgeBand.A25_44, 40, AgeBand.A45_64, 25, AgeBand.A65_PLUS, 15),
                50,
                Map.of(SpendTier.LOW, lowSpend, SpendTier.MEDIUM, medSpend, SpendTier.HIGH, highSpend),
                "English", "Europe", dwellMinutes, false, 0);
    }

    static NearbyFlight flight(String number, String airline, String dest, Haul haul, long minutes) {
        return new NearbyFlight(number, airline, dest, haul, minutes, minutes >= -5 && minutes <= 40);
    }

    /** Mutable builder for {@link DecisionInputs}. */
    static final class Dec {
        long screenId = 1;
        Long zoneId = 10L;
        Set<String> gates = Set.of("42");
        Instant now = NOW;
        Instant dataAsOf = NOW;
        int headcount = 400;
        CrowdProfile profile = DecisionFixtures.profile(400, 20, 55, 25, 30, 45, 30, 25, 130);
        List<NearbyFlight> nearby = List.of(flight("XY501", "Zephyr Airways", "DXB", Haul.INTERNATIONAL, 25));
        List<CampaignView> campaigns = List.of();
        AdvertiserKind lastWinnerKind = null;
        String lastWinnerAdvertiser = null;

        Dec profile(CrowdProfile v) { this.profile = v; return this; }
        Dec headcount(int v) { this.headcount = v; return this; }
        Dec now(Instant v) { this.now = v; return this; }
        Dec dataAsOf(Instant v) { this.dataAsOf = v; return this; }
        Dec gates(Set<String> v) { this.gates = v; return this; }
        Dec nearby(List<NearbyFlight> v) { this.nearby = v; return this; }
        Dec campaigns(CampaignView... v) { this.campaigns = List.of(v); return this; }
        Dec lastWinner(AdvertiserKind kind, String advertiser) { this.lastWinnerKind = kind; this.lastWinnerAdvertiser = advertiser; return this; }

        DecisionInputs build() {
            return new DecisionInputs(screenId, zoneId, gates, now, dataAsOf, headcount, profile,
                    nearby, campaigns, lastWinnerKind, lastWinnerAdvertiser, DAY_START, DAY_END);
        }
    }

    static Dec inputs() {
        return new Dec();
    }
}
