package com.airportmedia.platform.domain.entity;

import com.airportmedia.platform.common.enums.ScreenOrientation;
import com.airportmedia.platform.common.enums.ScreenStatus;
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

/** Platform-owned: a physical digital screen that plays ads, located in a zone near some gates. */
@Entity
@Table(name = "screen")
public class Screen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true)
    public String code;

    @Column(nullable = false)
    public String name;

    @ManyToOne(optional = false)
    public Zone zone;

    /** Comma-separated gate labels this screen is closest to, e.g. "40,41,42". */
    public String nearestGates;

    public int widthPx;
    public int heightPx;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public ScreenOrientation orientation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public ScreenStatus status;

    public int latencyMs;

    /** When the screen entered its current status (drives the "offline for more than 5 minutes" incident). */
    public Instant statusChangedAt;
}
