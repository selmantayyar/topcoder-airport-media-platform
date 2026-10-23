package com.airportmedia.platform.domain.entity;

import com.airportmedia.platform.common.enums.AdvertiserKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Platform-owned: a company that buys inventory (a retailer, an F&B brand, or an airline). */
@Entity
@Table(name = "advertiser")
public class Advertiser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public AdvertiserKind kind;
}
