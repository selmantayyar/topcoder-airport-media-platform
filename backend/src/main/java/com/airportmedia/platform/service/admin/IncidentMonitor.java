package com.airportmedia.platform.service.admin;

import com.airportmedia.platform.common.enums.IncidentStatus;
import com.airportmedia.platform.domain.entity.Creative;
import com.airportmedia.platform.domain.entity.Incident;
import com.airportmedia.platform.domain.model.output.ServerEvent;
import com.airportmedia.platform.repository.CreativeRepository;
import com.airportmedia.platform.repository.FlightEventRepository;
import com.airportmedia.platform.repository.IncidentRepository;
import com.airportmedia.platform.repository.QueueMeasurementRepository;
import com.airportmedia.platform.repository.ScreenRepository;
import com.airportmedia.platform.service.EventStream;
import com.airportmedia.platform.service.SimulationClock;
import com.airportmedia.platform.service.admin.IncidentReconciler.ActiveIncident;
import com.airportmedia.platform.service.admin.IncidentReconciler.SeverityChange;
import com.airportmedia.platform.service.admin.IncidentReconciler.Plan;
import com.airportmedia.platform.service.admin.IncidentRules.CreativeFact;
import com.airportmedia.platform.service.admin.IncidentRules.DesiredIncident;
import com.airportmedia.platform.service.admin.IncidentRules.FeedFact;
import com.airportmedia.platform.service.admin.IncidentRules.ScreenFact;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Auto-raises and auto-resolves incidents: gathers the live facts (screen statuses, last data from each feed,
 * creative QA for campaigns that can play now), asks {@link IncidentRules} what should be open, and applies the
 * {@link IncidentReconciler} plan. Changes are broadcast as an {@code incident} event after commit.
 */
@ApplicationScoped
public class IncidentMonitor {

    private final ScreenRepository screens;
    private final FlightEventRepository flightEvents;
    private final QueueMeasurementRepository queueMeasurements;
    private final CreativeRepository creatives;
    private final IncidentRepository incidents;
    private final SimulationClock clock;
    private final EventStream stream;
    private final long feedLagMinutes;

    public IncidentMonitor(
            ScreenRepository screens,
            FlightEventRepository flightEvents,
            QueueMeasurementRepository queueMeasurements,
            CreativeRepository creatives,
            IncidentRepository incidents,
            SimulationClock clock,
            EventStream stream,
            @ConfigProperty(name = "app.admin.feed-lag-minutes", defaultValue = "60") long feedLagMinutes) {
        this.screens = screens;
        this.flightEvents = flightEvents;
        this.queueMeasurements = queueMeasurements;
        this.creatives = creatives;
        this.incidents = incidents;
        this.clock = clock;
        this.stream = stream;
        this.feedLagMinutes = feedLagMinutes;
    }

    /** One scan: what changed. */
    public record ScanResult(int raised, int resolved, int updated) {}

    @Transactional
    public ScanResult scan() {
        Instant now = clock.snapshot().simTime();

        List<ScreenFact> screenFacts = screens.listAll().stream()
                .map(s -> new ScreenFact(s.id, s.code, s.status, s.statusChangedAt))
                .toList();
        List<FeedFact> feedFacts = List.of(
                new FeedFact("AIDX", flightEvents.latestOccurredBy(now)),
                new FeedFact("ACRIS", queueMeasurements.latestMeasuredBy(now)));
        List<CreativeFact> creativeFacts = creatives.listForActiveCampaigns(now).stream()
                .map(IncidentMonitor::qa)
                .toList();

        List<DesiredIncident> desired = IncidentRules.evaluate(now, screenFacts, feedFacts, creativeFacts, feedLagMinutes);
        Plan plan = IncidentReconciler.plan(desired, incidents.listReconcilable().stream()
                .map(i -> new ActiveIncident(i.id, i.subjectKey, i.severity, i.held))
                .toList());

        plan.raise().forEach(d -> incidents.persist(newIncident(d, now)));
        for (Long id : plan.resolve()) {
            Incident i = incidents.findById(id);
            i.status = IncidentStatus.RESOLVED;
            i.resolvedAt = now;
            i.updatedAt = now;
        }
        for (Long id : plan.release()) {
            Incident i = incidents.findById(id);
            i.held = false;
            i.updatedAt = now;
        }
        for (SeverityChange e : plan.update()) {
            Incident i = incidents.findById(e.incidentId());
            i.severity = e.desired().severity();
            i.title = e.desired().title();
            i.detail = e.desired().detail();
            i.updatedAt = now;
        }

        ScanResult result = new ScanResult(plan.raise().size(), plan.resolve().size(), plan.update().size());
        // Releasing a hold changes nothing on screen, so it doesn't warrant a broadcast.
        if (result.raised() + result.resolved() + result.updated() > 0) {
            stream.emitAfterCommit(ServerEvent.of("incident", Map.of(
                    "raised", result.raised(), "resolved", result.resolved(), "updated", result.updated())));
        }
        return result;
    }

    private static CreativeFact qa(Creative c) {
        return new CreativeFact(c.id, c.campaign.name, CreativeQa.problems(c.headline, c.offerText, c.body, c.language));
    }

    private static Incident newIncident(DesiredIncident d, Instant now) {
        Incident i = new Incident();
        i.subjectKey = d.subjectKey();
        i.type = d.type();
        i.severity = d.severity();
        i.owner = d.owner();
        i.status = IncidentStatus.OPEN;
        i.title = d.title();
        i.detail = d.detail();
        i.raisedAt = now;
        i.updatedAt = now;
        return i;
    }
}
