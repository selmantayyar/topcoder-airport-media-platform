package com.airportmedia.platform.repository;

import com.airportmedia.platform.common.util.Aggregates;
import com.airportmedia.platform.domain.entity.PromoScan;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;

@ApplicationScoped
public class PromoScanRepository implements PanacheRepository<PromoScan> {

    /** Lifetime scan count per campaign id. */
    public Map<Long, Long> countsByCampaign() {
        return Aggregates.toLongCountMap(getEntityManager()
                .createQuery("select s.campaign.id, count(s) from PromoScan s group by s.campaign.id", Object[].class)
                .getResultList());
    }
}
