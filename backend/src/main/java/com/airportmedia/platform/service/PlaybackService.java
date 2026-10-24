package com.airportmedia.platform.service;

import com.airportmedia.platform.domain.entity.Creative;
import com.airportmedia.platform.domain.entity.Flight;
import com.airportmedia.platform.domain.entity.GateAssignment;
import com.airportmedia.platform.domain.entity.PromoCode;
import com.airportmedia.platform.domain.entity.Screen;
import com.airportmedia.platform.domain.model.output.DecisionResult;
import com.airportmedia.platform.domain.model.output.PlaybackView;
import com.airportmedia.platform.domain.model.output.PlaybackView.CreativeView;
import com.airportmedia.platform.domain.model.output.PlaybackView.FidsEntry;
import com.airportmedia.platform.domain.model.output.PlaybackView.PromoPlayback;
import com.airportmedia.platform.domain.model.output.ScreenSummary;
import com.airportmedia.platform.repository.CreativeRepository;
import com.airportmedia.platform.repository.GateAssignmentRepository;
import com.airportmedia.platform.repository.PromoCodeRepository;
import com.airportmedia.platform.repository.ScreenRepository;
import com.airportmedia.platform.service.decision.DecisionService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * The passenger-screen read model: assembles what one screen should render now — its current
 * decision (via the pipeline, side-effect free), the winning creative and claimable promo, and a
 * FIDS-style strip of nearby flights — so the kiosk view and the "Why this ad?" overlay are one fetch.
 */
@ApplicationScoped
public class PlaybackService {

    private static final int FIDS_LIMIT = 8;
    /** FIDS shows flights from 20 minutes after departure to 4 hours before it. */
    private static final long FIDS_SHOW_AFTER_DEPARTURE_MINUTES = 20;
    private static final long FIDS_SHOW_BEFORE_DEPARTURE_MINUTES = 240;

    private final ScreenRepository screens;
    private final GateAssignmentRepository gateAssignments;
    private final CreativeRepository creatives;
    private final PromoCodeRepository promoCodes;
    private final DecisionService decisions;
    private final SimulationClock clock;

    public PlaybackService(
            ScreenRepository screens,
            GateAssignmentRepository gateAssignments,
            CreativeRepository creatives,
            PromoCodeRepository promoCodes,
            DecisionService decisions,
            SimulationClock clock) {
        this.screens = screens;
        this.gateAssignments = gateAssignments;
        this.creatives = creatives;
        this.promoCodes = promoCodes;
        this.decisions = decisions;
        this.clock = clock;
    }

    @Transactional
    public List<ScreenSummary> listScreens(String gate, String zone) {
        return screens.listAll().stream()
                .filter(s -> gate == null || gate.isBlank() || s.gates().contains(gate.trim()))
                .filter(s -> zone == null || zone.isBlank()
                        || (s.zone != null && s.zone.name.equalsIgnoreCase(zone.trim())))
                .map(PlaybackService::summary)
                .sorted(Comparator.comparing(ScreenSummary::code))
                .toList();
    }

    @Transactional
    public PlaybackView playback(long screenId) {
        Screen screen = screens.findByIdOptional(screenId)
                .orElseThrow(() -> new NotFoundException("No screen " + screenId));
        Instant now = clock.snapshot().simTime();
        DecisionResult decision = decisions.preview(screenId);

        CreativeView creative = null;
        PromoPlayback promo = null;
        if (decision.winnerCampaignId() != null) {
            creative = creatives.firstForCampaign(decision.winnerCampaignId())
                    .map(PlaybackService::creativeView)
                    .orElse(null);
            promo = promoCodes.listClaimableForCampaign(decision.winnerCampaignId()).stream()
                    .filter(p -> now.isBefore(PromoPolicy.effectiveExpiry(p)))
                    .findFirst()
                    .map(p -> promoPlayback(p, now))
                    .orElse(null);
        }
        return new PlaybackView(summary(screen), now, fids(screen, now), decision, creative, promo);
    }

    private List<FidsEntry> fids(Screen screen, Instant now) {
        if (screen.zone == null) {
            return List.of();
        }
        return gateAssignments.findDeparturesInZone(screen.zone.id).stream()
                .map(ga -> fidsEntry(ga, now))
                .filter(e -> e.minutesToDeparture() >= -FIDS_SHOW_AFTER_DEPARTURE_MINUTES
                        && e.minutesToDeparture() <= FIDS_SHOW_BEFORE_DEPARTURE_MINUTES)
                .sorted(Comparator.comparingLong(FidsEntry::minutesToDeparture))
                .limit(FIDS_LIMIT)
                .toList();
    }

    private static FidsEntry fidsEntry(GateAssignment ga, Instant now) {
        Flight f = ga.flight;
        Instant departure = f.departureTime();
        long minutes = Duration.between(now, departure).toMinutes();
        return new FidsEntry(f.flightNumber, f.airline.name, f.destination, ga.gate,
                f.scheduledTime, f.estimatedTime, FlightBoard.status(f, now), minutes);
    }


    private static ScreenSummary summary(Screen s) {
        return new ScreenSummary(
                s.id, s.code, s.name,
                s.zone != null ? s.zone.id : null,
                s.zone != null ? s.zone.name : null,
                s.nearestGates, s.orientation.name(), s.status.name());
    }

    private static CreativeView creativeView(Creative c) {
        return new CreativeView(
                c.advertiser.name, c.headline, c.body, c.offerText, c.language, c.templateKey);
    }

    private static PromoPlayback promoPlayback(PromoCode promo, Instant now) {
        Instant expiresAt = PromoPolicy.effectiveExpiry(promo);
        long secondsRemaining = Math.max(0, Duration.between(now, expiresAt).getSeconds());
        return new PromoPlayback(promo.code, expiresAt, secondsRemaining, promo.claimPath());
    }

}
