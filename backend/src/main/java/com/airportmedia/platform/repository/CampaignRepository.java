package com.airportmedia.platform.repository;

import com.airportmedia.platform.common.enums.CampaignStatus;
import com.airportmedia.platform.domain.entity.Campaign;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class CampaignRepository implements PanacheRepository<Campaign> {

    public List<Campaign> listRunning() {
        return list("status", CampaignStatus.RUNNING);
    }

    public List<Campaign> findByAdvertiser(long advertiserId) {
        return list("advertiser.id", advertiserId);
    }

    /**
     * Every campaign scheduled to be live at {@code now}, whatever its approval status — the decision
     * pipeline's candidate set, so that unapproved or rejected campaigns are dropped (and logged) by its
     * hard-filter stage rather than silently never considered.
     */
    public List<Campaign> listScheduledAt(Instant now) {
        return list("scheduleStart <= ?1 and scheduleEnd >= ?1", now);
    }

    /** RUNNING campaigns whose schedule covers {@code now} — what the pipeline can actually play. */
    public long countActiveAt(Instant now) {
        return count("status = ?1 and scheduleStart <= ?2 and scheduleEnd >= ?2", CampaignStatus.RUNNING, now);
    }
}
