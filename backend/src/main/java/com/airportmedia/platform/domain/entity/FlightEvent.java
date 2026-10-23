package com.airportmedia.platform.domain.entity;

import com.airportmedia.platform.common.enums.FlightEventType;
import com.airportmedia.platform.common.enums.FlightStatus;
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

/** Canonical (AIDX): a real-time update to a flight leg — a delay, gate change or status change. */
@Entity
@Table(name = "flight_event")
public class FlightEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false)
    public Flight flight;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public FlightEventType type;

    @Column(nullable = false)
    public Instant occurredAt;

    /** Feed the event arrived on, e.g. "AIDX". */
    @Column(nullable = false)
    public String source;

    /** Originating AIDX message id, kept for the evidence trail. */
    public String messageRef;

    public String newGate;

    @Enumerated(EnumType.STRING)
    public FlightStatus newStatus;

    public Integer delayMinutes;
}
