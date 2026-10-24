package com.airportmedia.platform.repository;

import com.airportmedia.platform.domain.entity.PromoCode;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;

@ApplicationScoped
public class PromoCodeRepository implements PanacheRepository<PromoCode> {

    public Optional<PromoCode> findByCode(String code) {
        return find("code", code).firstResultOptional();
    }

    /** The campaign's first still-claimable code — what a passenger screen offers. */
    public Optional<PromoCode> firstClaimableForCampaign(long campaignId) {
        return find("campaign.id = ?1 and redeemed = false order by validUntil", campaignId).firstResultOptional();
    }
}
