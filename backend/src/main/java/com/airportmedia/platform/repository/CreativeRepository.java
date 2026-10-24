package com.airportmedia.platform.repository;

import com.airportmedia.platform.domain.entity.Creative;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class CreativeRepository implements PanacheRepository<Creative> {

    public Optional<Creative> firstForCampaign(long campaignId) {
        return find("campaign.id = ?1 order by id", campaignId).firstResultOptional();
    }

    public List<Creative> findByAdvertiser(long advertiserId) {
        return list("advertiser.id", advertiserId);
    }
}
