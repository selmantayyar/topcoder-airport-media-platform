package com.airportmedia.platform.repository;

import com.airportmedia.platform.common.enums.FlightDirection;
import com.airportmedia.platform.common.enums.FlightStatus;
import com.airportmedia.platform.domain.entity.Flight;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;

@ApplicationScoped
public class FlightRepository implements PanacheRepository<Flight> {

    public Optional<Flight> findByFlightNumber(String flightNumber) {
        return find("flightNumber", flightNumber).firstResultOptional();
    }

    /** A departure that has not yet been delayed — a sensible default injection target. */
    public Optional<Flight> firstUndelayedDeparture() {
        return find("direction = ?1 and status <> ?2", FlightDirection.DEPARTURE, FlightStatus.DELAYED)
                .firstResultOptional();
    }
}
