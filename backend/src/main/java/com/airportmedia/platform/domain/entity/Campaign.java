package com.airportmedia.platform.domain.entity;

import com.airportmedia.platform.common.enums.CampaignPurpose;
import com.airportmedia.platform.common.enums.CampaignStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** Platform-owned: an advertiser's booked campaign and its targeting. */
@Entity
@Table(name = "campaign")
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false)
    public Advertiser advertiser;

    @Column(nullable = false)
    public String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public CampaignStatus status;

    @Column(nullable = false)
    public BigDecimal budget;

    public BigDecimal dailyCap;
    public int frequencyCapPerScreen;

    /** Targeting stored as JSON; the scoring pipeline (ticket 06) owns its shape. */
    @Column(columnDefinition = "text")
    public String segmentWeightsJson;

    @Column(columnDefinition = "text")
    public String hardFiltersJson;

    @Column(columnDefinition = "text")
    public String placementJson;

    @Column(nullable = false)
    public Instant scheduleStart;

    @Column(nullable = false)
    public Instant scheduleEnd;

    @Column(nullable = false)
    public Instant createdAt;

    /** Set for airline-sponsored content and delay-goodwill vouchers; null for ordinary campaigns. */
    @Enumerated(EnumType.STRING)
    public CampaignPurpose purpose;

    /** The flight a sponsored or goodwill campaign is for. */
    @ManyToOne
    public Flight linkedFlight;

    /** A retail partner co-funding a goodwill voucher. */
    @ManyToOne
    public Advertiser coFunder;
}
