package com.airportmedia.platform.repository;

import com.airportmedia.platform.domain.entity.PromoCode;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PromoCodeRepository implements PanacheRepository<PromoCode> {

    public Optional<PromoCode> findByCode(String code) {
        return find("code", code).firstResultOptional();
    }

    /** A campaign's unredeemed screen codes (family share copies excluded), soonest time box first. */
    public List<PromoCode> listClaimableForCampaign(long campaignId) {
        return list("campaign.id = ?1 and redeemed = false and parentCode is null order by validUntil", campaignId);
    }
}
