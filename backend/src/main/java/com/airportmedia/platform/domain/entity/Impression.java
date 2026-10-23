package com.airportmedia.platform.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** Platform-owned: one play of a campaign's creative on a screen. */
@Entity
@Table(name = "impression")
public class Impression {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false)
    public Screen screen;

    @ManyToOne(optional = false)
    public Campaign campaign;

    @Column(nullable = false)
    public Instant shownAt;
}
