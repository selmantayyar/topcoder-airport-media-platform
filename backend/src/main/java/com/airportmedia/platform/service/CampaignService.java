package com.airportmedia.platform.service;

import com.airportmedia.platform.common.enums.CampaignStatus;
import com.airportmedia.platform.domain.entity.Advertiser;
import com.airportmedia.platform.domain.entity.Campaign;
import com.airportmedia.platform.domain.entity.Creative;
import com.airportmedia.platform.domain.entity.Flight;
import com.airportmedia.platform.domain.entity.GateAssignment;
import com.airportmedia.platform.domain.model.input.CampaignDraft;
import com.airportmedia.platform.domain.model.input.CampaignTargeting;
import com.airportmedia.platform.domain.model.input.CampaignView;
import com.airportmedia.platform.domain.model.input.PromoRequest;
import com.airportmedia.platform.domain.model.output.AudienceMatch;
import com.airportmedia.platform.domain.model.output.BuilderOptions;
import com.airportmedia.platform.domain.model.output.BuilderOptions.FlightOption;
import com.airportmedia.platform.domain.model.output.BuilderOptions.ZoneOption;
import com.airportmedia.platform.domain.model.output.CampaignHistoryRow;
import com.airportmedia.platform.domain.model.output.CreativeSummary;
import com.airportmedia.platform.domain.model.output.PromoIssued;
import com.airportmedia.platform.repository.AdvertiserRepository;
import com.airportmedia.platform.repository.CampaignRepository;
import com.airportmedia.platform.repository.CreativeRepository;
import com.airportmedia.platform.repository.FlightRepository;
import com.airportmedia.platform.repository.GateAssignmentRepository;
import com.airportmedia.platform.repository.ImpressionRepository;
import com.airportmedia.platform.repository.RedemptionRepository;
import com.airportmedia.platform.repository.ZoneRepository;
import com.airportmedia.platform.service.decision.AudienceMatchCalculator;
import com.airportmedia.platform.service.decision.CampaignTargetingParser;
import com.airportmedia.platform.service.decision.DecisionService;
import com.airportmedia.platform.service.decision.TargetingJson;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The retailer's campaign workspace: build and submit a campaign for approval (once the Media Manager
 * approves it, the decision pipeline picks it up on its next tick), preview its audience match before
 * saving, duplicate (as a pre-filled draft) or re-run past campaigns, reuse
 * creatives from the library, issue promo codes, and review history with spend and ROI.
 */
@ApplicationScoped
public class CampaignService {

    /** Platform-wide conversion assumed before any impression has been played. */
    private static final double DEFAULT_CONVERSION = 0.02;
    private static final long DRAFT_ID = -1L;
    private static final Duration UPCOMING_FLIGHTS_WINDOW = Duration.ofHours(6);

    private final AdvertiserRepository advertisers;
    private final CampaignRepository campaigns;
    private final CreativeRepository creatives;
    private final FlightRepository flights;
    private final ZoneRepository zones;
    private final GateAssignmentRepository gateAssignments;
    private final ImpressionRepository impressions;
    private final RedemptionRepository redemptions;
    private final TargetingJson targetingJson;
    private final CampaignTargetingParser targetingParser;
    private final DecisionService decisions;
    private final PromoIssuance issuance;
    private final RetailEconomics economics;
    private final SimulationClock clock;

    public CampaignService(
            AdvertiserRepository advertisers,
            CampaignRepository campaigns,
            CreativeRepository creatives,
            FlightRepository flights,
            ZoneRepository zones,
            GateAssignmentRepository gateAssignments,
            ImpressionRepository impressions,
            RedemptionRepository redemptions,
            TargetingJson targetingJson,
            CampaignTargetingParser targetingParser,
            DecisionService decisions,
            PromoIssuance issuance,
            RetailEconomics economics,
            SimulationClock clock) {
        this.advertisers = advertisers;
        this.campaigns = campaigns;
        this.creatives = creatives;
        this.flights = flights;
        this.zones = zones;
        this.gateAssignments = gateAssignments;
        this.impressions = impressions;
        this.redemptions = redemptions;
        this.targetingJson = targetingJson;
        this.targetingParser = targetingParser;
        this.decisions = decisions;
        this.issuance = issuance;
        this.economics = economics;
        this.clock = clock;
    }

    /**
     * Saves the draft with its creative and submits it for the Media Manager's approval; once approved it
     * becomes RUNNING and the decision pipeline considers it on its next tick.
     */
    @Transactional
    public CampaignHistoryRow create(CampaignDraft draft) {
        Advertiser advertiser = requireAdvertiser(draft.advertiserId());
        TargetingJson.Columns columns = targetingJson.write(draft);

        Campaign c = new Campaign();
        c.advertiser = advertiser;
        c.name = draft.name();
        c.status = CampaignStatus.PENDING_APPROVAL;
        c.budget = draft.budget();
        c.dailyCap = draft.dailyCap();
        c.frequencyCapPerScreen = draft.frequencyCapPerScreen();
        c.placementJson = columns.placementJson();
        c.hardFiltersJson = columns.hardFiltersJson();
        c.segmentWeightsJson = columns.segmentWeightsJson();
        c.scheduleStart = draft.scheduleStart();
        c.scheduleEnd = draft.scheduleEnd();
        c.createdAt = clock.snapshot().simTime();
        campaigns.persist(c);

        if (draft.creativeId() != null) {
            Creative source = requireLibraryCreative(draft.creativeId(), advertiser);
            creatives.persist(creative(c, source.headline, source.offerText, source.body, source.language,
                    source.templateKey));
        } else {
            String language = draft.language() != null && !draft.language().isBlank() ? draft.language() : "en";
            creatives.persist(creative(c, draft.headline(), draft.offerText(), draft.body(), language, "promo-basic"));
        }
        return historyRow(c, Map.of(), Map.of());
    }

    /**
     * The live audience-match preview: the draft competes on every online screen as if it ran now
     * (its schedule is ignored so a future start still previews), through the real pipeline.
     */
    @Transactional
    public AudienceMatch preview(CampaignDraft draft) {
        Advertiser advertiser = requireAdvertiser(draft.advertiserId());
        TargetingJson.Columns columns = targetingJson.write(draft);
        Instant now = clock.snapshot().simTime();

        CampaignView view = new CampaignView(
                DRAFT_ID, draft.name(), advertiser.name, advertiser.kind, CampaignStatus.RUNNING,
                now.minus(Duration.ofHours(1)), now.plus(Duration.ofHours(1)),
                draft.budget(), draft.dailyCap(), BigDecimal.ZERO,
                draft.frequencyCapPerScreen(), 0, 0, 0,
                targetingParser.parse(columns.placementJson(), columns.hardFiltersJson(), columns.segmentWeightsJson()));

        return AudienceMatchCalculator.compute(
                decisions.simulateDraft(view), decisions.intervalSimSeconds(), platformConversion());
    }

    /**
     * One-click re-run: copies a past campaign and its creative, scheduled from now for the original's
     * duration and submitted for approval like any new campaign.
     */
    @Transactional
    public CampaignHistoryRow runAgain(long campaignId) {
        Campaign original = requireCampaign(campaignId);
        Instant now = clock.snapshot().simTime();
        Duration length = Duration.between(original.scheduleStart, original.scheduleEnd);

        Campaign copy = new Campaign();
        copy.advertiser = original.advertiser;
        copy.name = original.name;
        copy.status = CampaignStatus.PENDING_APPROVAL;
        copy.budget = original.budget;
        copy.dailyCap = original.dailyCap;
        copy.frequencyCapPerScreen = original.frequencyCapPerScreen;
        copy.placementJson = original.placementJson;
        copy.hardFiltersJson = original.hardFiltersJson;
        copy.segmentWeightsJson = original.segmentWeightsJson;
        copy.scheduleStart = now;
        copy.scheduleEnd = now.plus(length);
        copy.createdAt = now;
        campaigns.persist(copy);

        creatives.firstForCampaign(original.id).ifPresent(source -> creatives.persist(
                creative(copy, source.headline, source.offerText, source.body, source.language, source.templateKey)));
        return historyRow(copy, Map.of(), Map.of());
    }

    /**
     * One-click duplicate: a past campaign as a pre-filled builder draft (its targeting, budget, and its
     * creative as a library pick), rescheduled from now for the same duration. Saving it creates a new
     * campaign; nothing is persisted until then.
     */
    @Transactional
    public CampaignDraft draftFrom(long campaignId) {
        Campaign c = requireCampaign(campaignId);
        CampaignTargeting t = targetingParser.parse(c.placementJson, c.hardFiltersJson, c.segmentWeightsJson);
        Instant now = clock.snapshot().simTime();
        Duration length = Duration.between(c.scheduleStart, c.scheduleEnd);
        Long creativeId = creatives.firstForCampaign(c.id).map(cr -> cr.id).orElse(null);
        String name = c.name + " (copy)";

        return new CampaignDraft(
                c.advertiser.id, name.length() > 120 ? name.substring(0, 120) : name, creativeId,
                null, null, null, null,
                t.gates(), t.zoneIds(), t.screenIds(), t.destinations(), t.haul(),
                t.minDwellMinutes(), t.minDelayMinutes(), t.minCrowd(),
                t.weightBusiness(), t.weightHoliday(), t.weightFamily(), t.spendTarget(),
                now, now.plus(length), c.budget, c.dailyCap,
                Math.max(1, Math.min(50, c.frequencyCapPerScreen)));
    }

    @Transactional
    public List<CampaignHistoryRow> history(long advertiserId) {
        requireAdvertiser(advertiserId);
        Map<Long, Long> impressionsByCampaign = impressions.countsByCampaign();
        Map<Long, Long> redemptionsByCampaign = redemptions.countsByCampaign();
        return campaigns.findByAdvertiser(advertiserId).stream()
                .sorted(Comparator.comparing((Campaign c) -> c.createdAt).reversed())
                .map(c -> historyRow(c, impressionsByCampaign, redemptionsByCampaign))
                .toList();
    }

    /** The company's creatives, newest first, with identical copy collapsed to one library entry. */
    @Transactional
    public List<CreativeSummary> library(long advertiserId) {
        requireAdvertiser(advertiserId);
        Map<String, CreativeSummary> unique = new LinkedHashMap<>();
        creatives.findByAdvertiser(advertiserId).stream()
                .sorted(Comparator.comparing((Creative c) -> c.id).reversed())
                .forEach(c -> unique.putIfAbsent(c.headline + '|' + c.offerText + '|' + c.body + '|' + c.language,
                        new CreativeSummary(c.id,
                                c.campaign != null ? c.campaign.id : null,
                                c.campaign != null ? c.campaign.name : null,
                                c.headline, c.offerText, c.body, c.language, c.templateKey)));
        return List.copyOf(unique.values());
    }

    /** Issues a time-boxed promo code for a campaign, optionally tied to a flight's boarding close. */
    @Transactional
    public PromoIssued issuePromo(long campaignId, PromoRequest request) {
        Campaign campaign = requireCampaign(campaignId);
        Flight flight = null;
        if (request.flightId() != null) {
            flight = flights.findByIdOptional(request.flightId())
                    .orElseThrow(() -> new NotFoundException("No flight " + request.flightId()));
        }

        Instant now = clock.snapshot().simTime();
        if (flight != null && !now.isBefore(PromoPolicy.boardingCloses(flight.departureTime()))) {
            throw new ClientErrorException(
                    "Boarding for " + flight.flightNumber + " has already closed", Response.Status.CONFLICT);
        }
        return issuance.issue(campaign, now.plus(Duration.ofMinutes(request.validMinutesOrDefault())), flight, null);
    }

    @Transactional
    public BuilderOptions options() {
        Instant now = clock.snapshot().simTime();
        List<ZoneOption> zoneOptions = zones.listAll().stream()
                .map(z -> new ZoneOption(z.id, z.name, z.kind.name(), z.terminal != null ? z.terminal.code : null))
                .sorted(Comparator.comparing(ZoneOption::name))
                .toList();

        List<GateAssignment> departures = gateAssignments.listDepartures();
        List<String> destinations = departures.stream()
                .map(ga -> ga.flight.destination)
                .distinct()
                .sorted()
                .toList();
        List<FlightOption> upcoming = departures.stream()
                .map(ga -> new FlightOption(ga.flight.id, ga.flight.flightNumber, ga.flight.destination,
                        ga.gate, ga.flight.departureTime()))
                .filter(f -> f.departure().isAfter(now) && f.departure().isBefore(now.plus(UPCOMING_FLIGHTS_WINDOW)))
                .sorted(Comparator.comparing(FlightOption::departure))
                .toList();
        return new BuilderOptions(zoneOptions, destinations, upcoming);
    }

    private CampaignHistoryRow historyRow(
            Campaign c, Map<Long, Long> impressionsByCampaign, Map<Long, Long> redemptionsByCampaign) {
        long impressionCount = impressionsByCampaign.getOrDefault(c.id, 0L);
        long redemptionCount = redemptionsByCampaign.getOrDefault(c.id, 0L);
        BigDecimal spend = economics.spend(impressionCount);
        BigDecimal revenue = economics.revenue(redemptionCount);
        return new CampaignHistoryRow(c.id, c.name, c.status.name(), c.scheduleStart, c.scheduleEnd,
                c.budget, spend, impressionCount, redemptionCount, revenue, economics.roi(revenue, spend));
    }

    private double platformConversion() {
        long played = impressions.count();
        return played > 0 ? (double) redemptions.count() / played : DEFAULT_CONVERSION;
    }

    private Creative requireLibraryCreative(long creativeId, Advertiser advertiser) {
        Creative creative = creatives.findByIdOptional(creativeId)
                .orElseThrow(() -> new NotFoundException("No creative " + creativeId));
        if (!creative.advertiser.id.equals(advertiser.id)) {
            throw new BadRequestException("Creative " + creativeId + " belongs to another advertiser");
        }
        return creative;
    }

    /** A campaign's own creative. Library reuse copies the source, so editing one never changes another's ad. */
    private static Creative creative(
            Campaign campaign, String headline, String offerText, String body, String language, String templateKey) {
        Creative c = new Creative();
        c.advertiser = campaign.advertiser;
        c.campaign = campaign;
        c.name = campaign.name + " creative";
        c.templateKey = templateKey;
        c.language = language;
        c.headline = headline;
        c.offerText = offerText;
        c.body = body;
        return c;
    }

    private Advertiser requireAdvertiser(long advertiserId) {
        return advertisers.findByIdOptional(advertiserId)
                .orElseThrow(() -> new NotFoundException("No advertiser " + advertiserId));
    }

    private Campaign requireCampaign(long campaignId) {
        return campaigns.findByIdOptional(campaignId)
                .orElseThrow(() -> new NotFoundException("No campaign " + campaignId));
    }

}
