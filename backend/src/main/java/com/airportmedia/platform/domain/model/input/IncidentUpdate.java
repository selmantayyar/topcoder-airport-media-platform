package com.airportmedia.platform.domain.model.input;

import com.airportmedia.platform.common.enums.IncidentStatus;
import jakarta.validation.constraints.Size;

/** An operator acknowledging, resolving or reassigning an incident; null fields are left unchanged. */
public record IncidentUpdate(IncidentStatus status, @Size(min = 1, max = 64) String owner) {}
