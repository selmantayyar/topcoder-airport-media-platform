package com.airportmedia.platform.service.airline;

import com.airportmedia.platform.common.enums.CampaignStatus;
import com.airportmedia.platform.common.enums.CampaignPurpose;
import com.airportmedia.platform.common.enums.FlightDirection;
import com.airportmedia.platform.common.enums.SponsoredContentType;
import com.airportmedia.platform.common.exception.FieldValidationException;
import com.airportmedia.platform.common.util.Jsons;
import com.airportmedia.platform.domain.entity.Advertiser;
import com.airportmedia.platform.domain.entity.Airline;
import com.airportmedia.platform.domain.entity.Campaign;
import com.airportmedia.platform.domain.entity.Creative;
import com.airportmedia.platform.domain.entity.Flight;
import com.airportmedia.platform.domain.entity.GateAssignment;
import com.airportmedia.platform.domain.model.input.SponsoredContentRequest;
import com.airportmedia.platform.domain.model.output.AirlineContentRow;
import com.airportmedia.platform.domain.model.output.AirlineFlightRow;
import com.airportmedia.platform.domain.model.output.AirlineSummary;
import com.airportmedia.platform.repository.AdvertiserRepository;
import com.airportmedia.platform.repository.AirlineRepository;
import com.airportmedia.platform.repository.CampaignRepository;
import com.airportmedia.platform.repository.CreativeRepository;
import com.airportmedia.platform.repository.FlightRepository;
import com.airportmedia.platform.repository.GateAssignmentRepository;
import com.airportmedia.platform.service.FlightBoard;
import com.airportmedia.platform.service.SimulationClock;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Airline Representative's view of their own operation: today's departures, the campaigns they run at their
 * gates, and scheduling sponsored content for a flight's boarding window ({@link FlightBoard#BOARDING_WINDOW_MINUTES}
 * min before departure until departure) — exactly when the pipeline's airline-priority rule applies. Sponsored content is owned by the airline's advertiser record, so the decision
 * pipeline's airline-priority rule boosts it while that flight boards. It goes live without the Media Manager queue:
 * an airline's own gate messaging during its own boarding window is its contracted inventory, and the content is
 * worthless if it waits past the window.
 */
@ApplicationScoped
public class AirlineService {

    /** Budget given to one sponsored boarding-window campaign. */
    static final BigDecimal SPONSORED_BUDGET = BigDecimal.valueOf(2000);

    private final AirlineRepository airlines;
    private final AdvertiserRepository advertisers;
    private final FlightRepository flights;
    private final GateAssignmentRepository gateAssignments;
    private final CampaignRepository campaigns;
    private final CreativeRepository creatives;
    private final SimulationClock clock;
    private final ObjectMapper mapper;

    public AirlineService(
            AirlineRepository airlines,
            AdvertiserRepository advertisers,
            FlightRepository flights,
            GateAssignmentRepository gateAssignments,
            CampaignRepository campaigns,
            CreativeRepository creatives,
            SimulationClock clock,
            ObjectMapper mapper) {
        this.airlines = airlines;
        this.advertisers = advertisers;
        this.flights = flights;
        this.gateAssignments = gateAssignments;
        this.campaigns = campaigns;
        this.creatives = creatives;
        this.clock = clock;
        this.mapper = mapper;
    }

    @Transactional
    public List<AirlineSummary> listAirlines() {
        return airlines.listAll().stream()
                .sorted(Comparator.comparing((Airline a) -> a.name))
                .map(a -> new AirlineSummary(a.id, a.iataCode, a.name, a.lowCost))
                .toList();
    }

    /** The airline's departures scheduled on the current simulated day, in departure order. */
    @Transactional
    public List<AirlineFlightRow> flights(long airlineId) {
        requireAirline(airlineId);
        Instant now = clock.snapshot().simTime();
        Instant dayStart = now.truncatedTo(ChronoUnit.DAYS);
        return flights.list("airline.id = ?1 and direction = ?2 and scheduledTime >= ?3 and scheduledTime < ?4",
                        airlineId, FlightDirection.DEPARTURE, dayStart, dayStart.plus(Duration.ofDays(1))).stream()
                .sorted(Comparator.comparing(Flight::departureTime))
                .map(f -> flightRow(f, now))
                .toList();
    }

    @Transactional
    public List<AirlineContentRow> content(long airlineId) {
        Airline airline = requireAirline(airlineId);
        return airlineAdvertiser(airline)
                .map(a -> campaigns.list("advertiser.id = ?1 and purpose is not null", a.id).stream()
                        .sorted(Comparator.comparing((Campaign c) -> c.scheduleStart).reversed())
                        .map(this::contentRow)
                        .toList())
                .orElse(List.of());
    }

    /** Schedules sponsored content on the flight's gate for its boarding window; live immediately. */
    @Transactional
    public AirlineContentRow schedule(long airlineId, SponsoredContentRequest request) {
        Airline airline = requireAirline(airlineId);
        Flight flight = flights.findByIdOptional(request.flightId())
                .orElseThrow(() -> new NotFoundException("No flight " + request.flightId()));
        if (!flight.airline.id.equals(airline.id) || flight.direction != FlightDirection.DEPARTURE) {
            throw FieldValidationException.of("flightId", flight.flightNumber + " is not a " + airline.name + " departure");
        }
        Instant now = clock.snapshot().simTime();
        Instant departure = flight.departureTime();
        if (!departure.isAfter(now)) {
            throw new ClientErrorException(flight.flightNumber + " has already departed", Response.Status.CONFLICT);
        }
        Advertiser advertiser = airlineAdvertiser(airline)
                .orElseThrow(() -> new NotFoundException("No advertiser record for " + airline.name));
        GateAssignment gate = gateAssignments.findByFlightId(flight.id)
                .orElseThrow(() -> new NotFoundException("No gate for " + flight.flightNumber));

        Instant windowStart = departure.minus(Duration.ofMinutes(FlightBoard.BOARDING_WINDOW_MINUTES));
        Campaign c = new Campaign();
        c.advertiser = advertiser;
        c.name = flight.flightNumber + " — " + label(request.type());
        c.status = CampaignStatus.RUNNING;
        c.purpose = CampaignPurpose.SPONSORED;
        c.linkedFlight = flight;
        c.budget = SPONSORED_BUDGET;
        c.frequencyCapPerScreen = 20;
        c.placementJson = Jsons.write(mapper, Map.of("gates", List.of(gate.gate)));
        c.hardFiltersJson = "{}";
        c.segmentWeightsJson = Jsons.write(mapper, Map.of("business", 0.5, "holiday", 0.5, "family", 0.5,
                "spendTarget", request.type() == SponsoredContentType.UPGRADE_OFFER ? "HIGH" : "MEDIUM"));
        c.scheduleStart = windowStart.isAfter(now) ? windowStart : now;
        c.scheduleEnd = departure;
        c.createdAt = now;
        campaigns.persist(c);
        creatives.persist(creative(c, request, flight, gate.gate));
        return contentRow(c);
    }

    private static Creative creative(Campaign c, SponsoredContentRequest request, Flight flight, String gate) {
        Creative creative = new Creative();
        creative.advertiser = c.advertiser;
        creative.campaign = c;
        creative.name = c.name + " creative";
        creative.templateKey = "promo-basic";
        creative.language = "en";
        creative.headline = blank(request.headline()) ? defaultHeadline(request.type(), flight) : request.headline();
        creative.offerText = blank(request.offerText()) ? defaultOffer(request.type(), flight, gate) : request.offerText();
        creative.body = c.advertiser.name + " — for passengers on " + flight.flightNumber + ".";
        return creative;
    }

    private static String defaultHeadline(SponsoredContentType type, Flight flight) {
        return switch (type) {
            case UPGRADE_OFFER -> "Upgrade your flight to " + flight.destination;
            case LOUNGE_PASS -> "Relax before " + flight.flightNumber;
            case DESTINATION_PARTNER -> "Discover " + flight.destination;
        };
    }

    private static String defaultOffer(SponsoredContentType type, Flight flight, String gate) {
        return switch (type) {
            case UPGRADE_OFFER -> "Business class from $199 — ask at gate " + gate;
            case LOUNGE_PASS -> "Lounge pass $39 — boarding pass required";
            case DESTINATION_PARTNER -> "Partner offers waiting for you in " + flight.destination;
        };
    }

    private static String label(SponsoredContentType type) {
        return switch (type) {
            case UPGRADE_OFFER -> "upgrade offer";
            case LOUNGE_PASS -> "lounge pass";
            case DESTINATION_PARTNER -> "destination partner";
        };
    }

    private AirlineFlightRow flightRow(Flight f, Instant now) {
        String gate = gateAssignments.findByFlightId(f.id).map(g -> g.gate).orElse(null);
        int seats = f.aircraftType.totalSeats();
        return new AirlineFlightRow(f.id, f.flightNumber, f.destination, gate, f.scheduledTime, f.departureTime(),
                FlightBoard.status(f, now), f.delayMinutes, f.paxCount, seats,
                seats > 0 ? Math.round(1000.0 * f.paxCount / seats) / 10.0 : 0,
                FlightBoard.minutesToDeparture(f, now));
    }

    private AirlineContentRow contentRow(Campaign c) {
        Creative creative = creatives.firstForCampaign(c.id).orElse(null);
        String gate = c.linkedFlight != null
                ? gateAssignments.findByFlightId(c.linkedFlight.id).map(g -> g.gate).orElse(null)
                : null;
        return new AirlineContentRow(c.id, c.name, c.purpose != null ? c.purpose.name() : null, c.status.name(),
                c.linkedFlight != null ? c.linkedFlight.flightNumber : null, gate, c.scheduleStart, c.scheduleEnd,
                c.coFunder != null ? c.coFunder.name : null,
                creative != null ? creative.headline : null,
                creative != null ? creative.offerText : null);
    }

    private Optional<Advertiser> airlineAdvertiser(Airline airline) {
        return advertisers.findAirline(airline.name);
    }

    private Airline requireAirline(long airlineId) {
        return airlines.findByIdOptional(airlineId).orElseThrow(() -> new NotFoundException("No airline " + airlineId));
    }


    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
