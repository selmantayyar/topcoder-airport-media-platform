package com.airportmedia.platform.repository;

import com.airportmedia.platform.domain.entity.QueueMeasurement;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;

@ApplicationScoped
public class QueueMeasurementRepository implements PanacheRepository<QueueMeasurement> {

    /** The most recent ACRIS queue reading taken by {@code now} (sim time), or null if none. */
    public Instant latestMeasuredBy(Instant now) {
        return getEntityManager()
                .createQuery("select max(m.measuredAt) from QueueMeasurement m where m.measuredAt <= ?1", Instant.class)
                .setParameter(1, now).getSingleResult();
    }
}
