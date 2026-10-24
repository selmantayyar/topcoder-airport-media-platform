package com.airportmedia.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.airportmedia.platform.common.enums.ClaimStatus;
import com.airportmedia.platform.domain.model.output.ClaimResult;
import com.airportmedia.platform.repository.PromoScanRepository;
import com.airportmedia.platform.repository.RedemptionRepository;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Promo-code claim logic is reproducible business logic (expiry, single-use), so it is pinned
 * against the seeded worked-example promo. The simulation clock drives expiry.
 */
@QuarkusTest
@TestTransaction
class ClaimServiceTest {

    /** Seeded by the Gate 42 worked example; valid until 2026-10-24T00:00Z. */
    private static final String PROMO = "AMP-PERFUME15";
    private static final Instant DURING = Instant.parse("2026-10-23T14:30:00Z");
    private static final Instant AFTER_EXPIRY = Instant.parse("2026-10-25T09:00:00Z");

    @Inject
    ClaimService claims;

    @Inject
    SimulationClock clock;

    @Inject
    RedemptionRepository redemptions;

    @Inject
    PromoScanRepository scans;

    private long redemptionsFor(String code) {
        return redemptions.count("promoCode.code", code);
    }

    @Test
    void claimingAValidCodeRecordsARedemptionAndMarksItClaimed() {
        clock.jumpTo(DURING);

        ClaimResult result = claims.claim(PROMO);

        assertThat(result.status()).isEqualTo(ClaimStatus.CLAIMED);
        assertThat(result.offerText()).isNotBlank();
        assertThat(redemptionsFor(PROMO)).isEqualTo(1);
        assertThat(claims.info(PROMO).redeemed()).isTrue();
    }

    @Test
    void claimingAnAlreadyClaimedCodeDoesNotRecordASecondRedemption() {
        clock.jumpTo(DURING);
        claims.claim(PROMO);

        ClaimResult second = claims.claim(PROMO);

        assertThat(second.status()).isEqualTo(ClaimStatus.ALREADY_CLAIMED);
        assertThat(redemptionsFor(PROMO)).isEqualTo(1);
    }

    @Test
    void claimingAnExpiredCodeIsRejectedAndRecordsNoRedemption() {
        clock.jumpTo(AFTER_EXPIRY);

        ClaimResult result = claims.claim(PROMO);

        assertThat(result.status()).isEqualTo(ClaimStatus.EXPIRED);
        assertThat(redemptionsFor(PROMO)).isZero();
    }

    @Test
    void claimingAnUnknownCodeIsNotFound() {
        clock.jumpTo(DURING);

        assertThatThrownBy(() -> claims.claim("AMP-NOPE")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void scanningRecordsAScanForTheFunnel() {
        clock.jumpTo(DURING);
        long before = scans.count("promoCode.code", PROMO);

        claims.scan(PROMO);

        assertThat(scans.count("promoCode.code", PROMO)).isEqualTo(before + 1);
    }

    @Test
    void infoReportsValidityAndTimeRemaining() {
        clock.jumpTo(DURING);

        var info = claims.info(PROMO);

        assertThat(info.expired()).isFalse();
        assertThat(info.redeemed()).isFalse();
        assertThat(info.secondsRemaining()).isPositive();
        assertThat(info.offerText()).isNotBlank();
    }

    @Test
    void aLinkedCodeExpiresWhenItsFlightsBoardingCloses() {
        // XY501 departs 14:55 after its delay, so boarding — and the linked code — closes at 14:40.
        clock.jumpTo(Instant.parse("2026-10-23T14:45:00Z"));

        assertThat(claims.info(PROMO).expired()).isTrue();
        assertThat(claims.claim(PROMO).status()).isEqualTo(ClaimStatus.EXPIRED);
    }

    @Test
    void sharingCreatesOneFamilyCodeLinkedToTheParent() {
        clock.jumpTo(DURING);
        claims.claim(PROMO);

        var child = claims.share(PROMO);

        assertThat(child.code()).isNotEqualTo(PROMO);
        assertThat(child.parentCode()).isEqualTo(PROMO);
        assertThat(claims.claim(child.code()).status()).isEqualTo(ClaimStatus.CLAIMED);
    }

    @Test
    void aCodeCanOnlyBeSharedOnceAndAFamilyCodeCannotBeReshared() {
        clock.jumpTo(DURING);
        claims.claim(PROMO);
        var child = claims.share(PROMO);
        claims.claim(child.code());

        assertThatThrownBy(() -> claims.share(PROMO)).isInstanceOf(WebApplicationException.class);
        assertThatThrownBy(() -> claims.share(child.code())).isInstanceOf(WebApplicationException.class);
    }

    @Test
    void anUnclaimedCodeCannotMintAFamilyCode() {
        clock.jumpTo(DURING);

        assertThatThrownBy(() -> claims.share(PROMO)).isInstanceOf(WebApplicationException.class);
    }
}
