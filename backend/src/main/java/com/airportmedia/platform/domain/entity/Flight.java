package com.airportmedia.platform.domain.entity;

import com.airportmedia.platform.common.enums.FlightDirection;
import com.airportmedia.platform.common.enums.FlightStatus;
import com.airportmedia.platform.common.enums.Haul;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** Canonical (AODB): one flight leg at the airport on the simulated day. */
@Entity
@Table(name = "flight")
public class Flight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public String flightNumber;

    @ManyToOne(optional = false)
    public Airline airline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public FlightDirection direction;

    /** IATA codes. For a departure, origin is this airport. */
    @Column(nullable = false)
    public String origin;

    @Column(nullable = false)
    public String destination;

    public String via;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Haul haul;

    @ManyToOne(optional = false)
    public AircraftType aircraftType;

    public int paxCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public FlightStatus status;

    public int delayMinutes;
    public String delayCode;

    /** Scheduled / estimated / actual off-block (STD / ETD / ATD for departures). */
    @Column(nullable = false)
    public Instant scheduledTime;

    public Instant estimatedTime;
    public Instant actualTime;

    /** A-CDM milestones. */
    public Instant tobt;
    public Instant tsat;
    public Instant aibt;
    public Instant aobt;

    public String checkInCounters;
    public String belt;
}
