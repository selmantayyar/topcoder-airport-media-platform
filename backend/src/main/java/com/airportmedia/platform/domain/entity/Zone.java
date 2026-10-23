package com.airportmedia.platform.domain.entity;

import com.airportmedia.platform.common.enums.ZoneKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Canonical (ACRIS ZoneAreaLocation): an area within a terminal that screens and gates sit in. */
@Entity
@Table(name = "zone")
public class Zone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true)
    public String acrisId;

    @ManyToOne(optional = false)
    public Terminal terminal;

    @Column(nullable = false)
    public String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public ZoneKind kind;

    public double latitude;
    public double longitude;
}
