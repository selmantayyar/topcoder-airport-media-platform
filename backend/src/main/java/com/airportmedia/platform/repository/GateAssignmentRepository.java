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

    /** Departures whose gate sits in one zone, for a screen's FIDS strip. */
    public List<GateAssignment> findDeparturesInZone(long zoneId) {
        return list("zone.id = ?1 and flight.direction = ?2", zoneId, FlightDirection.DEPARTURE);
    }
}
