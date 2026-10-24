package com.airportmedia.platform.repository;

import com.airportmedia.platform.domain.entity.FlightEvent;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;

@ApplicationScoped
public class FlightEventRepository implements PanacheRepository<FlightEvent> {

    /** The most recent AIDX update that has happened by {@code now} (sim time), or null if none. */
    public Instant latestOccurredBy(Instant now) {
        return getEntityManager()
                .createQuery("select max(e.occurredAt) from FlightEvent e where e.occurredAt <= ?1", Instant.class)
                .setParameter(1, now).getSingleResult();
    }
}
