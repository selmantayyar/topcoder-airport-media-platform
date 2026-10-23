package com.airportmedia.platform.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** Platform-owned: a time-boxed promo code shown on a screen, claimable via QR. */
@Entity
@Table(name = "promo_code")
public class PromoCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false)
    public Campaign campaign;

    @Column(nullable = false, unique = true)
    public String code;

    @Column(nullable = false)
    public Instant validUntil;

    /** When set, the code expires with this flight's boarding. */
    @ManyToOne
    public Flight linkedFlight;

    /** Set on a share-to-family child code; points back to the originating code. */
    public String parentCode;

    public boolean redeemed;
}
