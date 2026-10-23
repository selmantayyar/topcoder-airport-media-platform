package com.airportmedia.platform.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Canonical (AODB): the gate and stand a flight is assigned to, and the zone that gate sits in. */
@Entity
@Table(name = "gate_assignment")
public class GateAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false)
    public Flight flight;

    @Column(nullable = false)
    public String gate;

    public String stand;

    @ManyToOne(optional = false)
    public Zone zone;
}
