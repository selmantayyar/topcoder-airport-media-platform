package com.airportmedia.platform.domain.model.input;

import com.airportmedia.platform.common.enums.SponsoredContentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * An airline scheduling gate-area content for one of its own flights. Headline and offer are optional; when
 * blank, copy is generated from the content type and the flight.
 */
public record SponsoredContentRequest(
        @NotNull Long flightId,
        @NotNull SponsoredContentType type,
        @Size(max = 60) String headline,
        @Size(max = 80) String offerText) {}
