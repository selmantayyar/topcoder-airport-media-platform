package com.airportmedia.platform.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.airportmedia.platform.common.RouteDirectory;
import com.airportmedia.platform.common.enums.AgeBand;
import com.airportmedia.platform.common.enums.FlightDirection;
import com.airportmedia.platform.common.enums.FlightStatus;
import com.airportmedia.platform.common.enums.Haul;
import com.airportmedia.platform.common.enums.PartyType;
import com.airportmedia.platform.common.enums.SpendTier;
import com.airportmedia.platform.common.enums.TripPurpose;
import com.airportmedia.platform.domain.entity.AircraftType;
import com.airportmedia.platform.domain.entity.Airline;
import com.airportmedia.platform.domain.entity.Flight;
import com.airportmedia.platform.domain.model.output.CrowdProfile;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** Pure unit tests — the profiler is a reproducible function of a flight's attributes. */
class CrowdProfilerTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 19);
    private static final LocalDate FRIDAY = LocalDate.of(2026, 10, 23);

    private final CrowdProfiler profiler = new CrowdProfiler(new RouteDirectory());

    @Test
    void mondayMorningDomesticFlightSkewsBusinessAndAgeBand25To44() {
        Flight flight = departure("ZZ101", MONDAY, LocalTime.of(7, 0), Haul.DOMESTIC, "NGT", narrowbody(), carrier(false));

        CrowdProfile profile = profiler.profile(flight);

        assertThat(flight.scheduledTime.atZone(ZoneOffset.UTC).getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(CrowdProfile.dominant(profile.tripPurpose())).isEqualTo(TripPurpose.BUSINESS);
        assertThat(CrowdProfile.dominant(profile.ageBand())).isEqualTo(AgeBand.A25_44);
    }

    @Test
    void fridayEveningHolidayRouteSkewsFamiliesOrCouplesAndLeisure() {
        Flight flight = departure("ZZ202", FRIDAY, LocalTime.of(19, 0), Haul.INTERNATIONAL, "MLE", widebody(), carrier(false));

        CrowdProfile profile = profiler.profile(flight);

        assertThat(CrowdProfile.dominant(profile.tripPurpose())).isEqualTo(TripPurpose.LEISURE);
        assertThat(CrowdProfile.dominant(profile.partyType()))
                .isIn(PartyType.FAMILY, PartyType.COUPLE);
    }

    @Test
    void lowCostCarrierSkewsSpendTierLow() {
        Flight flight = departure("ZZ303", FRIDAY, LocalTime.of(10, 0), Haul.DOMESTIC, "RVT", narrowbody(), carrier(true));

        CrowdProfile profile = profiler.profile(flight);

        assertThat(CrowdProfile.dominant(profile.spendTier())).isEqualTo(SpendTier.LOW);
    }

    @Test
    void businessCabinWidebodyLiftsPremiumSpendAboveALowCostFlight() {
        Flight premium = departure("ZZ404", MONDAY, LocalTime.of(8, 0), Haul.INTERNATIONAL, "LHR", widebody(), carrier(false));
        Flight budget = departure("ZZ405", MONDAY, LocalTime.of(8, 0), Haul.INTERNATIONAL, "LHR", narrowbody(), carrier(true));

        int premiumHigh = profiler.profile(premium).spendTier().get(SpendTier.HIGH);
        int budgetHigh = profiler.profile(budget).spendTier().get(SpendTier.HIGH);

        assertThat(premiumHigh).isGreaterThan(budgetHigh);
    }

    @Test
    void languageAndRegionComeFromTheDestination() {
        Flight flight = departure("ZZ506", FRIDAY, LocalTime.of(12, 0), Haul.INTERNATIONAL, "CDG", widebody(), carrier(false));

        CrowdProfile profile = profiler.profile(flight);

        assertThat(profile.language()).isEqualTo("French");
        assertThat(profile.region()).isEqualTo("Europe");
    }

    @Test
    void everyDistributionSumsTo100() {
        CrowdProfile profile = profiler.profile(
                departure("ZZ607", FRIDAY, LocalTime.of(14, 10), Haul.INTERNATIONAL, "DXB", widebody(), carrier(false)));

        assertThat(profile.tripPurpose().values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(100);
        assertThat(profile.partyType().values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(100);
        assertThat(profile.ageBand().values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(100);
        assertThat(profile.spendTier().values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(100);
    }

    @Test
    void sameFlightAlwaysProducesTheSameProfile() {
        Flight flight = departure("ZZ708", FRIDAY, LocalTime.of(9, 30), Haul.INTERNATIONAL, "SIN", widebody(), carrier(false));

        assertThat(profiler.profile(flight)).isEqualTo(profiler.profile(flight));
    }

    @Test
    void crowdSizeAndDelayContextComeFromTheFlight() {
        Flight flight = departure("ZZ809", FRIDAY, LocalTime.of(16, 0), Haul.INTERNATIONAL, "JFK", widebody(), carrier(false));
        flight.paxCount = 237;
        flight.status = FlightStatus.DELAYED;
        flight.delayMinutes = 45;

        CrowdProfile profile = profiler.profile(flight);

        assertThat(profile.crowdSize()).isEqualTo(237);
        assertThat(profile.delayed()).isTrue();
        assertThat(profile.delayMinutes()).isEqualTo(45);
    }

    // --- builders ---------------------------------------------------------

    private static Flight departure(
            String number, LocalDate date, LocalTime time, Haul haul, String destination,
            AircraftType aircraft, Airline airline) {
        Flight flight = new Flight();
        flight.flightNumber = number;
        flight.airline = airline;
        flight.aircraftType = aircraft;
        flight.direction = FlightDirection.DEPARTURE;
        flight.origin = "AMP";
        flight.destination = destination;
        flight.haul = haul;
        flight.paxCount = Math.min(aircraft.totalSeats(), 180);
        flight.status = FlightStatus.SCHEDULED;
        flight.scheduledTime = Instant.from(date.atTime(time).toInstant(ZoneOffset.UTC));
        return flight;
    }

    private static Airline carrier(boolean lowCost) {
        Airline airline = new Airline();
        airline.iataCode = lowCost ? "ZQ" : "XY";
        airline.icaoCode = lowCost ? "ZQA" : "XYZ";
        airline.name = lowCost ? "Budget Air" : "Full Service Air";
        airline.lowCost = lowCost;
        return airline;
    }

    private static AircraftType widebody() {
        AircraftType type = new AircraftType();
        type.icaoCode = "B77W";
        type.name = "Widebody";
        type.seatsEconomy = 290;
        type.seatsPremium = 28;
        type.seatsBusiness = 42;
        return type;
    }

    private static AircraftType narrowbody() {
        AircraftType type = new AircraftType();
        type.icaoCode = "A320";
        type.name = "Narrowbody";
        type.seatsEconomy = 150;
        type.seatsPremium = 0;
        type.seatsBusiness = 12;
        return type;
    }
}
