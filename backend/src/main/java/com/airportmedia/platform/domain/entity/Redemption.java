package com.airportmedia.platform.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** Platform-owned: a passenger claiming a promo code, attributed to a campaign and segment. */
@Entity
@Table(name = "redemption")
public class Redemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false)
    public Campaign campaign;

    @ManyToOne
    public PromoCode promoCode;

    @Column(nullable = false)
    public Instant redeemedAt;

    /** Dominant crowd segment at claim time, e.g. "family". */
    public String segment;
}
