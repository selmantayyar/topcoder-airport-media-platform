package com.airportmedia.platform.repository;

import com.airportmedia.platform.common.enums.CampaignStatus;
import com.airportmedia.platform.domain.entity.Campaign;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class CampaignRepository implements PanacheRepository<Campaign> {

    public List<Campaign> listRunning() {
        return list("status", CampaignStatus.RUNNING);
    }
}
