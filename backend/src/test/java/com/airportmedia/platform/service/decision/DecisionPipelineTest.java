package com.airportmedia.platform.service.decision;

import static com.airportmedia.platform.service.decision.DecisionFixtures.camp;
import static com.airportmedia.platform.service.decision.DecisionFixtures.inputs;
import static com.airportmedia.platform.service.decision.DecisionFixtures.targeting;
import static org.assertj.core.api.Assertions.assertThat;

import com.airportmedia.platform.common.RouteDirectory;
import com.airportmedia.platform.common.enums.AdvertiserKind;
import com.airportmedia.platform.common.enums.FlightDirection;
import com.airportmedia.platform.common.enums.FlightStatus;
import com.airportmedia.platform.common.enums.Haul;
import com.airportmedia.platform.common.enums.SpendTier;
import com.airportmedia.platform.common.enums.ZoneKind;
import com.airportmedia.platform.domain.entity.AircraftType;
import com.airportmedia.platform.domain.entity.Airline;
import com.airportmedia.platform.domain.entity.Flight;
import com.airportmedia.platform.domain.entity.GateAssignment;
import com.airportmedia.platform.domain.entity.Zone;
import com.airportmedia.platform.domain.model.input.CampaignView;
import com.airportmedia.platform.domain.model.input.DecisionInputs;
import com.airportmedia.platform.domain.model.input.DecisionInputs.NearbyFlight;
import com.airportmedia.platform.domain.model.output.CandidateScore;
import com.airportmedia.platform.domain.model.output.CrowdProfile;
import com.airportmedia.platform.domain.model.output.DecisionResult;
import com.airportmedia.platform.domain.model.output.ZonePresence;
import com.airportmedia.platform.service.CrowdProfiler;
import com.airportmedia.platform.service.ZonePresenceService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Pure pipeline tests, including the frozen Gate 42 worked example built through the real crowd stack. */
class DecisionPipelineTest {

    private final DecisionPipeline pipeline = new DecisionPipeline(
            new HardFilters(), new DecisionScorer(), new BusinessRules(), new FreshnessCheck());

    private final CrowdProfiler profiler = new CrowdProfiler(new RouteDirectory());
    private final ZonePresenceService presence =
            new ZonePresenceService(null, null, null, profiler, null);

    private static final Set<String> GATE_42_PLACEMENT = Set.of("40", "41", "42", "43", "44", "45");

    // --- Gate 42 worked example ------------------------------------------

    @Test
    void gate42WorkedExample_dutyFreeWins() {
        DecisionInputs in = gate42Inputs();

        DecisionResult result = pipeline.decide(in);

        assertThat(result.fallback()).isFalse();
        assertThat(result.winnerName()).contains("Duty-Free");

        CandidateScore dutyFree = candidate(result, 1);
        CandidateScore coffee = candidate(result, 2);
        CandidateScore watches = candidate(result, 3);

        // All three survive the hard filters and no business rule fires, so final == base.
        assertThat(dutyFree.eligible()).isTrue();
        assertThat(dutyFree.ruleMultiplier()).isEqualTo(1.0);
        assertThat(dutyFree.finalScore()).isEqualTo(dutyFree.baseScore());

        // Duty-free wins on audience fit and spend alignment, ahead of coffee, with watches last.
        assertThat(dutyFree.finalScore()).isGreaterThan(coffee.finalScore());
        assertThat(coffee.finalScore()).isGreaterThan(watches.finalScore());
        assertThat(dutyFree.crowdFit()).isGreaterThan(watches.crowdFit());
        assertThat(dutyFree.spendMatch()).isGreaterThan(watches.spendMatch());
        assertThat(result.winnerCampaignId()).isEqualTo(1L);

        // Frozen documented numbers (see docs/adr/0004-decision-pipeline-scoring.md). The same two
        // flights always blend to this audience, so these scores are the canonical worked example.
        assertThat(in.headcount()).isEqualTo(400);
        assertThat(in.profile().region()).isEqualTo("Gulf");

        assertThat(dutyFree.crowdFit()).isEqualTo(0.4831);
        assertThat(dutyFree.boardingProximity()).isEqualTo(1.0);
        assertThat(dutyFree.dwellFit()).isEqualTo(1.0);
        assertThat(dutyFree.spendMatch()).isEqualTo(0.655);
        assertThat(dutyFree.engagementPrior()).isEqualTo(0.5);
        assertThat(dutyFree.baseScore()).isEqualTo(71.73);
        assertThat(dutyFree.finalScore()).isEqualTo(71.73);

        assertThat(coffee.crowdFit()).isEqualTo(0.4);
        assertThat(coffee.spendMatch()).isEqualTo(0.605);
        assertThat(coffee.finalScore()).isEqualTo(68.08);

        assertThat(watches.crowdFit()).isEqualTo(0.276);
        assertThat(watches.spendMatch()).isEqualTo(0.395);
        assertThat(watches.finalScore()).isEqualTo(60.59);
    }

    @Test
    void gate42WorkedExample_isReproducible() {
        assertThat(pipeline.decide(gate42Inputs())).isEqualTo(pipeline.decide(gate42Inputs()));
    }

    // --- general pipeline behaviour --------------------------------------

    @Test
    void ineligibleCampaignsAreLoggedButNeverChosen() {
        CampaignView eligible = camp().id(1).name("In-zone").build();
        CampaignView offPlacement = camp().id(2).name("Far away")
                .targeting(targeting(Set.of("99"), null, Set.of(), 0, 0, null, SpendTier.MEDIUM, 1, 1, 1))
                .build();

        DecisionResult result = pipeline.decide(inputs().campaigns(eligible, offPlacement).build());

        assertThat(result.winnerCampaignId()).isEqualTo(1L);
        assertThat(candidate(result, 2).eligible()).isFalse();
        assertThat(candidate(result, 2).dropReason()).containsIgnoringCase("placement");
    }

    @Test
    void noEligibleCampaignYieldsNoWinner() {
        CampaignView offPlacement = camp().id(7)
                .targeting(targeting(Set.of("99"), null, Set.of(), 0, 0, null, SpendTier.MEDIUM, 1, 1, 1))
                .build();

        DecisionResult result = pipeline.decide(inputs().campaigns(offPlacement).build());

        assertThat(result.hasWinner()).isFalse();
        assertThat(result.reason()).containsIgnoringCase("no eligible");
    }

    @Test
    void missingCrowdEvidenceFallsBackToTheRuleBasedDefault() {
        // No profile at all (feed gap): the pipeline must not score on absent facts; it falls back.
        CampaignView c = camp().id(5).name("House default").build();

        DecisionResult result = pipeline.decide(inputs().profile(null).campaigns(c).build());

        assertThat(result.fallback()).isTrue();
        assertThat(result.winnerCampaignId()).isEqualTo(5L);
    }

    @Test
    void staleInputsFallBackToTheRuleBasedDefault() {
        // Dynamic scoring would pick the duty-free campaign; stale data forces the static default,
        // which picks on remaining budget — here the coffee campaign has the most left.
        CampaignView dutyFree = dutyFreeCampaign().build();
        CampaignView coffee = coffeeCampaign().budget("200000").build();
        CampaignView watches = watchesCampaign().build();

        DecisionInputs stale = gate42InputsBuilder()
                .dataAsOf(DecisionFixtures.NOW.minusSeconds(20 * 60))
                .campaigns(dutyFree, coffee, watches)
                .build();

        DecisionResult result = pipeline.decide(stale);

        assertThat(result.fallback()).isTrue();
        assertThat(result.reason().toLowerCase()).contains("default");
        assertThat(result.winnerCampaignId()).isEqualTo(2L);
    }

    // --- scenario construction -------------------------------------------

    private DecisionInputs gate42Inputs() {
        return gate42InputsBuilder()
                .campaigns(dutyFreeCampaign().build(), coffeeCampaign().build(), watchesCampaign().build())
                .build();
    }

    private DecisionFixtures.Dec gate42InputsBuilder() {
        Instant now = DecisionFixtures.NOW;
        Zone pierD = zone(10L, "Pier D", ZoneKind.GATE_AREA);
        Flight xy501 = departure("XY501", "Zephyr Airways", false, "DXB", 240,
                Instant.parse("2026-10-23T14:10:00Z"), Instant.parse("2026-10-23T14:55:00Z"), 45, widebody());
        Flight zq88 = departure("ZQ88", "Quokka Air", true, "MLE", 160,
                Instant.parse("2026-10-23T15:00:00Z"), Instant.parse("2026-10-23T15:00:00Z"), 0, narrowbody());

        ZonePresence zp = presence.compute(pierD,
                List.of(gate(pierD, "42", xy501), gate(pierD, "43", zq88)), 0, now, 5);
        CrowdProfile profile = zp.profile();

        List<NearbyFlight> nearby = List.of(
                new NearbyFlight("XY501", "Zephyr Airways", "DXB", Haul.INTERNATIONAL, 25, true),
                new NearbyFlight("ZQ88", "Quokka Air", "MLE", Haul.INTERNATIONAL, 30, true));

        return inputs()
                .now(now)
                .dataAsOf(now)
                .gates(Set.of("42"))
                .headcount(zp.estimatedHeadcount())
                .profile(profile)
                .nearby(nearby);
    }

    private static DecisionFixtures.Camp dutyFreeCampaign() {
        return camp().id(1).name("Azure Duty-Free — 15% off perfume").advertiser("Azure Duty-Free")
                .kind(AdvertiserKind.RETAILER)
                .targeting(targeting(GATE_42_PLACEMENT, Haul.INTERNATIONAL, Set.of(), 100, 15, null,
                        SpendTier.MEDIUM, 0.2, 0.7, 0.4));
    }

    private static DecisionFixtures.Camp coffeeCampaign() {
        return camp().id(2).name("Bean & Brew Coffee — Free coffee upgrade").advertiser("Bean & Brew Coffee")
                .kind(AdvertiserKind.FNB)
                .targeting(targeting(GATE_42_PLACEMENT, null, Set.of(), 50, 0, null,
                        SpendTier.LOW, 0.4, 0.4, 0.4));
    }

    private static DecisionFixtures.Camp watchesCampaign() {
        return camp().id(3).name("Luxe Watches — Premium watch sale").advertiser("Luxe Watches")
                .kind(AdvertiserKind.RETAILER)
                .targeting(targeting(GATE_42_PLACEMENT, Haul.INTERNATIONAL, Set.of(), 100, 0, null,
                        SpendTier.HIGH, 0.7, 0.2, 0.1));
    }

    private static CandidateScore candidate(DecisionResult result, long campaignId) {
        return result.candidates().stream()
                .filter(c -> c.campaignId() == campaignId)
                .findFirst()
                .orElseThrow();
    }

    // --- entity builders --------------------------------------------------

    private static Zone zone(Long id, String name, ZoneKind kind) {
        Zone zone = new Zone();
        zone.id = id;
        zone.name = name;
        zone.kind = kind;
        return zone;
    }

    private static GateAssignment gate(Zone zone, String gate, Flight flight) {
        GateAssignment ga = new GateAssignment();
        ga.zone = zone;
        ga.gate = gate;
        ga.flight = flight;
        return ga;
    }

    private static Flight departure(
            String number, String airlineName, boolean lowCost, String destination, int pax,
            Instant scheduled, Instant estimated, int delay, AircraftType aircraft) {
        Flight flight = new Flight();
        flight.id = (long) number.hashCode();
        flight.flightNumber = number;
        flight.airline = airline(airlineName, lowCost);
        flight.aircraftType = aircraft;
        flight.direction = FlightDirection.DEPARTURE;
        flight.origin = "AMP";
        flight.destination = destination;
        flight.haul = Haul.INTERNATIONAL;
        flight.paxCount = pax;
        flight.status = delay > 0 ? FlightStatus.DELAYED : FlightStatus.SCHEDULED;
        flight.delayMinutes = delay;
        flight.scheduledTime = scheduled;
        flight.estimatedTime = estimated;
        return flight;
    }

    private static Airline airline(String name, boolean lowCost) {
        Airline airline = new Airline();
        airline.iataCode = name.startsWith("Zephyr") ? "XY" : "ZQ";
        airline.icaoCode = airline.iataCode + "X";
        airline.name = name;
        airline.lowCost = lowCost;
        return airline;
    }

    private static AircraftType widebody() {
        AircraftType type = new AircraftType();
        type.icaoCode = "B77W";
        type.name = "Widebody";
        type.seatsEconomy = 290;
        type.seatsPremium = 28;
        type.seatsBusiness = 42;
        return type;
    }

    private static AircraftType narrowbody() {
        AircraftType type = new AircraftType();
        type.icaoCode = "A321";
        type.name = "Narrowbody";
        type.seatsEconomy = 160;
        type.seatsPremium = 0;
        type.seatsBusiness = 20;
        return type;
    }
}
