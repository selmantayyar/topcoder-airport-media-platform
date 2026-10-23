package com.airportmedia.platform.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A fictional airline operating at the airport. Referenced by flights by IATA code. */
@Entity
@Table(name = "airline")
public class Airline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true)
    public String iataCode;

    @Column(nullable = false)
    public String icaoCode;

    @Column(nullable = false)
    public String name;

    public boolean lowCost;
}
