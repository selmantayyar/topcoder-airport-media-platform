package com.airportmedia.platform.repository;

import com.airportmedia.platform.common.util.Aggregates;
import com.airportmedia.platform.domain.entity.Impression;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class ImpressionRepository implements PanacheRepository<Impression> {

    public long countByScreenAndCampaignSince(long screenId, long campaignId, Instant since) {
        return count("screen.id = ?1 and campaign.id = ?2 and shownAt >= ?3", screenId, campaignId, since);
    }

    /** Lifetime impression count per campaign id — the engagement-prior / spend denominator. */
    public Map<Long, Long> countsByCampaign() {
        return Aggregates.toLongCountMap(getEntityManager()
                .createQuery("select i.campaign.id, count(i) from Impression i group by i.campaign.id", Object[].class)
                .getResultList());
    }

    /** Impressions since {@code since}, as screenId → (campaignId → count), for the frequency cap. */
    public Map<Long, Map<Long, Long>> windowCountsByScreenAndCampaign(Instant since) {
        List<Object[]> rows = getEntityManager()
                .createQuery("select i.screen.id, i.campaign.id, count(i) from Impression i "
                        + "where i.shownAt >= ?1 group by i.screen.id, i.campaign.id", Object[].class)
                .setParameter(1, since)
                .getResultList();
        Map<Long, Map<Long, Long>> out = new HashMap<>();
        for (Object[] row : rows) {
            out.computeIfAbsent((Long) row[0], k -> new HashMap<>()).put((Long) row[1], (Long) row[2]);
        }
        return out;
    }
}
