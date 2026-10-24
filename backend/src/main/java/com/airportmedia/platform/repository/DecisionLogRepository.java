package com.airportmedia.platform.repository;

import com.airportmedia.platform.domain.entity.DecisionLog;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class DecisionLogRepository implements PanacheRepository<DecisionLog> {

    public List<DecisionLog> recent(int limit) {
        return find("order by decidedAt desc, id desc").page(0, limit).list();
    }

    public List<DecisionLog> recentForScreen(long screenId, int limit) {
        return find("screen.id = ?1 order by decidedAt desc, id desc", screenId).page(0, limit).list();
    }

    /** Ads played in a window: executed decisions that had a winner. */
    public long countPlaysBetween(Instant from, Instant to) {
        return count("winnerCampaign is not null and decidedAt >= ?1 and decidedAt <= ?2", from, to);
    }

    /** Audience reached by executed decisions in a window: Σ crowd headcount of decisions that played an ad. */
    public long audienceBetween(Instant from, Instant to) {
        Long total = getEntityManager()
                .createQuery("select sum(d.headcount) from DecisionLog d where d.winnerCampaign is not null "
                        + "and d.decidedAt >= ?1 and d.decidedAt <= ?2", Long.class)
                .setParameter(1, from).setParameter(2, to)
                .getSingleResult();
        return total != null ? total : 0L;
    }
}
