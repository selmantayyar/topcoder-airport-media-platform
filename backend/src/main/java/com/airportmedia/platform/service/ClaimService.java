package com.airportmedia.platform.service;

import com.airportmedia.platform.common.enums.ClaimStatus;
import com.airportmedia.platform.common.enums.PartyType;
import com.airportmedia.platform.domain.entity.Creative;
import com.airportmedia.platform.domain.entity.Flight;
import com.airportmedia.platform.domain.entity.PromoCode;
import com.airportmedia.platform.domain.entity.PromoScan;
import com.airportmedia.platform.domain.entity.Redemption;
import com.airportmedia.platform.domain.model.output.ClaimResult;
import com.airportmedia.platform.domain.model.output.CrowdProfile;
import com.airportmedia.platform.domain.model.output.PromoInfo;
import com.airportmedia.platform.domain.model.output.ServerEvent;
import com.airportmedia.platform.repository.CreativeRepository;
import com.airportmedia.platform.repository.PromoCodeRepository;
import com.airportmedia.platform.repository.PromoScanRepository;
import com.airportmedia.platform.repository.RedemptionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Records a passenger's QR claim as a {@link Redemption}. Expiry is evaluated against the
 * simulation clock (so jumping the clock past a promo's validity expires it), and a code is
 * single-use: a second claim reports {@code ALREADY_CLAIMED} without recording a duplicate.
 * A successful claim is broadcast on the live stream so dashboards reflect it within seconds.
 */
@ApplicationScoped
public class ClaimService {

    private final PromoCodeRepository promoCodes;
    private final RedemptionRepository redemptions;
    private final PromoScanRepository scans;
    private final CreativeRepository creatives;
    private final SimulationClock clock;
    private final CrowdProfiler profiler;
    private final EventStream stream;

    public ClaimService(
            PromoCodeRepository promoCodes,
            RedemptionRepository redemptions,
            PromoScanRepository scans,
            CreativeRepository creatives,
            SimulationClock clock,
            CrowdProfiler profiler,
            EventStream stream) {
        this.promoCodes = promoCodes;
        this.redemptions = redemptions;
        this.scans = scans;
        this.creatives = creatives;
        this.clock = clock;
        this.profiler = profiler;
        this.stream = stream;
    }

    @Transactional
    public PromoInfo info(String code) {
        PromoCode promo = require(code);
        Instant now = clock.snapshot().simTime();
        boolean expired = now.isAfter(promo.validUntil);
        long secondsRemaining = Math.max(0, Duration.between(now, promo.validUntil).getSeconds());
        Creative creative = creativeFor(promo);
        return new PromoInfo(
                promo.code, promo.campaign.id, promo.campaign.name,
                headline(promo, creative), offer(promo, creative), promo.validUntil,
                promo.redeemed, expired, secondsRemaining);
    }

    /** Records a QR scan (claim page opened) and broadcasts it, so the retailer funnel updates live. */
    @Transactional
    public void scan(String code) {
        PromoCode promo = require(code);
        PromoScan scan = new PromoScan();
        scan.campaign = promo.campaign;
        scan.promoCode = promo;
        scan.scannedAt = clock.snapshot().simTime();
        scans.persist(scan);
        stream.emitAfterCommit(ServerEvent.of("scan", Map.of(
                "campaignId", promo.campaign.id,
                "code", promo.code)));
    }

    @Transactional
    public ClaimResult claim(String code) {
        PromoCode promo = require(code);
        Instant now = clock.snapshot().simTime();

        if (now.isAfter(promo.validUntil)) {
            return result(ClaimStatus.EXPIRED, promo, null, "This offer has expired.");
        }
        if (promo.redeemed) {
            return result(ClaimStatus.ALREADY_CLAIMED, promo, null, "This code has already been claimed.");
        }

        String segment = segmentOf(promo.linkedFlight);
        Redemption redemption = new Redemption();
        redemption.campaign = promo.campaign;
        redemption.promoCode = promo;
        redemption.redeemedAt = now;
        redemption.segment = segment;
        redemptions.persist(redemption);
        promo.redeemed = true;

        stream.emitAfterCommit(ServerEvent.of("redemption", Map.of(
                "campaignId", promo.campaign.id,
                "code", promo.code,
                "segment", segment == null ? "" : segment)));

        return result(ClaimStatus.CLAIMED, promo, segment, "Offer claimed — show this screen at the counter.");
    }

    private PromoCode require(String code) {
        return promoCodes.findByCode(code)
                .orElseThrow(() -> new NotFoundException("No promo code " + code));
    }

    private ClaimResult result(ClaimStatus status, PromoCode promo, String segment, String message) {
        return new ClaimResult(
                status, promo.code, promo.campaign.id, promo.campaign.name,
                offer(promo, creativeFor(promo)), promo.validUntil,
                status == ClaimStatus.CLAIMED ? clock.snapshot().simTime() : null,
                segment, message);
    }

    /** The claiming crowd's dominant party type, inferred from the linked flight (null if none). */
    private String segmentOf(Flight linkedFlight) {
        if (linkedFlight == null) {
            return null;
        }
        CrowdProfile profile = profiler.profile(linkedFlight);
        PartyType dominant = CrowdProfile.dominant(profile.partyType());
        return dominant.name().toLowerCase();
    }

    private Creative creativeFor(PromoCode promo) {
        return creatives.firstForCampaign(promo.campaign.id).orElse(null);
    }

    private static String offer(PromoCode promo, Creative creative) {
        if (creative == null) {
            return promo.campaign.name;
        }
        return creative.offerText != null ? creative.offerText : creative.headline;
    }

    private static String headline(PromoCode promo, Creative creative) {
        return creative != null ? creative.headline : promo.campaign.name;
    }
}
