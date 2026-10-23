package com.airportmedia.platform.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Canonical (ACRIS TerminalFacility): a passenger terminal building. */
@Entity
@Table(name = "terminal")
public class Terminal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** Original ACRIS NGSI-LD entity id, kept for provenance. */
    @Column(nullable = false, unique = true)
    public String acrisId;

    @Column(nullable = false)
    public String code;

    @Column(nullable = false)
    public String name;
}
