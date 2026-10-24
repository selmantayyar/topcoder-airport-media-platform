package com.airportmedia.platform.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** Platform-owned: a passenger opening a promo's claim page (QR scanned) — the funnel step before a redemption. */
@Entity
@Table(name = "promo_scan")
public class PromoScan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false)
    public Campaign campaign;

    @ManyToOne
    public PromoCode promoCode;

    @Column(nullable = false)
    public Instant scannedAt;
}
