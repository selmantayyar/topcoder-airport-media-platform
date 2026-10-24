package com.airportmedia.platform.service;

import com.airportmedia.platform.domain.entity.Campaign;
import com.airportmedia.platform.domain.entity.Flight;
import com.airportmedia.platform.domain.entity.PromoCode;
import com.airportmedia.platform.domain.model.output.PromoIssued;
import com.airportmedia.platform.repository.PromoCodeRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.security.SecureRandom;
import java.time.Instant;

/**
 * Mints promo codes — a retailer's campaign code or a passenger's share-to-family copy — with a fresh,
 * unambiguous, collision-free value ({@code AMP-XXXXXX}), persisted and returned as a {@link PromoIssued}.
 */
@ApplicationScoped
public class PromoIssuance {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int LENGTH = 6;

    private final PromoCodeRepository promoCodes;
    private final SecureRandom random = new SecureRandom();

    public PromoIssuance(PromoCodeRepository promoCodes) {
        this.promoCodes = promoCodes;
    }

    public PromoIssued issue(Campaign campaign, Instant validUntil, Flight linkedFlight, String parentCode) {
        PromoCode promo = new PromoCode();
        promo.campaign = campaign;
        promo.code = nextCode();
        promo.validUntil = validUntil;
        promo.linkedFlight = linkedFlight;
        promo.parentCode = parentCode;
        promo.redeemed = false;
        promoCodes.persist(promo);

        return new PromoIssued(promo.code, parentCode, campaign.id, validUntil,
                PromoPolicy.effectiveExpiry(promo),
                linkedFlight != null ? linkedFlight.flightNumber : null,
                promo.claimPath());
    }

    private String nextCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder("AMP-");
            for (int i = 0; i < LENGTH; i++) {
                sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            code = sb.toString();
        } while (promoCodes.findByCode(code).isPresent());
        return code;
    }
}
