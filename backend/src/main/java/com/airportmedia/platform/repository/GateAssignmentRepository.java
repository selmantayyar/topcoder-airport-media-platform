package com.airportmedia.platform.repository;

import com.airportmedia.platform.domain.entity.GateAssignment;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;

@ApplicationScoped
public class GateAssignmentRepository implements PanacheRepository<GateAssignment> {

    public Optional<GateAssignment> findByFlightId(Long flightId) {
        return find("flight.id", flightId).firstResultOptional();
    }
}
