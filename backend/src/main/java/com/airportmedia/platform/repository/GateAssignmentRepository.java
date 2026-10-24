package com.airportmedia.platform.repository;

import com.airportmedia.platform.common.enums.FlightDirection;
import com.airportmedia.platform.domain.entity.GateAssignment;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class GateAssignmentRepository implements PanacheRepository<GateAssignment> {

    public Optional<GateAssignment> findByFlightId(Long flightId) {
        return find("flight.id", flightId).firstResultOptional();
    }

    /** Every departure's gate assignment — grouped by zone to find flights near each screen. */
    public List<GateAssignment> listDepartures() {
        return list("flight.direction", FlightDirection.DEPARTURE);
    }
}
