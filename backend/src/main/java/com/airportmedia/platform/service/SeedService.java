package com.airportmedia.platform.service;

import com.airportmedia.platform.adapter.SampleAirport;
import com.airportmedia.platform.adapter.SampleAirport.AircraftSpec;
import com.airportmedia.platform.adapter.SampleAirport.AirlineSpec;
import com.airportmedia.platform.adapter.SampleAirport.GateSpec;
import com.airportmedia.platform.adapter.acris.AcrisSourceProvider;
import com.airportmedia.platform.adapter.acris.AcrisTranslator;
import com.airportmedia.platform.adapter.aidx.AidxLegChange;
import com.airportmedia.platform.adapter.aidx.AidxTranslator;
import com.airportmedia.platform.adapter.aidx.FlightEventSourceProvider;
import com.airportmedia.platform.adapter.aodb.AodbCsvCodec;
import com.airportmedia.platform.adapter.aodb.AodbRecord;
import com.airportmedia.platform.adapter.aodb.AodbTranslator;
import com.airportmedia.platform.adapter.aodb.FlightSourceProvider;
import com.airportmedia.platform.common.enums.AdvertiserKind;
import com.airportmedia.platform.common.enums.CampaignStatus;
import com.airportmedia.platform.common.enums.FlightStatus;
import com.airportmedia.platform.common.enums.OutletType;
import com.airportmedia.platform.common.enums.ScreenOrientation;
import com.airportmedia.platform.common.enums.ScreenStatus;
import com.airportmedia.platform.domain.entity.Advertiser;
import com.airportmedia.platform.domain.entity.AircraftType;
import com.airportmedia.platform.domain.entity.Airline;
import com.airportmedia.platform.domain.entity.Campaign;
import com.airportmedia.platform.domain.entity.Checkpoint;
import com.airportmedia.platform.domain.entity.Creative;
import com.airportmedia.platform.domain.entity.Flight;
import com.airportmedia.platform.domain.entity.GateAssignment;
import com.airportmedia.platform.domain.entity.Impression;
import com.airportmedia.platform.domain.entity.Outlet;
import com.airportmedia.platform.domain.entity.PromoCode;
import com.airportmedia.platform.domain.entity.QueueMeasurement;
import com.airportmedia.platform.domain.entity.Redemption;
import com.airportmedia.platform.domain.entity.Screen;
import com.airportmedia.platform.domain.entity.Terminal;
import com.airportmedia.platform.domain.entity.Zone;
import com.airportmedia.platform.domain.model.mapper.FlightEventMapper;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Builds the fictional airport-day by pulling every feed through its adapter and
 * persisting only canonical entities: ACRIS topology, AODB flights, AIDX events, then
 * the platform-owned screens, advertisers, outlets, campaigns and 30-day history.
 */
@ApplicationScoped
public class SeedService {

    private static final Logger LOG = Logger.getLogger(SeedService.class);
    private static final LocalDate DEMO_DAY = LocalDate.parse("2026-10-23");

    private final EntityManager em;
    private final SampleAirport airport;
    private final AcrisSourceProvider acrisSource;
    private final AcrisTranslator acrisTranslator;
    private final FlightSourceProvider flightSource;
    private final AodbTranslator aodbTranslator;
    private final FlightEventSourceProvider eventSource;
    private final AidxTranslator aidxTranslator;
    private final FlightEventMapper flightEventMapper;

    private final int flightCount;
    private final int screenCount;
    private final int outletCount;
    private final int campaignCount;
    private final int historyDays;

    public SeedService(
            EntityManager em,
            SampleAirport airport,
            AcrisSourceProvider acrisSource,
            AcrisTranslator acrisTranslator,
            FlightSourceProvider flightSource,
            AodbTranslator aodbTranslator,
            FlightEventSourceProvider eventSource,
            AidxTranslator aidxTranslator,
            FlightEventMapper flightEventMapper,
            @ConfigProperty(name = "app.seed.flights", defaultValue = "900") int flightCount,
            @ConfigProperty(name = "app.seed.screens", defaultValue = "180") int screenCount,
            @ConfigProperty(name = "app.seed.outlets", defaultValue = "40") int outletCount,
            @ConfigProperty(name = "app.seed.campaigns", defaultValue = "25") int campaignCount,
            @ConfigProperty(name = "app.seed.history-days", defaultValue = "30") int historyDays) {
        this.em = em;
        this.airport = airport;
        this.acrisSource = acrisSource;
        this.acrisTranslator = acrisTranslator;
        this.flightSource = flightSource;
        this.aodbTranslator = aodbTranslator;
        this.eventSource = eventSource;
        this.aidxTranslator = aidxTranslator;
        this.flightEventMapper = flightEventMapper;
        this.flightCount = flightCount;
        this.screenCount = screenCount;
        this.outletCount = outletCount;
        this.campaignCount = campaignCount;
        this.historyDays = historyDays;
    }

    public boolean alreadySeeded() {
        return em.createQuery("select count(t) from Terminal t", Long.class).getSingleResult() > 0;
    }

    @Transactional
    public void seedAll() {
        Random rng = new Random(99);
        Map<String, Zone> zonesByAcris = seedTopology();
        Map<String, Airline> airlines = seedAirlines();
        Map<String, AircraftType> aircraft = seedAircraft();
        Map<String, Zone> gateToZone = gateToZone(zonesByAcris);

        List<Flight> flights = seedFlights(airlines, aircraft, gateToZone);
        int events = seedEvents(flights, airlines);

        List<Advertiser> advertisers = seedAdvertisers(airlines);
        seedOutlets(advertisers, new ArrayList<>(zonesByAcris.values()), rng);
        List<Screen> screens = seedScreens(new ArrayList<>(zonesByAcris.values()), gateToZone, rng);
        List<Campaign> workedExample = seedWorkedExampleCampaigns(advertisers, flights);
        List<Campaign> campaigns = seedCampaigns(advertisers, flights, rng);
        // History seeds only the random campaigns, so the Gate 42 trio stays cold and reproduces
        // the documented worked-example scores (neutral engagement prior).
        seedHistory(campaigns, screens, rng);

        LOG.infof("Seed complete: %d flights, %d AIDX events, %d screens, %d outlets, %d campaigns (+%d worked-example)",
                flights.size(), events, screens.size(), outletCount, campaigns.size(), workedExample.size());
    }

    // --- ACRIS topology ---------------------------------------------------

    private Map<String, Zone> seedTopology() {
        Map<String, Terminal> terminals = new HashMap<>();
        Map<String, Zone> zones = new HashMap<>();
        Map<String, Checkpoint> checkpoints = new HashMap<>();
        Map<String, Checkpoint> checkpointByQueue = new HashMap<>();

        for (JsonNode node : acrisSource.active().entities()) {
            switch (node.get("type").asText()) {
                case "TerminalFacility" -> {
                    Terminal terminal = acrisTranslator.toTerminal(node);
                    em.persist(terminal);
                    terminals.put(terminal.acrisId, terminal);
                }
                case "ZoneAreaLocation" -> {
                    Terminal terminal = terminals.get(ref(node, "refTerminalFacility"));
                    Zone zone = acrisTranslator.toZone(node, terminal);
                    em.persist(zone);
                    zones.put(zone.acrisId, zone);
                }
                case "CheckpointFacility" -> {
                    Zone zone = zones.get(ref(node, "refZoneAreaLocation"));
                    Checkpoint checkpoint = acrisTranslator.toCheckpoint(node, zone);
                    em.persist(checkpoint);
                    checkpoints.put(checkpoint.acrisId, checkpoint);
                }
                case "PassengerQueue" ->
                    // Not a canonical entity; it links a queue id to its checkpoint so
                    // measurements (which reference the queue, as in real FIWARE) resolve.
                    checkpointByQueue.put(node.get("id").asText(), checkpoints.get(ref(node, "refCheckpointFacility")));
                case "QueueMeasurement" -> {
                    Checkpoint checkpoint = checkpointByQueue.get(ref(node, "refPassengerQueue"));
                    em.persist(acrisTranslator.toQueueMeasurement(node, checkpoint));
                }
                default -> { /* ignore unknown ACRIS types */ }
            }
        }
        return zones;
    }

    private static String ref(JsonNode node, String relationship) {
        return node.get(relationship).get("object").asText();
    }

    private Map<String, Zone> gateToZone(Map<String, Zone> zonesByAcris) {
        Map<String, Zone> map = new HashMap<>();
        for (GateSpec gate : airport.gates()) {
            map.put(gate.label(), zonesByAcris.get(gate.zoneAcrisId()));
        }
        return map;
    }

    // --- Reference data ---------------------------------------------------

    private Map<String, Airline> seedAirlines() {
        Map<String, Airline> map = new HashMap<>();
        for (AirlineSpec spec : airport.airlines()) {
            Airline airline = new Airline();
            airline.iataCode = spec.iata();
            airline.icaoCode = spec.icao();
            airline.name = spec.name();
            airline.lowCost = spec.lowCost();
            em.persist(airline);
            map.put(spec.iata(), airline);
        }
        return map;
    }

    private Map<String, AircraftType> seedAircraft() {
        Map<String, AircraftType> map = new HashMap<>();
        for (AircraftSpec spec : airport.aircraft()) {
            AircraftType type = new AircraftType();
            type.icaoCode = spec.icao();
            type.name = spec.name();
            type.seatsEconomy = spec.economy();
            type.seatsPremium = spec.premium();
            type.seatsBusiness = spec.business();
            em.persist(type);
            map.put(spec.icao(), type);
        }
        return map;
    }

    // --- AODB flights -----------------------------------------------------

    private List<Flight> seedFlights(
            Map<String, Airline> airlines, Map<String, AircraftType> aircraft, Map<String, Zone> gateToZone) {
        String csv = flightSource.active().aodbSnapshotCsv(flightCount);
        List<AodbRecord> rows = AodbCsvCodec.decode(csv);
        List<Zone> anyZone = new ArrayList<>(gateToZone.values());
        List<Flight> flights = new ArrayList<>(rows.size());

        int i = 0;
        for (AodbRecord row : rows) {
            Airline airline = airlines.get(row.airlineIata());
            AircraftType type = aircraft.get(row.aircraftIcaoType());
            if (airline == null || type == null) {
                continue;
            }
            Flight flight = aodbTranslator.toFlight(row, airline, type);
            em.persist(flight);

            GateAssignment ga = new GateAssignment();
            ga.flight = flight;
            ga.gate = aodbTranslator.gate(row);
            ga.stand = aodbTranslator.stand(row);
            Zone zone = gateToZone.get(ga.gate);
            ga.zone = zone != null ? zone : anyZone.get(i % anyZone.size());
            em.persist(ga);

            flights.add(flight);
            if (++i % 200 == 0) {
                em.flush();
            }
        }
        em.flush();
        return flights;
    }

    // --- AIDX events (round-tripped through real message XML) -------------

    private int seedEvents(List<Flight> flights, Map<String, Airline> airlines) {
        int created = 0;
        int seq = 1;
        for (Flight flight : flights) {
            if (flight.status != FlightStatus.DELAYED || created >= 60) {
                continue;
            }
            String numeric = flight.flightNumber.replaceAll("^[A-Za-z]+", "");
            String originDate = flight.scheduledTime.atZone(ZoneOffset.UTC).toLocalDate().toString();
            Instant occurredAt = flight.scheduledTime.minusSeconds(30L * 60);
            AidxLegChange change = new AidxLegChange(
                    flight.airline.iataCode, numeric, originDate,
                    flight.delayMinutes, null, "DL", occurredAt,
                    String.format("AMP-AIDX-%05d", seq++));

            String xml = eventSource.active().buildFlightLegNotif(change);
            var update = aidxTranslator.parse(xml);
            em.persist(flightEventMapper.toFlightEvent(update, flight));
            created++;
        }
        em.flush();
        return created;
    }

    // --- Platform-owned ---------------------------------------------------

    private static final String[] RETAIL_NAMES = {
        "Azure Duty-Free", "Bean & Brew Coffee", "Skyline Eatery", "Dune Perfumes",
        "Terminal Toys", "Gadget Port", "Luxe Watches", "Harvest Deli",
        "Cloud Nine Chocolates", "Transit Books", "Peak Sportswear", "Oasis Juice Bar"
    };

    private List<Advertiser> seedAdvertisers(Map<String, Airline> airlines) {
        List<Advertiser> advertisers = new ArrayList<>();
        for (int i = 0; i < RETAIL_NAMES.length; i++) {
            Advertiser a = new Advertiser();
            a.name = RETAIL_NAMES[i];
            a.kind = (i % 3 == 0) ? AdvertiserKind.FNB : AdvertiserKind.RETAILER;
            em.persist(a);
            advertisers.add(a);
        }
        for (Airline airline : airlines.values()) {
            Advertiser a = new Advertiser();
            a.name = airline.name;
            a.kind = AdvertiserKind.AIRLINE;
            em.persist(a);
            advertisers.add(a);
        }
        return advertisers;
    }

    private void seedOutlets(List<Advertiser> advertisers, List<Zone> zones, Random rng) {
        List<Advertiser> retail = advertisers.stream()
                .filter(a -> a.kind != AdvertiserKind.AIRLINE)
                .toList();
        OutletType[] types = OutletType.values();
        for (int i = 0; i < outletCount; i++) {
            Outlet outlet = new Outlet();
            outlet.advertiser = retail.get(i % retail.size());
            outlet.name = outlet.advertiser.name + " #" + (i / retail.size() + 1);
            outlet.type = types[i % types.length];
            outlet.zone = zones.get(rng.nextInt(zones.size()));
            em.persist(outlet);
        }
    }

    private List<Screen> seedScreens(List<Zone> zones, Map<String, Zone> gateToZone, Random rng) {
        Map<Long, List<String>> gatesByZoneId = new HashMap<>();
        for (Map.Entry<String, Zone> e : gateToZone.entrySet()) {
            if (e.getValue() != null) {
                gatesByZoneId.computeIfAbsent(e.getValue().id, k -> new ArrayList<>()).add(e.getKey());
            }
        }
        List<Screen> screens = new ArrayList<>(screenCount);
        for (int i = 0; i < screenCount; i++) {
            Zone zone = zones.get(rng.nextInt(zones.size()));
            Screen screen = new Screen();
            screen.code = String.format("SCR-%03d", i + 1);
            screen.name = zone.name + " Screen " + (i + 1);
            screen.zone = zone;
            List<String> gates = gatesByZoneId.getOrDefault(zone.id, List.of());
            screen.nearestGates = String.join(",", gates.stream().limit(4).toList());
            boolean portrait = rng.nextBoolean();
            screen.orientation = portrait ? ScreenOrientation.PORTRAIT : ScreenOrientation.LANDSCAPE;
            screen.widthPx = portrait ? 1080 : 1920;
            screen.heightPx = portrait ? 1920 : 1080;
            int roll = rng.nextInt(100);
            screen.status = roll < 90 ? ScreenStatus.ONLINE : (roll < 96 ? ScreenStatus.DEGRADED : ScreenStatus.OFFLINE);
            screen.latencyMs = 20 + rng.nextInt(180);
            em.persist(screen);
            screens.add(screen);
        }
        return screens;
    }

    /**
     * The three Gate 42 worked-example campaigns (ADR 0004), with fixed targeting so the live app
     * reproduces the documented decision: the duty-free campaign wins at a gate-42 screen.
     */
    private List<Campaign> seedWorkedExampleCampaigns(List<Advertiser> advertisers, List<Flight> flights) {
        Map<String, Advertiser> byName = new HashMap<>();
        for (Advertiser a : advertisers) {
            byName.putIfAbsent(a.name, a);
        }
        Instant dayStart = DEMO_DAY.atStartOfDay().toInstant(ZoneOffset.UTC);
        String placement = "{\"gates\":[\"40\",\"41\",\"42\",\"43\",\"44\",\"45\"]}";

        record Spec(String advertiser, String offer, String segments, String filters) {}
        List<Spec> specs = List.of(
                new Spec("Azure Duty-Free", "15% off perfume",
                        "{\"business\":0.2,\"holiday\":0.7,\"family\":0.4,\"spendTarget\":\"MEDIUM\"}",
                        "{\"haul\":\"INTERNATIONAL\",\"minDwellMinutes\":15,\"minCrowd\":100}"),
                new Spec("Bean & Brew Coffee", "Free coffee upgrade",
                        "{\"business\":0.4,\"holiday\":0.4,\"family\":0.4,\"spendTarget\":\"LOW\"}",
                        "{\"minCrowd\":50}"),
                new Spec("Luxe Watches", "Premium watch sale",
                        "{\"business\":0.7,\"holiday\":0.2,\"family\":0.1,\"spendTarget\":\"HIGH\"}",
                        "{\"haul\":\"INTERNATIONAL\",\"minCrowd\":100}"));

        List<Campaign> created = new ArrayList<>();
        for (Spec spec : specs) {
            Advertiser advertiser = byName.get(spec.advertiser());
            if (advertiser == null) {
                continue;
            }
            Campaign c = new Campaign();
            c.advertiser = advertiser;
            c.name = advertiser.name + " — " + spec.offer();
            c.status = CampaignStatus.RUNNING;
            c.budget = BigDecimal.valueOf(40000);
            c.dailyCap = BigDecimal.valueOf(5000);
            c.frequencyCapPerScreen = 6;
            c.segmentWeightsJson = spec.segments();
            c.hardFiltersJson = spec.filters();
            c.placementJson = placement;
            c.scheduleStart = dayStart;
            c.scheduleEnd = dayStart.plusSeconds(7L * 86400);
            c.createdAt = dayStart.minusSeconds(86400);
            em.persist(c);

            Creative creative = new Creative();
            creative.advertiser = advertiser;
            creative.campaign = c;
            creative.name = c.name + " creative";
            creative.templateKey = "promo-basic";
            creative.language = "en";
            creative.headline = spec.offer();
            creative.body = "Limited-time offer for travellers at " + SampleAirport.NAME + ".";
            creative.offerText = spec.offer();
            em.persist(creative);

            if (spec.advertiser().equals("Azure Duty-Free")) {
                PromoCode promo = new PromoCode();
                promo.campaign = c;
                promo.code = "AMP-PERFUME15";
                promo.validUntil = dayStart.plusSeconds(86400);
                promo.linkedFlight = flights.isEmpty() ? null : flights.get(0);
                promo.redeemed = false;
                em.persist(promo);
            }
            created.add(c);
        }
        em.flush();
        return created;
    }

    private List<Campaign> seedCampaigns(List<Advertiser> advertisers, List<Flight> flights, Random rng) {
        CampaignStatus[] statuses = {
            CampaignStatus.RUNNING, CampaignStatus.RUNNING, CampaignStatus.APPROVED,
            CampaignStatus.PENDING_APPROVAL, CampaignStatus.ENDED, CampaignStatus.DRAFT,
            CampaignStatus.REJECTED
        };
        Instant dayStart = DEMO_DAY.atStartOfDay().toInstant(ZoneOffset.UTC);
        List<Campaign> campaigns = new ArrayList<>(campaignCount);

        for (int i = 0; i < campaignCount; i++) {
            Advertiser advertiser = advertisers.get(rng.nextInt(advertisers.size()));
            Campaign c = new Campaign();
            c.advertiser = advertiser;
            c.name = advertiser.name + " — " + OFFERS[i % OFFERS.length];
            c.status = statuses[i % statuses.length];
            c.budget = BigDecimal.valueOf(5000 + rng.nextInt(45000));
            c.dailyCap = BigDecimal.valueOf(500 + rng.nextInt(2000));
            c.frequencyCapPerScreen = 2 + rng.nextInt(6);
            c.segmentWeightsJson = "{\"family\":%.1f,\"holiday\":%.1f,\"business\":%.1f}"
                    .formatted(rng.nextDouble(), rng.nextDouble(), rng.nextDouble());
            c.hardFiltersJson = "{\"haul\":\"INTERNATIONAL\",\"minDwellMinutes\":15,\"minCrowd\":100}";
            // Random campaigns target piers A/B/C; Pier D (gate 42) is reserved for the worked example.
            c.placementJson = RANDOM_CAMPAIGN_PLACEMENTS[i % RANDOM_CAMPAIGN_PLACEMENTS.length];
            boolean past = c.status == CampaignStatus.ENDED;
            c.scheduleStart = past ? dayStart.minusSeconds((long) (historyDays) * 86400) : dayStart;
            c.scheduleEnd = past ? dayStart.minusSeconds(86400) : dayStart.plusSeconds(7L * 86400);
            c.createdAt = c.scheduleStart.minusSeconds(86400);
            em.persist(c);

            Creative creative = new Creative();
            creative.advertiser = advertiser;
            creative.campaign = c;
            creative.name = c.name + " creative";
            creative.templateKey = "promo-basic";
            creative.language = (i % 4 == 0) ? "es" : "en";
            creative.headline = OFFERS[i % OFFERS.length];
            creative.body = "Limited-time offer for travellers at " + SampleAirport.NAME + ".";
            creative.offerText = OFFERS[i % OFFERS.length];
            em.persist(creative);

            if (c.status == CampaignStatus.RUNNING) {
                PromoCode promo = new PromoCode();
                promo.campaign = c;
                promo.code = "AMP-" + randomCode(rng);
                promo.validUntil = dayStart.plusSeconds(86400);
                promo.linkedFlight = flights.isEmpty() ? null : flights.get(rng.nextInt(Math.min(50, flights.size())));
                promo.redeemed = false;
                em.persist(promo);
            }
            campaigns.add(c);
        }
        em.flush();
        return campaigns;
    }

    private void seedHistory(List<Campaign> campaigns, List<Screen> screens, Random rng) {
        if (screens.isEmpty()) {
            return;
        }
        String[] segments = {"family", "holiday", "business", "vfr", "student"};
        int persisted = 0;
        for (Campaign c : campaigns) {
            if (c.status != CampaignStatus.RUNNING && c.status != CampaignStatus.ENDED) {
                continue;
            }
            for (int d = 1; d <= historyDays; d++) {
                Instant day = DEMO_DAY.minusDays(d).atStartOfDay().toInstant(ZoneOffset.UTC);
                int impressions = 8 + rng.nextInt(20);
                for (int k = 0; k < impressions; k++) {
                    Impression imp = new Impression();
                    imp.screen = screens.get(rng.nextInt(screens.size()));
                    imp.campaign = c;
                    imp.shownAt = day.plusSeconds(rng.nextInt(86400));
                    em.persist(imp);
                    if (rng.nextInt(100) < 8) {
                        Redemption r = new Redemption();
                        r.campaign = c;
                        r.redeemedAt = imp.shownAt.plusSeconds(rng.nextInt(1800));
                        r.segment = segments[rng.nextInt(segments.length)];
                        em.persist(r);
                    }
                    if (++persisted % 500 == 0) {
                        em.flush();
                    }
                }
            }
        }
        em.flush();
    }

    private static final String[] RANDOM_CAMPAIGN_PLACEMENTS = {
        "{\"gates\":[\"1\",\"2\",\"3\",\"4\",\"5\",\"6\",\"7\",\"8\",\"9\",\"10\"]}",
        "{\"gates\":[\"11\",\"12\",\"13\",\"14\",\"15\",\"16\",\"17\",\"18\",\"19\",\"20\"]}",
        "{\"gates\":[\"21\",\"22\",\"23\",\"24\",\"25\",\"26\",\"27\",\"28\",\"29\",\"30\"]}"
    };

    private static final String[] OFFERS = {
        "15% off perfume", "Buy 2 get 1 free", "Lounge day pass", "Kids meal deal",
        "Premium watch sale", "Festive sweets 20% off", "Free coffee upgrade", "Duty-free spirits"
    };

    private static String randomCode(Random rng) {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        char[] out = new char[6];
        for (int i = 0; i < 6; i++) {
            out[i] = chars.charAt(rng.nextInt(chars.length()));
        }
        return new String(out);
    }
}
