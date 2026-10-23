package com.airportmedia.platform.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Canonical (ACRIS CheckpointFacility): a security or passport checkpoint inside a zone. */
@Entity
@Table(name = "checkpoint")
public class Checkpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true)
    public String acrisId;

    @ManyToOne(optional = false)
    public Zone zone;

    @Column(nullable = false)
    public String name;

    /** ACRIS CheckpointFacilityType description, e.g. "Security Screening". */
    @Column(nullable = false)
    public String type;
}
