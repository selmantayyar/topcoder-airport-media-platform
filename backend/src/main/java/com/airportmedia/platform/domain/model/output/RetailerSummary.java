package com.airportmedia.platform.domain.model.output;

/** A selectable retail company in the dashboard's outlet picker. */
public record RetailerSummary(long id, String name, String kind, int outletCount) {}
