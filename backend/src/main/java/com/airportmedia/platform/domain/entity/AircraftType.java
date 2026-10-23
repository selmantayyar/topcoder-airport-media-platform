package com.airportmedia.platform.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Canonical (AODB): an aircraft model and its seat layout, used to infer cabin mix. */
@Entity
@Table(name = "aircraft_type")
public class AircraftType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true)
    public String icaoCode;

    @Column(nullable = false)
    public String name;

    public int seatsEconomy;
    public int seatsPremium;
    public int seatsBusiness;

    public int totalSeats() {
        return seatsEconomy + seatsPremium + seatsBusiness;
    }
}
