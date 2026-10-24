package com.airportmedia.platform.domain.model.input;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A retailer's free-text brief for the creative assistant, e.g. "Festive sweets, 20% off, families flying domestic". */
public record CreativeBrief(@NotBlank @Size(max = 300) String brief) {}
