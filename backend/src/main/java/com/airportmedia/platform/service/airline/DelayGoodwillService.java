package com.airportmedia.platform.service.airline;

import com.airportmedia.platform.common.util.Jsons;
import com.airportmedia.platform.common.enums.CampaignPurpose;
import com.airportmedia.platform.common.enums.CampaignStatus;
import com.airportmedia.platform.common.enums.FlightDirection;
import com.airportmedia.platform.common.enums.FlightStatus;
import com.airportmedia.platform.common.enums.VoucherKind;
import com.airportmedia.platform.domain.entity.Advertiser;
import com.airportmedia.platform.domain.entity.Campaign;
import com.airportmedia.platform.domain.entity.Creative;
import com.airportmedia.platform.domain.entity.Flight;
import com.airportmedia.platform.domain.entity.GateAssignment;
import com.airportmedia.platform.domain.model.output.ServerEvent;
import com.airportmedia.platform.repository.AdvertiserRepository;
import com.airportmedia.platform.repository.CampaignRepository;
import com.airportmedia.platform.repository.CreativeRepository;
import com.airportmedia.platform.repository.FlightRepository;
import com.airportmedia.platform.repository.GateAssignmentRepository;
import com.airportmedia.platform.repository.OutletRepository;
import com.airportmedia.platform.service.EventStream;
import com.airportmedia.platform.service.PromoIssuance;
import com.airportmedia.platform.service.SimulationClock;
import com.airportmedia.platform.service.airline.GoodwillPolicy.PartnerCandidate;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Automatic delay goodwill: when a departure's delay reaches {@code app.airline.delay-voucher-minutes}, a meal (or,
 * at twice that, lounge) voucher campaign is created for the airline, co-funded with a retail partner chosen by
 * {@link GoodwillPolicy}, placed on the flight's gate, live until its boarding closes — with a promo code linked to
 * the flight. No one approves it: it runs on an injected delay and on a periodic scan, at most once per flight.
 */
@ApplicationScoped
public class DelayGoodwillService {

    private static final Logger LOG = Logger.getLogger(DelayGoodwillService.class);
    /** Voucher value per passenger, used to size the campaign budget. */
    private static final Map<VoucherKind, Integer> VALUE_PER_PAX = Map.of(VoucherKind.MEAL, 15, VoucherKind.LOUNGE, 40);

    private final FlightRepository flights;
    private final GateAssignmentRepository gateAssignments;
    private final OutletRepository outlets;
    private final AdvertiserRepository advertisers;
    private final CampaignRepository campaigns;
    private final CreativeRepository creatives;
    private final PromoIssuance promoIssuance;
    private final SimulationClock clock;
    private final EventStream stream;
    private final ObjectMapper mapper;
    private final int thresholdMinutes;
    private final long horizonHours;

    public DelayGoodwillService(
            FlightRepository flights,
            GateAssignmentRepository gateAssignments,
            OutletRepository outlets,
            AdvertiserRepository advertisers,
            CampaignRepository campaigns,
            CreativeRepository creatives,
            PromoIssuance promoIssuance,
            SimulationClock clock,
            EventStream stream,
            ObjectMapper mapper,
            @ConfigProperty(name = "app.airline.delay-voucher-minutes", defaultValue = "60") int thresholdMinutes,
            @ConfigProperty(name = "app.airline.goodwill-horizon-hours", defaultValue = "3") long horizonHours) {
        this.flights = flights;
        this.gateAssignments = gateAssignments;
        this.outlets = outlets;
        this.advertisers = advertisers;
        this.campaigns = campaigns;
        this.creatives = creatives;
        this.promoIssuance = promoIssuance;
        this.clock = clock;
        this.stream = stream;
        this.mapper = mapper;
        this.thresholdMinutes = thresholdMinutes;
        this.horizonHours = horizonHours;
    }

    /** Vouchers for every delayed departure leaving within the horizon that doesn't have one yet. */
    @Transactional
    public int scan() {
        Instant now = clock.snapshot().simTime();
        Instant horizon = now.plus(Duration.ofHours(horizonHours));
        int created = 0;
        for (Flight f : flights.list("direction = ?1 and status = ?2", FlightDirection.DEPARTURE, FlightStatus.DELAYED)) {
            Instant departure = f.departureTime();
            if (departure.isAfter(now) && !departure.isAfter(horizon) && handle(f).isPresent()) {
                created++;
            }
        }
        return created;
    }

    /** Creates this flight's goodwill voucher if its delay qualifies and it has none; returns the new campaign. */
    @Transactional
    public Optional<Campaign> handle(Flight flight) {
        Instant now = clock.snapshot().simTime();
        VoucherKind kind = GoodwillPolicy.voucherFor(flight.delayMinutes, thresholdMinutes);
        if (kind == VoucherKind.NONE || flight.direction != FlightDirection.DEPARTURE
                || !flight.departureTime().isAfter(now) || alreadyHasVoucher(flight)) {
            return Optional.empty();
        }
        Optional<Advertiser> airline = advertisers.findAirline(flight.airline.name);
        Optional<GateAssignment> gate = gateAssignments.findByFlightId(flight.id);
        if (airline.isEmpty() || gate.isEmpty()) {
            return Optional.empty();
        }
        Long terminalId = gate.get().zone.terminal.id;
        Optional<PartnerCandidate> partner = GoodwillPolicy.choosePartner(kind, outlets.listAll().stream()
                .map(o -> new PartnerCandidate(o.advertiser.id, o.advertiser.name, o.advertiser.kind, o.type,
                        o.zone.terminal.id.equals(terminalId)))
                .toList());
        if (partner.isEmpty()) {
            LOG.warnf("No retail partner to co-fund a %s voucher for %s", kind, flight.flightNumber);
            return Optional.empty();
        }

        Campaign c = voucherCampaign(flight, kind, airline.get(), advertisers.findById(partner.get().advertiserId()),
                gate.get().gate, now);
        campaigns.persist(c);
        creatives.persist(voucherCreative(c, kind, flight, airline.get(), c.coFunder));
        promoIssuance.issue(c, flight.departureTime(), flight, null);

        stream.emitAfterCommit(ServerEvent.of("goodwill", Map.of(
                "flightNumber", flight.flightNumber, "voucher", kind.name(), "partner", c.coFunder.name,
                "gate", gate.get().gate)));
        return Optional.of(c);
    }

    private boolean alreadyHasVoucher(Flight flight) {
        return campaigns.count("purpose = ?1 and linkedFlight.id = ?2", CampaignPurpose.GOODWILL, flight.id) > 0;
    }

    private Campaign voucherCampaign(Flight flight, VoucherKind kind, Advertiser airline, Advertiser partner,
            String gate, Instant now) {
        Campaign c = new Campaign();
        c.advertiser = airline;
        c.coFunder = partner;
        c.name = flight.flightNumber + " delay — " + (kind == VoucherKind.LOUNGE ? "lounge pass" : "free meal")
                + " at " + partner.name;
        c.status = CampaignStatus.RUNNING;
        c.purpose = CampaignPurpose.GOODWILL;
        c.linkedFlight = flight;
        c.budget = BigDecimal.valueOf((long) Math.max(1, flight.paxCount) * VALUE_PER_PAX.get(kind));
        c.frequencyCapPerScreen = 20;
        c.placementJson = Jsons.write(mapper, Map.of("gates", List.of(gate)));
        c.hardFiltersJson = "{}";
        c.segmentWeightsJson = Jsons.write(mapper, Map.of("business", 0.5, "holiday", 0.5, "family", 0.5,
                "spendTarget", kind == VoucherKind.LOUNGE ? "MEDIUM" : "LOW"));
        c.scheduleStart = now;
        c.scheduleEnd = flight.departureTime();
        c.createdAt = now;
        return c;
    }

    private static Creative voucherCreative(Campaign c, VoucherKind kind, Flight flight, Advertiser airline,
            Advertiser partner) {
        boolean lounge = kind == VoucherKind.LOUNGE;
        Creative creative = new Creative();
        creative.advertiser = airline;
        creative.campaign = c;
        creative.name = c.name + " creative";
        creative.templateKey = "promo-basic";
        creative.language = "en";
        creative.headline = lounge ? "Delayed? Relax in the lounge" : "Delayed? Your meal's on us";
        creative.offerText = (lounge ? "Lounge pass at " : "Free meal at ") + partner.name + " for " + flight.flightNumber;
        creative.body = "Sorry for the wait. Co-funded by " + airline.name + " and " + partner.name
                + " — scan to claim before boarding closes.";
        creative.translate("es",
                lounge ? "¿Retrasado? Relájate en la sala VIP" : "¿Retrasado? Tu comida corre por nuestra cuenta",
                (lounge ? "Pase a la sala VIP en " : "Comida gratis en ") + partner.name + " para el vuelo " + flight.flightNumber,
                "Disculpa la espera. Cofinanciado por " + airline.name + " y " + partner.name
                        + " — escanea para canjear antes del cierre del embarque.");
        return creative;
    }

}
