package com.airportmedia.platform.repository;

import com.airportmedia.platform.domain.entity.QueueMeasurement;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class QueueMeasurementRepository implements PanacheRepository<QueueMeasurement> {
}
