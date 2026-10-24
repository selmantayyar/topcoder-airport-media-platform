package com.airportmedia.platform.domain.model.output;

/** A screen as the chooser lists it: enough to pick one by gate or zone and know its shape. */
public record ScreenSummary(
        long id,
        String code,
        String name,
        Long zoneId,
        String zoneName,
        String nearestGates,
        String orientation,
        String status) {}
