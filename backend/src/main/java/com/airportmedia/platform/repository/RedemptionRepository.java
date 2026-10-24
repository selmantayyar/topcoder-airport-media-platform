package com.airportmedia.platform.repository;

import com.airportmedia.platform.common.util.Aggregates;
import com.airportmedia.platform.domain.entity.Redemption;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;

@ApplicationScoped
public class RedemptionRepository implements PanacheRepository<Redemption> {

    /** Lifetime redemption count per campaign id — the engagement-prior numerator. */
    public Map<Long, Long> countsByCampaign() {
        return Aggregates.toLongCountMap(getEntityManager()
                .createQuery("select r.campaign.id, count(r) from Redemption r group by r.campaign.id", Object[].class)
                .getResultList());
    }
}
