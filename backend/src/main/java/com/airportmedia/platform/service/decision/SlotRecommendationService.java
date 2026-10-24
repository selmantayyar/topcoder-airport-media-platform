package com.airportmedia.platform.service.decision;

import com.airportmedia.platform.domain.entity.Campaign;
import com.airportmedia.platform.domain.entity.GateAssignment;
import com.airportmedia.platform.domain.model.output.SlotRecommendation;
import com.airportmedia.platform.domain.model.output.SlotRecommendations;
import com.airportmedia.platform.domain.model.output.SlotSample;
import com.airportmedia.platform.repository.CampaignRepository;
import com.airportmedia.platform.repository.GateAssignmentRepository;
import com.airportmedia.platform.service.SimulationClock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * "Best 3 slots for this campaign tomorrow": projects tomorrow's departures from today's schedule
 * ({@link TomorrowProjection}), replays the whole day through the real decision pipeline every
 * {@value #SAMPLE_MINUTES} minutes ({@link DecisionService#simulateDay}), and ranks zone-hours by predicted
 * impressions ({@link SlotRecommender}). Same scoring as live decisions, so the prediction is reproducible.
 */
@ApplicationScoped
public class SlotRecommendationService {

    static final int SAMPLE_MINUTES = 15;
    static final int SLOTS = 3;

    private final CampaignRepository campaigns;
    private final GateAssignmentRepository gateAssignments;
    private final DecisionService decisions;
    private final SimulationClock clock;

    public SlotRecommendationService(
            CampaignRepository campaigns,
            GateAssignmentRepository gateAssignments,
            DecisionService decisions,
            SimulationClock clock) {
        this.campaigns = campaigns;
        this.gateAssignments = gateAssignments;
        this.decisions = decisions;
        this.clock = clock;
    }

    @Transactional
    public SlotRecommendations recommend(long campaignId) {
        Campaign campaign = campaigns.findByIdOptional(campaignId)
                .orElseThrow(() -> new NotFoundException("No campaign " + campaignId));
        Instant today = clock.snapshot().simTime().truncatedTo(ChronoUnit.DAYS);
        Instant tomorrow = today.plus(Duration.ofDays(1));

        List<GateAssignment> projected = TomorrowProjection.project(gateAssignments.listDepartures(), today);
        List<SlotSample> samples = decisions.simulateDay(campaign, projected, tomorrow, SAMPLE_MINUTES);
        List<SlotRecommendation> slots = SlotRecommender.recommend(samples, SLOTS);

        String method = String.format(
                "Tomorrow's %d departures projected from today's schedule (no delays). Every %d min on each "
                        + "online screen the campaign competes through the live decision pipeline against the "
                        + "campaigns scheduled tomorrow, as if approved. Each win = %d min of plays at one per "
                        + "%d s, up to its per-screen frequency cap; ranked by predicted impressions, then "
                        + "average score.",
                projected.size(), SAMPLE_MINUTES, SAMPLE_MINUTES, decisions.intervalSimSeconds());
        return new SlotRecommendations(campaign.id, campaign.name, tomorrow.atZone(ZoneOffset.UTC).toLocalDate(),
                slots, method);
    }
}
