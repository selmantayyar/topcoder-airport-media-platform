package com.airportmedia.platform.domain.model.output;

/** One of a company's stores, for scoping "passenger flow near my store" to a single outlet. */
public record OutletSummary(long id, String name, String type, String zoneName, String terminalCode) {}
