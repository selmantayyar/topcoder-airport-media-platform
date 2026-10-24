package com.airportmedia.platform.service.admin;

import com.airportmedia.platform.common.enums.IncidentStatus;
import com.airportmedia.platform.common.enums.ScreenStatus;
import com.airportmedia.platform.domain.entity.Incident;
import com.airportmedia.platform.domain.entity.Screen;
import com.airportmedia.platform.domain.entity.Terminal;
import com.airportmedia.platform.domain.entity.Zone;
import com.airportmedia.platform.domain.model.input.IncidentUpdate;
import com.airportmedia.platform.domain.model.output.AdminOverview;
import com.airportmedia.platform.domain.model.output.AdminOverview.IncidentView;
import com.airportmedia.platform.domain.model.output.AdminOverview.Kpis;
import com.airportmedia.platform.domain.model.output.AdminOverview.ScreenDot;
import com.airportmedia.platform.domain.model.output.AdminOverview.TerminalMap;
import com.airportmedia.platform.domain.model.output.AdminOverview.ZoneMap;
import com.airportmedia.platform.domain.model.output.ServerEvent;
import com.airportmedia.platform.domain.model.output.ZonePresence;
import com.airportmedia.platform.repository.CampaignRepository;
import com.airportmedia.platform.repository.DecisionLogRepository;
import com.airportmedia.platform.repository.IncidentRepository;
import com.airportmedia.platform.repository.ScreenRepository;
import com.airportmedia.platform.repository.TerminalRepository;
import com.airportmedia.platform.repository.ZoneRepository;
import com.airportmedia.platform.service.EventStream;
import com.airportmedia.platform.service.RetailEconomics;
import com.airportmedia.platform.service.SimulationClock;
import com.airportmedia.platform.service.ZonePresenceService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * The Platform Admin control room's live read model — KPIs, the terminal map with screen status and a
 * ZonePresence heat overlay, and the incident tracker — plus operator actions on incidents.
 */
@ApplicationScoped
public class AdminOverviewService {

    private static final int RECENTLY_RESOLVED = 10;

    private final ScreenRepository screens;
    private final ZoneRepository zones;
    private final TerminalRepository terminals;
    private final DecisionLogRepository decisionLogs;
    private final CampaignRepository campaigns;
    private final IncidentRepository incidents;
    private final ZonePresenceService zonePresence;
    private final RetailEconomics economics;
    private final SimulationClock clock;
    private final EventStream stream;
    private final String currency;
    private final BigDecimal currencyRate;

    public AdminOverviewService(
            ScreenRepository screens,
            ZoneRepository zones,
            TerminalRepository terminals,
            DecisionLogRepository decisionLogs,
            CampaignRepository campaigns,
            IncidentRepository incidents,
            ZonePresenceService zonePresence,
            RetailEconomics economics,
            SimulationClock clock,
            EventStream stream,
            @ConfigProperty(name = "app.admin.currency", defaultValue = "USD") String currency,
            @ConfigProperty(name = "app.admin.currency-rate", defaultValue = "1.0") double currencyRate) {
        this.screens = screens;
        this.zones = zones;
        this.terminals = terminals;
        this.decisionLogs = decisionLogs;
        this.campaigns = campaigns;
        this.incidents = incidents;
        this.zonePresence = zonePresence;
        this.economics = economics;
        this.clock = clock;
        this.stream = stream;
        this.currency = currency;
        this.currencyRate = BigDecimal.valueOf(currencyRate);
    }

    @Transactional
    public AdminOverview overview() {
        Instant now = clock.snapshot().simTime();
        List<Screen> allScreens = screens.listAll();
        List<Incident> active = incidents.listActive();
        return new AdminOverview(kpis(now, allScreens, active.size()), terminalMap(now, allScreens), incidentViews(active),
                IncidentRules.OWNERS);
    }

    @Transactional
    public IncidentView updateIncident(long id, IncidentUpdate update) {
        Incident i = incidents.findByIdOptional(id).orElseThrow(() -> new NotFoundException("No incident " + id));
        Instant now = clock.snapshot().simTime();
        if (update.status() != null && update.status() != i.status) {
            boolean resolving = update.status() == IncidentStatus.RESOLVED;
            i.status = update.status();
            i.resolvedAt = resolving ? now : null;
            // An operator resolve holds the incident until its condition clears (see IncidentReconciler).
            i.held = resolving;
        }
        if (update.owner() != null) {
            i.owner = update.owner();
        }
        i.updatedAt = now;
        stream.emitAfterCommit(ServerEvent.of("incident", Map.of("updated", 1)));
        return view(i);
    }

    private Kpis kpis(Instant now, List<Screen> allScreens, int openIncidents) {
        Instant dayStart = now.truncatedTo(ChronoUnit.DAYS);
        long online = allScreens.stream().filter(s -> s.status == ScreenStatus.ONLINE).count();
        long plays = decisionLogs.countPlaysBetween(dayStart, now);
        double latency = allScreens.stream()
                .filter(s -> s.status != ScreenStatus.OFFLINE)
                .mapToInt(s -> s.latencyMs)
                .average().orElse(0);
        BigDecimal revenue = economics.spend(plays).multiply(currencyRate).setScale(2, RoundingMode.HALF_UP);

        return new Kpis(
                allScreens.isEmpty() ? 0 : round1(100.0 * online / allScreens.size()),
                allScreens.size(),
                plays,
                decisionLogs.audienceBetween(dayStart, now),
                revenue,
                currency,
                campaigns.countActiveAt(now),
                round1(latency),
                openIncidents);
    }

    private List<TerminalMap> terminalMap(Instant now, List<Screen> allScreens) {
        Map<Long, Integer> headcount = zonePresence.snapshotAt(now).stream()
                .collect(Collectors.toMap(ZonePresence::zoneId, ZonePresence::estimatedHeadcount, (a, b) -> a));
        int busiest = headcount.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        Map<Long, List<Screen>> screensByZone = allScreens.stream()
                .filter(s -> s.zone != null)
                .collect(Collectors.groupingBy(s -> s.zone.id));
        Map<Long, List<Zone>> zonesByTerminal = zones.listAll().stream()
                .collect(Collectors.groupingBy(z -> z.terminal.id));

        return terminals.listAll().stream()
                .sorted(Comparator.comparing((Terminal t) -> t.code))
                .map(t -> new TerminalMap(t.id, t.code, t.name,
                        zonesByTerminal.getOrDefault(t.id, List.of()).stream()
                                .sorted(Comparator.comparing((Zone z) -> z.kind).thenComparing(z -> z.name))
                                .map(z -> zoneMap(z, headcount.getOrDefault(z.id, 0), busiest,
                                        screensByZone.getOrDefault(z.id, List.of())))
                                .toList()))
                .toList();
    }

    private static ZoneMap zoneMap(Zone z, int headcount, int busiest, List<Screen> zoneScreens) {
        return new ZoneMap(z.id, z.name, z.kind.name(), headcount,
                busiest > 0 ? round2((double) headcount / busiest) : 0,
                zoneScreens.stream()
                        .sorted(Comparator.comparing((Screen s) -> s.code))
                        .map(s -> new ScreenDot(s.id, s.code, s.status.name(), s.latencyMs, s.statusChangedAt))
                        .toList());
    }

    /** Active incidents, most severe and newest first, followed by the most recently resolved. */
    private List<IncidentView> incidentViews(List<Incident> active) {
        Stream<Incident> open = active.stream()
                .sorted(Comparator.comparing((Incident i) -> i.severity, Comparator.reverseOrder())
                        .thenComparing(i -> i.raisedAt, Comparator.reverseOrder()));
        return Stream.concat(open, incidents.recentlyResolved(RECENTLY_RESOLVED).stream())
                .map(AdminOverviewService::view)
                .toList();
    }

    private static IncidentView view(Incident i) {
        return new IncidentView(i.id, i.type.name(), i.severity.name(), i.owner, i.status.name(), i.title, i.detail,
                i.raisedAt, i.updatedAt, i.resolvedAt);
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
