package com.airportmedia.platform.domain.entity;

import com.airportmedia.platform.common.enums.IncidentSeverity;
import com.airportmedia.platform.common.enums.IncidentStatus;
import com.airportmedia.platform.common.enums.IncidentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Platform-owned: an operational problem auto-raised by the incident monitor (a screen offline too long,
 * a lagging data feed, a creative that fails QA). {@code subjectKey} identifies what it's about (e.g.
 * {@code screen:12}) so the monitor raises one active incident per subject and auto-resolves it on recovery.
 */
@Entity
@Table(name = "incident")
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public String subjectKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public IncidentType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public IncidentSeverity severity;

    @Column(nullable = false)
    public String owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public IncidentStatus status;

    @Column(nullable = false)
    public String title;

    public String detail;

    @Column(nullable = false)
    public Instant raisedAt;

    @Column(nullable = false)
    public Instant updatedAt;

    public Instant resolvedAt;

    /**
     * Set when an operator resolves the incident while its condition still holds: the monitor then won't
     * re-raise it every scan, and clears this once the condition recovers.
     */
    public boolean held;
}
