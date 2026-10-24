package com.airportmedia.platform.repository;

import com.airportmedia.platform.common.enums.IncidentStatus;
import com.airportmedia.platform.domain.entity.Incident;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class IncidentRepository implements PanacheRepository<Incident> {

    /** Incidents not yet resolved (open or acknowledged). */
    public List<Incident> listActive() {
        return list("status <> ?1", IncidentStatus.RESOLVED);
    }

    /** What the monitor reconciles against: active incidents plus operator-resolved ones still held. */
    public List<Incident> listReconcilable() {
        return list("status <> ?1 or held = true", IncidentStatus.RESOLVED);
    }

    public List<Incident> recentlyResolved(int limit) {
        return find("status = ?1 order by resolvedAt desc", IncidentStatus.RESOLVED).page(0, limit).list();
    }
}
