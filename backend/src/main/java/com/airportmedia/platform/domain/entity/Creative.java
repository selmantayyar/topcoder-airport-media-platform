package com.airportmedia.platform.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Platform-owned: a reusable ad page an advertiser can attach to campaigns. */
@Entity
@Table(name = "creative")
public class Creative {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false)
    public Advertiser advertiser;

    @ManyToOne
    public Campaign campaign;

    @Column(nullable = false)
    public String name;

    @Column(nullable = false)
    public String templateKey;

    /** ISO language code of this creative's copy, e.g. "en" or "es". */
    @Column(nullable = false)
    public String language;

    @Column(nullable = false)
    public String headline;

    @Column(columnDefinition = "text")
    public String body;

    public String offerText;
}
