package com.airportmedia.platform.repository;

import com.airportmedia.platform.common.enums.FlightDirection;
import com.airportmedia.platform.common.enums.FlightStatus;
import com.airportmedia.platform.domain.entity.Flight;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.Optional;

@ApplicationScoped
public class FlightRepository implements PanacheRepository<Flight> {

    public Optional<Flight> findByFlightNumber(String flightNumber) {
        return find("flightNumber", flightNumber).firstResultOptional();
    }

    /**
     * The next departure after {@code now} that hasn't been delayed — the default injection target, so a delay
     * or gate change injected from the control bar lands on a flight that is still to come.
     */
    public Optional<Flight> nextUndelayedDeparture(Instant now) {
        return find("direction = ?1 and status <> ?2 and scheduledTime > ?3 order by scheduledTime",
                FlightDirection.DEPARTURE, FlightStatus.DELAYED, now).firstResultOptional();
    }

    /** Delayed departures that haven't left yet — a live disruption count for the control room. */
    public long countDelayedDeparturesAfter(Instant now) {
        return count("direction = ?1 and status = ?2 and coalesce(estimatedTime, scheduledTime) > ?3",
                FlightDirection.DEPARTURE, FlightStatus.DELAYED, now);
    }
}
