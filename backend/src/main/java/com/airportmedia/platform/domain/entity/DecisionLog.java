package com.airportmedia.platform.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Platform-owned: the persisted evidence trail of one decision the pipeline made for one
 * screen at one simulation instant. The crowd profile and the full per-candidate scoring
 * (filters, component scores, rule adjustments, winner, reason) are kept as JSON so a score
 * can be reproduced and audited from exactly the inputs that produced it.
 */
@Entity
@Table(name = "decision_log")
public class DecisionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false)
    public Screen screen;

    @ManyToOne
    public Zone zone;

    @Column(nullable = false)
    public Instant decidedAt;

    public int headcount;

    @ManyToOne
    public Campaign winnerCampaign;

    public String winnerName;

    @Column(nullable = false)
    public String reason;

    public boolean fallback;

    /** The full {@link com.airportmedia.platform.domain.model.input.DecisionInputs} as JSON, so a
     * logged decision can be re-run from exactly the inputs that produced it. */
    @Column(columnDefinition = "text")
    public String inputsJson;

    @Column(columnDefinition = "text")
    public String profileJson;

    @Column(columnDefinition = "text", nullable = false)
    public String candidatesJson;
}
