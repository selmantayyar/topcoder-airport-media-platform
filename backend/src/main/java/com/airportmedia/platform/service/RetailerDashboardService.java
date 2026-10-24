package com.airportmedia.platform.service;

import com.airportmedia.platform.common.RouteDirectory;
import com.airportmedia.platform.common.enums.FlightStatus;
import com.airportmedia.platform.domain.entity.Advertiser;
import com.airportmedia.platform.domain.entity.Campaign;
import com.airportmedia.platform.domain.entity.Flight;
import com.airportmedia.platform.domain.entity.GateAssignment;
import com.airportmedia.platform.domain.entity.Zone;
import com.airportmedia.platform.domain.model.output.DashboardMetrics;
import com.airportmedia.platform.domain.model.output.DashboardMetrics.CampaignMetric;
import com.airportmedia.platform.domain.model.output.PassengerFlow;
import com.airportmedia.platform.domain.model.output.PassengerFlow.FlowWindow;
import com.airportmedia.platform.domain.model.output.PassengerFlow.LabelCount;
import com.airportmedia.platform.domain.model.output.OutletSummary;
import com.airportmedia.platform.domain.model.output.RetailerSummary;
import com.airportmedia.platform.repository.AdvertiserRepository;
import com.airportmedia.platform.repository.CampaignRepository;
import com.airportmedia.platform.repository.GateAssignmentRepository;
import com.airportmedia.platform.repository.ImpressionRepository;
import com.airportmedia.platform.repository.OutletRepository;
import com.airportmedia.platform.repository.PromoScanRepository;
import com.airportmedia.platform.repository.RedemptionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Powers the retailer dashboard: a company's live impression → scan → redemption funnel with
 * attributed revenue, and a forward-looking footfall view over the terminals where it has outlets.
 */
@ApplicationScoped
public class RetailerDashboardService {

    private static final int TOP_N = 5;

    private final AdvertiserRepository advertisers;
    private final OutletRepository outlets;
    private final CampaignRepository campaigns;
    private final ImpressionRepository impressions;
    private final PromoScanRepository scans;
    private final RedemptionRepository redemptions;
    private final GateAssignmentRepository gateAssignments;
    private final RouteDirectory routes;
    private final SimulationClock clock;
    private final RetailEconomics economics;

    public RetailerDashboardService(
            AdvertiserRepository advertisers,
            OutletRepository outlets,
            CampaignRepository campaigns,
            ImpressionRepository impressions,
            PromoScanRepository scans,
            RedemptionRepository redemptions,
            GateAssignmentRepository gateAssignments,
            RouteDirectory routes,
            SimulationClock clock,
            RetailEconomics economics) {
        this.advertisers = advertisers;
        this.outlets = outlets;
        this.campaigns = campaigns;
        this.impressions = impressions;
        this.scans = scans;
        this.redemptions = redemptions;
        this.gateAssignments = gateAssignments;
        this.routes = routes;
        this.clock = clock;
        this.economics = economics;
    }

    @Transactional
    public List<RetailerSummary> listRetailers() {
        Map<Long, Long> outletCounts = outlets.listAll().stream()
                .collect(Collectors.groupingBy(o -> o.advertiser.id, Collectors.counting()));
        return advertisers.listRetailers().stream()
                .map(a -> new RetailerSummary(a.id, a.name, a.kind.name(),
                        outletCounts.getOrDefault(a.id, 0L).intValue()))
                .toList();
    }

    @Transactional
    public DashboardMetrics metrics(long advertiserId) {
        Advertiser advertiser = requireAdvertiser(advertiserId);

        Map<Long, Long> impByCampaign = impressions.countsByCampaign();
        Map<Long, Long> scanByCampaign = scans.countsByCampaign();
        Map<Long, Long> redByCampaign = redemptions.countsByCampaign();

        List<CampaignMetric> rows = campaigns.findByAdvertiser(advertiserId).stream()
                .map(c -> campaignMetric(c, impByCampaign, scanByCampaign, redByCampaign))
                .sorted(Comparator.comparingLong(CampaignMetric::impressions).reversed())
                .toList();

        long totalImpressions = rows.stream().mapToLong(CampaignMetric::impressions).sum();
        long totalScans = rows.stream().mapToLong(CampaignMetric::scans).sum();
        long totalRedemptions = rows.stream().mapToLong(CampaignMetric::redemptions).sum();

        return new DashboardMetrics(
                advertiser.id, advertiser.name,
                totalImpressions, totalScans, totalRedemptions,
                conversionRate(totalRedemptions, totalImpressions),
                economics.revenue(totalRedemptions),
                rows);
    }

    @Transactional
    public List<OutletSummary> listOutlets(long advertiserId) {
        requireAdvertiser(advertiserId);
        return outlets.findByAdvertiser(advertiserId).stream()
                .map(o -> new OutletSummary(o.id, o.name, o.type.name(),
                        o.zone != null ? o.zone.name : null,
                        terminalOf(o.zone) != null ? o.zone.terminal.code : null))
                .sorted(Comparator.comparing(OutletSummary::name))
                .toList();
    }

    /**
     * Departing footfall "near my store" over the next 60 and 120 minutes. Scoped to one outlet when
     * {@code outletId} is given, otherwise to every outlet the company runs. Proximity is measured at
     * terminal level on purpose: most outlets sit in retail/plaza zones with no gates, so zone-level
     * departures would read zero, whereas every departing passenger in the terminal walks past its
     * shops. Cancelled flights are excluded.
     */
    @Transactional
    public PassengerFlow passengerFlow(long advertiserId, Long outletId) {
        requireAdvertiser(advertiserId);

        Set<Long> terminals = outlets.findByAdvertiser(advertiserId).stream()
                .filter(o -> outletId == null || o.id.equals(outletId))
                .map(o -> terminalOf(o.zone))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (outletId != null && terminals.isEmpty()) {
            throw new NotFoundException("No outlet " + outletId + " for advertiser " + advertiserId);
        }

        Instant now = clock.snapshot().simTime();
        List<GateAssignment> departures = gateAssignments.listDepartures().stream()
                .filter(ga -> terminals.contains(terminalOf(ga.zone)))
                .filter(ga -> ga.flight.status != FlightStatus.CANCELLED)
                .toList();

        return new PassengerFlow(window(departures, now, 60), window(departures, now, 120));
    }

    private Advertiser requireAdvertiser(long advertiserId) {
        return advertisers.findByIdOptional(advertiserId)
                .orElseThrow(() -> new NotFoundException("No advertiser " + advertiserId));
    }

    private static Long terminalOf(Zone zone) {
        return zone != null && zone.terminal != null ? zone.terminal.id : null;
    }

    private CampaignMetric campaignMetric(
            Campaign c, Map<Long, Long> imp, Map<Long, Long> scan, Map<Long, Long> red) {
        long redemptionCount = red.getOrDefault(c.id, 0L);
        return new CampaignMetric(
                c.id, c.name, c.status.name(),
                imp.getOrDefault(c.id, 0L),
                scan.getOrDefault(c.id, 0L),
                redemptionCount,
                economics.revenue(redemptionCount));
    }

    /** Conversion = redemptions ÷ impressions, as a 0..1 fraction to 4 places (0 when nothing has played). */
    private static double conversionRate(long redemptions, long impressions) {
        if (impressions <= 0) {
            return 0.0;
        }
        return BigDecimal.valueOf(redemptions)
                .divide(BigDecimal.valueOf(impressions), 4, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private FlowWindow window(List<GateAssignment> departures, Instant now, int minutes) {
        Map<String, Integer> byDestination = new LinkedHashMap<>();
        Map<String, Integer> byNationality = new LinkedHashMap<>();
        int footfall = 0;

        for (GateAssignment ga : departures) {
            Flight f = ga.flight;
            Instant departure = f.departureTime();
            long mins = Duration.between(now, departure).toMinutes();
            if (mins < 0 || mins > minutes) {
                continue;
            }
            footfall += f.paxCount;
            byDestination.merge(f.destination, f.paxCount, Integer::sum);
            byNationality.merge(routes.lookup(f.destination).language(), f.paxCount, Integer::sum);
        }

        return new FlowWindow(minutes, footfall, topN(byDestination), topN(byNationality));
    }

    private static List<LabelCount> topN(Map<String, Integer> counts) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(TOP_N)
                .map(e -> new LabelCount(e.getKey(), e.getValue()))
                .toList();
    }
}
