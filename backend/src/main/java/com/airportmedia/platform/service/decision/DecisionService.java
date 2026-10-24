package com.airportmedia.platform.service.decision;

import com.airportmedia.platform.domain.entity.Campaign;
import com.airportmedia.platform.domain.entity.DecisionLog;
import com.airportmedia.platform.domain.entity.Flight;
import com.airportmedia.platform.domain.entity.GateAssignment;
import com.airportmedia.platform.domain.entity.Impression;
import com.airportmedia.platform.domain.entity.Screen;
import com.airportmedia.platform.domain.model.input.CampaignTargeting;
import com.airportmedia.platform.domain.model.input.CampaignView;
import com.airportmedia.platform.domain.model.input.DecisionInputs;
import com.airportmedia.platform.domain.model.input.DecisionInputs.NearbyFlight;
import com.airportmedia.platform.domain.model.output.CandidateScore;
import com.airportmedia.platform.domain.model.output.AudienceMatch.ScreenOutcome;
import com.airportmedia.platform.domain.model.output.DecisionResult;
import com.airportmedia.platform.domain.model.output.ZonePresence;
import com.airportmedia.platform.repository.CampaignRepository;
import com.airportmedia.platform.repository.DecisionLogRepository;
import com.airportmedia.platform.repository.GateAssignmentRepository;
import com.airportmedia.platform.repository.ImpressionRepository;
import com.airportmedia.platform.repository.RedemptionRepository;
import com.airportmedia.platform.repository.ScreenRepository;
import com.airportmedia.platform.service.RetailEconomics;
import com.airportmedia.platform.service.SimulationClock;
import com.airportmedia.platform.service.ZonePresenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import com.airportmedia.platform.common.enums.AdvertiserKind;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Assembles {@link DecisionInputs} for a screen from live repositories, runs the pure
 * {@link DecisionPipeline}, and (when executing) persists the full evidence trail as a
 * {@link DecisionLog} and records the winner's {@link Impression} — the "choose & log" and
 * "learn" stages. One ZonePresence/campaign snapshot is shared across a batch of screens so the
 * continuous re-run loop stays cheap.
 */
@ApplicationScoped
public class DecisionService {

    private final SimulationClock clock;
    private final ZonePresenceService zonePresence;
    private final DecisionPipeline pipeline;
    private final HardFilters hardFilters;
    private final CampaignTargetingParser targetingParser;
    private final CampaignRepository campaigns;
    private final ScreenRepository screens;
    private final GateAssignmentRepository gateAssignments;
    private final ImpressionRepository impressions;
    private final RedemptionRepository redemptions;
    private final DecisionLogRepository decisionLogs;
    private final ObjectMapper mapper;

    private final RetailEconomics economics;
    private final int frequencyWindowMinutes;
    private final int intervalSimSeconds;
    private final int screenBatch;

    /** In-memory so the continuous loop can pace itself and apply competitor separation across ticks. */
    private final Map<Long, Instant> lastDecisionAt = new ConcurrentHashMap<>();
    private final Map<Long, LastWinner> lastWinners = new ConcurrentHashMap<>();

    private record LastWinner(AdvertiserKind kind, String advertiser, Instant at) {}

    public DecisionService(
            SimulationClock clock,
            ZonePresenceService zonePresence,
            DecisionPipeline pipeline,
            HardFilters hardFilters,
            CampaignTargetingParser targetingParser,
            CampaignRepository campaigns,
            ScreenRepository screens,
            GateAssignmentRepository gateAssignments,
            ImpressionRepository impressions,
            RedemptionRepository redemptions,
            DecisionLogRepository decisionLogs,
            ObjectMapper mapper,
            RetailEconomics economics,
            @ConfigProperty(name = "app.decision.frequency-window-minutes", defaultValue = "60") int frequencyWindowMinutes,
            @ConfigProperty(name = "app.decision.interval-sim-seconds", defaultValue = "15") int intervalSimSeconds,
            @ConfigProperty(name = "app.decision.screen-batch", defaultValue = "40") int screenBatch) {
        this.clock = clock;
        this.zonePresence = zonePresence;
        this.pipeline = pipeline;
        this.hardFilters = hardFilters;
        this.targetingParser = targetingParser;
        this.campaigns = campaigns;
        this.screens = screens;
        this.gateAssignments = gateAssignments;
        this.impressions = impressions;
        this.redemptions = redemptions;
        this.decisionLogs = decisionLogs;
        this.mapper = mapper;
        this.economics = economics;
        this.frequencyWindowMinutes = frequencyWindowMinutes;
        this.intervalSimSeconds = intervalSimSeconds;
        this.screenBatch = screenBatch;
    }

    /** Compute the current decision for a screen without side effects (read view / "why this ad?"). */
    @Transactional
    public DecisionResult preview(long screenId) {
        Screen screen = screens.findByIdOptional(screenId)
                .orElseThrow(() -> new NotFoundException("No screen " + screenId));
        Instant now = clock.snapshot().simTime();
        return decide(screen, loadShared(now), now);
    }

    /** Compute, persist the evidence trail, and record the winner's impression for one screen. */
    @Transactional
    public DecisionResult decideAndExecute(long screenId) {
        Screen screen = screens.findByIdOptional(screenId)
                .orElseThrow(() -> new NotFoundException("No screen " + screenId));
        Instant now = clock.snapshot().simTime();
        DecisionInputs inputs = buildInputs(screen, loadShared(now), now);
        DecisionResult result = pipeline.decide(inputs);
        record(screen, inputs, result, now);
        return result;
    }

    /** The continuous loop's unit of work: decide for every online screen that is due. */
    @Transactional
    public int runDueScreens() {
        Instant now = clock.snapshot().simTime();
        List<Screen> due = screens.listOnline().stream()
                .filter(screen -> isDue(screen.id, now))
                .limit(screenBatch)
                .toList();
        if (due.isEmpty()) {
            return 0;
        }
        Shared shared = loadShared(now);
        for (Screen screen : due) {
            DecisionInputs inputs = buildInputs(screen, shared, now);
            record(screen, inputs, pipeline.decide(inputs), now);
        }
        return due.size();
    }

    /**
     * What a not-yet-saved campaign would do if it ran right now: on every online screen, the draft
     * competes against the live campaigns through the real pipeline (no side effects). Feeds the
     * builder's audience-match preview, so the estimate uses exactly the logic that will pick it.
     */
    @Transactional
    public List<ScreenOutcome> simulateDraft(CampaignView draft) {
        Instant now = clock.snapshot().simTime();
        Shared shared = loadShared(now);
        List<ScreenOutcome> outcomes = new ArrayList<>();
        for (Screen screen : screens.listOnline()) {
            DecisionInputs live = buildInputs(screen, shared, now);
            List<CampaignView> candidates = new ArrayList<>(live.campaigns());
            candidates.add(draft);
            DecisionInputs inputs = live.withCampaigns(candidates);

            DecisionResult result = pipeline.decide(inputs);
            CandidateScore score = result.candidates().stream()
                    .filter(c -> c.campaignId() == draft.campaignId())
                    .findFirst()
                    .orElse(null);
            boolean eligible = score != null && score.eligible();
            outcomes.add(new ScreenOutcome(
                    hardFilters.firstStaticFailure(draft, inputs) == null,
                    eligible,
                    !result.fallback() && Objects.equals(result.winnerCampaignId(), draft.campaignId()),
                    eligible ? score.crowdFit() : 0.0,
                    inputs.headcount()));
        }
        return outcomes;
    }

    public int intervalSimSeconds() {
        return intervalSimSeconds;
    }

    private boolean isDue(long screenId, Instant now) {
        Instant last = lastDecisionAt.get(screenId);
        return last == null || Duration.between(last, now).abs().toSeconds() >= intervalSimSeconds;
    }

    // --- core -------------------------------------------------------------

    private DecisionResult decide(Screen screen, Shared shared, Instant now) {
        return pipeline.decide(buildInputs(screen, shared, now));
    }

    private DecisionInputs buildInputs(Screen screen, Shared shared, Instant now) {
        Long zoneId = screen.zone != null ? screen.zone.id : null;
        ZonePresence presence = zoneId != null ? shared.presenceByZone().get(zoneId) : null;
        int headcount = presence != null ? presence.estimatedHeadcount() : 0;

        List<NearbyFlight> nearby = nearbyFlights(zoneId, shared, now);
        List<CampaignView> views = shared.campaigns().stream()
                .map(c -> view(c, screen.id, shared))
                .toList();

        LastWinner recent = recentWinner(screen.id, now);
        return new DecisionInputs(
                screen.id, zoneId, screen.gates(), now, now, headcount,
                presence != null ? presence.profile() : null,
                nearby, views,
                recent != null ? recent.kind() : null,
                recent != null ? recent.advertiser() : null,
                shared.dayStart(), shared.dayEnd());
    }

    private List<NearbyFlight> nearbyFlights(Long zoneId, Shared shared, Instant now) {
        if (zoneId == null) {
            return List.of();
        }
        List<NearbyFlight> out = new ArrayList<>();
        for (GateAssignment ga : shared.departuresByZone().getOrDefault(zoneId, List.of())) {
            Flight f = ga.flight;
            Instant departure = f.departureTime();
            long minutes = Duration.between(now, departure).toMinutes();
            if (ZonePresenceService.presenceFraction(minutes) <= 0) {
                continue;
            }
            boolean boarding = minutes >= -5 && minutes <= 40;
            out.add(new NearbyFlight(f.flightNumber, f.airline.name, f.destination, f.haul, minutes, boarding));
        }
        out.sort(Comparator.comparingLong(NearbyFlight::minutesToDeparture));
        return out;
    }

    private CampaignView view(Campaign c, long screenId, Shared shared) {
        long impressionsTotal = shared.impressionTotals().getOrDefault(c.id, 0L);
        long redemptionsTotal = shared.redemptionTotals().getOrDefault(c.id, 0L);
        long windowCount = shared.windowCounts().getOrDefault(screenId, Map.of()).getOrDefault(c.id, 0L);
        BigDecimal spent = economics.spend(impressionsTotal);
        CampaignTargeting targeting = targetingParser.parse(c.placementJson, c.hardFiltersJson, c.segmentWeightsJson);
        return new CampaignView(
                c.id, c.name, c.advertiser.name, c.advertiser.kind, c.status,
                c.scheduleStart, c.scheduleEnd, c.budget, c.dailyCap, spent,
                c.frequencyCapPerScreen, (int) windowCount, impressionsTotal, redemptionsTotal, targeting);
    }

    private LastWinner recentWinner(long screenId, Instant now) {
        LastWinner last = lastWinners.get(screenId);
        if (last == null || Duration.between(last.at(), now).abs().toMinutes() > frequencyWindowMinutes) {
            return null;
        }
        return last;
    }


    // --- persistence ------------------------------------------------------

    private void record(Screen screen, DecisionInputs inputs, DecisionResult result, Instant now) {
        DecisionLog log = new DecisionLog();
        log.screen = screen;
        log.zone = screen.zone;
        log.decidedAt = result.decidedAt();
        log.headcount = result.headcount();
        log.winnerName = result.winnerName();
        log.reason = truncate(result.reason(), 512);
        log.fallback = result.fallback();
        log.inputsJson = json(inputs);
        log.profileJson = json(result.profile());
        log.candidatesJson = json(result.candidates());

        if (result.winnerCampaignId() != null) {
            Campaign winner = campaigns.findById(result.winnerCampaignId());
            log.winnerCampaign = winner;
            decisionLogs.persist(log);

            Impression impression = new Impression();
            impression.screen = screen;
            impression.campaign = winner;
            impression.shownAt = now;
            impressions.persist(impression);

            CandidateScore winning = result.candidates().stream()
                    .filter(c -> c.campaignId() == result.winnerCampaignId())
                    .findFirst()
                    .orElse(null);
            if (winning != null) {
                lastWinners.put(screen.id, new LastWinner(winning.advertiserKind(), winning.advertiserName(), now));
            }
        } else {
            decisionLogs.persist(log);
        }
        lastDecisionAt.put(screen.id, now);
    }

    // --- shared snapshot --------------------------------------------------

    private Shared loadShared(Instant now) {
        Map<Long, ZonePresence> presenceByZone = zonePresence.snapshotAt(now).stream()
                .filter(zp -> zp.zoneId() != null)
                .collect(Collectors.toMap(ZonePresence::zoneId, Function.identity(), (a, b) -> a));

        Map<Long, List<GateAssignment>> departuresByZone = new HashMap<>();
        for (GateAssignment ga : gateAssignments.listDepartures()) {
            if (ga.zone != null) {
                departuresByZone.computeIfAbsent(ga.zone.id, k -> new ArrayList<>()).add(ga);
            }
        }

        Instant windowSince = now.minus(Duration.ofMinutes(frequencyWindowMinutes));
        Instant dayStart = now.truncatedTo(ChronoUnit.DAYS);
        return new Shared(
                now,
                presenceByZone,
                campaigns.listScheduledAt(now),
                impressions.countsByCampaign(),
                redemptions.countsByCampaign(),
                departuresByZone,
                impressions.windowCountsByScreenAndCampaign(windowSince),
                dayStart,
                dayStart.plus(Duration.ofDays(1)));
    }

    private String json(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static String truncate(String value, int max) {
        return value != null && value.length() > max ? value.substring(0, max) : value;
    }

    private record Shared(
            Instant now,
            Map<Long, ZonePresence> presenceByZone,
            List<Campaign> campaigns,
            Map<Long, Long> impressionTotals,
            Map<Long, Long> redemptionTotals,
            Map<Long, List<GateAssignment>> departuresByZone,
            Map<Long, Map<Long, Long>> windowCounts,
            Instant dayStart,
            Instant dayEnd) {}
}
