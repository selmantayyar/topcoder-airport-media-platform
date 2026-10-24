package com.airportmedia.platform.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** A creative's copy in a language other than its primary one. */
@Embeddable
public class CreativeTranslation {

    @Column(nullable = false)
    public String headline;

    public String offerText;

    @Column(columnDefinition = "text")
    public String body;
}
