package com.airportmedia.platform.service.media;

import com.airportmedia.platform.common.enums.CampaignStatus;
import com.airportmedia.platform.domain.entity.Advertiser;
import com.airportmedia.platform.domain.entity.Campaign;
import com.airportmedia.platform.domain.entity.Creative;
import com.airportmedia.platform.domain.entity.Screen;
import com.airportmedia.platform.domain.model.input.CampaignTargeting;
import com.airportmedia.platform.domain.model.output.AdvertiserOverviewRow;
import com.airportmedia.platform.domain.model.output.AdvertiserOverviewRow.UnderDelivery;
import com.airportmedia.platform.domain.model.output.ApprovalItem;
import com.airportmedia.platform.domain.model.output.InventoryView;
import com.airportmedia.platform.domain.model.output.InventoryView.ForecastDay;
import com.airportmedia.platform.domain.model.output.InventoryView.ScreenRow;
import com.airportmedia.platform.domain.model.output.InventoryView.Slot;
import com.airportmedia.platform.domain.model.output.ZonePresence;
import com.airportmedia.platform.repository.AdvertiserRepository;
import com.airportmedia.platform.repository.CampaignRepository;
import com.airportmedia.platform.repository.CreativeRepository;
import com.airportmedia.platform.repository.ImpressionRepository;
import com.airportmedia.platform.repository.RedemptionRepository;
import com.airportmedia.platform.repository.ScreenRepository;
import com.airportmedia.platform.service.RetailEconomics;
import com.airportmedia.platform.service.SimulationClock;
import com.airportmedia.platform.service.ZonePresenceService;
import com.airportmedia.platform.service.admin.CreativeQa;
import com.airportmedia.platform.service.decision.CampaignTargetingParser;
import com.airportmedia.platform.service.decision.DecisionService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * The Media Manager's tools: the inventory calendar with yield pricing and a 7-day forecast, the approval
 * queue that gates which campaigns the decision pipeline may play, and the advertiser overview.
 *
 * <p>A slot (screen × hour) is <em>booked</em> when a RUNNING campaign's schedule overlaps the hour and its
 * placement covers the screen; its footfall is the screen's zone headcount from ZonePresence for the middle of
 * that hour (a prediction for hours ahead, the estimate as it unfolded for hours already past). The forecast assumes each coming day repeats today's footfall pattern.
 */
@ApplicationScoped
public class MediaManagerService {

    static final int FIRST_HOUR = 0;
    static final int LAST_HOUR = 23;
    private static final int FORECAST_DAYS = 7;

    private final ScreenRepository screens;
    private final CampaignRepository campaigns;
    private final CreativeRepository creatives;
    private final AdvertiserRepository advertisers;
    private final ImpressionRepository impressions;
    private final RedemptionRepository redemptions;
    private final ZonePresenceService zonePresence;
    private final CampaignTargetingParser targetingParser;
    private final DecisionService decisions;
    private final RetailEconomics economics;
    private final SimulationClock clock;

    public MediaManagerService(
            ScreenRepository screens,
            CampaignRepository campaigns,
            CreativeRepository creatives,
            AdvertiserRepository advertisers,
            ImpressionRepository impressions,
            RedemptionRepository redemptions,
            ZonePresenceService zonePresence,
            CampaignTargetingParser targetingParser,
            DecisionService decisions,
            RetailEconomics economics,
            SimulationClock clock) {
        this.screens = screens;
        this.campaigns = campaigns;
        this.creatives = creatives;
        this.advertisers = advertisers;
        this.impressions = impressions;
        this.redemptions = redemptions;
        this.zonePresence = zonePresence;
        this.targetingParser = targetingParser;
        this.decisions = decisions;
        this.economics = economics;
        this.clock = clock;
    }

    // --- inventory & yield ------------------------------------------------

    @Transactional
    public InventoryView inventory() {
        Instant now = clock.snapshot().simTime();
        Instant dayStart = now.truncatedTo(ChronoUnit.DAYS);
        List<Integer> hours = IntStream.rangeClosed(FIRST_HOUR, LAST_HOUR).boxed().toList();

        Map<Integer, Map<Long, Integer>> headcountByHour = new HashMap<>();
        for (int h : hours) {
            Instant midHour = dayStart.plus(Duration.ofMinutes(h * 60L + 30));
            headcountByHour.put(h, zonePresence.snapshotAt(midHour).stream()
                    .collect(Collectors.toMap(ZonePresence::zoneId, ZonePresence::estimatedHeadcount, (a, b) -> a)));
        }

        List<Screen> allScreens = screens.listAll().stream()
                .sorted(Comparator.comparing((Screen s) -> s.zone.name).thenComparing(s -> s.code))
                .toList();
        Map<Long, int[]> footfallByScreen = new HashMap<>();
        List<Integer> footfalls = new ArrayList<>();
        for (Screen s : allScreens) {
            int[] perHour = hours.stream().mapToInt(h -> headcountByHour.get(h).getOrDefault(s.zone.id, 0)).toArray();
            footfallByScreen.put(s.id, perHour);
            Arrays.stream(perHour).forEach(footfalls::add);
        }
        double median = YieldPricing.medianBusyFootfall(footfalls);
        double premiumThreshold = YieldPricing.premiumThreshold(footfalls);
        BigDecimal basePrice = basePricePerHour();
        List<Booker> bookers = bookers();

        List<ScreenRow> rows = new ArrayList<>();
        int booked = 0;
        for (Screen s : allScreens) {
            Set<String> gates = s.gates();
            List<Slot> slots = new ArrayList<>();
            int[] perHour = footfallByScreen.get(s.id);
            for (int i = 0; i < hours.size(); i++) {
                int h = hours.get(i);
                int footfall = perHour[i];
                Instant hourStart = dayStart.plus(Duration.ofHours(h));
                boolean isBooked = isBooked(bookers, s, gates, hourStart);
                booked += isBooked ? 1 : 0;
                slots.add(new Slot(h, isBooked, footfall >= premiumThreshold, footfall,
                        YieldPricing.suggestedPrice(footfall, median, basePrice)));
            }
            rows.add(new ScreenRow(s.id, s.code, s.zone.id, s.zone.name, slots));
        }

        List<ForecastDay> forecast = forecast(rows, bookers, allScreens, dayStart);
        BigDecimal total = forecast.stream().map(ForecastDay::revenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        int totalSlots = allScreens.size() * hours.size();
        return new InventoryView(LocalDate.ofInstant(dayStart, ZoneOffset.UTC), hours, rows,
                pct(booked, totalSlots), basePrice, premiumThreshold, forecast, total);
    }

    /** Revenue if every booked slot sells at its suggested price, for today and the next six days. */
    private List<ForecastDay> forecast(List<ScreenRow> rows, List<Booker> bookers, List<Screen> allScreens,
            Instant dayStart) {
        Map<Long, Screen> byId = allScreens.stream().collect(Collectors.toMap(s -> s.id, s -> s));
        List<ForecastDay> days = new ArrayList<>();
        for (int d = 0; d < FORECAST_DAYS; d++) {
            Instant day = dayStart.plus(Duration.ofDays(d));
            BigDecimal revenue = BigDecimal.ZERO;
            int booked = 0;
            int total = 0;
            for (ScreenRow row : rows) {
                Screen s = byId.get(row.screenId());
                Set<String> gates = s.gates();
                for (Slot slot : row.slots()) {
                    total++;
                    if (isBooked(bookers, s, gates, day.plus(Duration.ofHours(slot.hour())))) {
                        booked++;
                        revenue = revenue.add(slot.price());
                    }
                }
            }
            days.add(new ForecastDay(LocalDate.ofInstant(day, ZoneOffset.UTC), pct(booked, total), revenue));
        }
        return days;
    }

    /**
     * What one screen-hour is worth at the median footfall: every decision slot in the hour plays an ad,
     * {@code 3600 / app.decision.interval-sim-seconds} slots × the impression price ({@link RetailEconomics}).
     */
    private BigDecimal basePricePerHour() {
        return economics.spend(3600 / decisions.intervalSimSeconds());
    }

    /** A RUNNING campaign's schedule and parsed placement, ready to test slots against. */
    private record Booker(Instant start, Instant end, CampaignTargeting targeting) {}

    private List<Booker> bookers() {
        return campaigns.listRunning().stream()
                .map(c -> new Booker(c.scheduleStart, c.scheduleEnd,
                        targetingParser.parse(c.placementJson, c.hardFiltersJson, c.segmentWeightsJson)))
                .toList();
    }

    private static boolean isBooked(List<Booker> bookers, Screen s, Set<String> gates, Instant hourStart) {
        Instant hourEnd = hourStart.plus(Duration.ofHours(1));
        return bookers.stream().anyMatch(b -> b.start().isBefore(hourEnd) && b.end().isAfter(hourStart)
                && b.targeting().coversScreen(s.id, s.zone.id, gates));
    }


    // --- approval queue ---------------------------------------------------

    @Transactional
    public List<ApprovalItem> approvalQueue() {
        return campaigns.list("status", CampaignStatus.PENDING_APPROVAL).stream()
                .sorted(Comparator.comparing((Campaign c) -> c.createdAt))
                .map(this::approvalItem)
                .toList();
    }

    /** Approve a pending campaign: it becomes RUNNING, so the pipeline's hard filters let it compete. */
    @Transactional
    public ApprovalItem approve(long campaignId) {
        return decide(campaignId, CampaignStatus.RUNNING);
    }

    /** Reject a pending campaign: it stays out of the pipeline for good. */
    @Transactional
    public ApprovalItem reject(long campaignId) {
        return decide(campaignId, CampaignStatus.REJECTED);
    }

    private ApprovalItem decide(long campaignId, CampaignStatus outcome) {
        Campaign c = campaigns.findByIdOptional(campaignId)
                .orElseThrow(() -> new NotFoundException("No campaign " + campaignId));
        if (c.status != CampaignStatus.PENDING_APPROVAL) {
            throw new ClientErrorException("Campaign " + campaignId + " is " + c.status + ", not awaiting approval",
                    Response.Status.CONFLICT);
        }
        c.status = outcome;
        return approvalItem(c);
    }

    private ApprovalItem approvalItem(Campaign c) {
        Creative creative = creatives.firstForCampaign(c.id).orElse(null);
        List<String> problems = creative == null
                ? List.of("no creative attached")
                : CreativeQa.problems(creative.headline, creative.offerText, creative.body, creative.language);
        return new ApprovalItem(c.id, c.name, c.advertiser.name, c.createdAt, c.scheduleStart, c.scheduleEnd,
                c.budget,
                creative != null ? creative.headline : null,
                creative != null ? creative.offerText : null,
                creative != null ? creative.language : null,
                problems);
    }

    // --- advertiser overview ----------------------------------------------

    @Transactional
    public List<AdvertiserOverviewRow> advertiserOverview() {
        Instant now = clock.snapshot().simTime();
        Map<Long, Long> impressionsByCampaign = impressions.countsByCampaign();
        Map<Long, Long> redemptionsByCampaign = redemptions.countsByCampaign();
        Map<Long, List<Campaign>> byAdvertiser = campaigns.listAll().stream()
                .collect(Collectors.groupingBy(c -> c.advertiser.id));

        return advertisers.listAll().stream()
                .map(a -> overviewRow(a, byAdvertiser.getOrDefault(a.id, List.of()),
                        impressionsByCampaign, redemptionsByCampaign, now))
                .sorted(Comparator.comparing(AdvertiserOverviewRow::spend).reversed()
                        .thenComparing(AdvertiserOverviewRow::name))
                .toList();
    }

    private AdvertiserOverviewRow overviewRow(Advertiser a, List<Campaign> owned,
            Map<Long, Long> impressionsByCampaign, Map<Long, Long> redemptionsByCampaign, Instant now) {
        long imps = 0;
        long reds = 0;
        int running = 0;
        List<UnderDelivery> flags = new ArrayList<>();
        for (Campaign c : owned) {
            long campaignImpressions = impressionsByCampaign.getOrDefault(c.id, 0L);
            imps += campaignImpressions;
            reds += redemptionsByCampaign.getOrDefault(c.id, 0L);
            if (c.status != CampaignStatus.RUNNING) {
                continue;
            }
            running++;
            DeliveryPacing.Pace pace = DeliveryPacing.evaluate(
                    economics.spend(campaignImpressions), c.budget, c.scheduleStart, c.scheduleEnd, now);
            if (pace.underDelivering()) {
                flags.add(new UnderDelivery(c.id, c.name, round1(pace.deliveredFraction() * 100),
                        round1(pace.expectedFraction() * 100), round1(pace.pace() * 100)));
            }
        }
        return new AdvertiserOverviewRow(a.id, a.name, a.kind.name(), economics.spend(imps), imps, reds, running, flags);
    }

    private static double pct(int part, int whole) {
        return whole == 0 ? 0 : round1(100.0 * part / whole);
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
