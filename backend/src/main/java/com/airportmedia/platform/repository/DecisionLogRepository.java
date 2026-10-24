package com.airportmedia.platform.repository;

import com.airportmedia.platform.domain.entity.DecisionLog;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class DecisionLogRepository implements PanacheRepository<DecisionLog> {

    public List<DecisionLog> recent(int limit) {
        return find("order by decidedAt desc, id desc").page(0, limit).list();
    }

    public List<DecisionLog> recentForScreen(long screenId, int limit) {
        return find("screen.id = ?1 order by decidedAt desc, id desc", screenId).page(0, limit).list();
    }
}
