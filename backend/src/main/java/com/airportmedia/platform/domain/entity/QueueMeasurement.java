package com.airportmedia.platform.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** Canonical (ACRIS QueueMeasurement): one reading of a checkpoint's queue at a moment. */
@Entity
@Table(name = "queue_measurement")
public class QueueMeasurement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false)
    public Checkpoint checkpoint;

    /** Number of people in the queue. */
    public int occupancy;

    public int waitTimeSeconds;
    public int projectedWaitTimeSeconds;

    /** Average passengers processed per hour. */
    public int throughput;

    @Column(nullable = false)
    public Instant measuredAt;
}
