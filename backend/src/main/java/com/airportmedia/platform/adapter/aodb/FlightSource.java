package com.airportmedia.platform.adapter.aodb;

/**
 * A source of AODB flight-movement data. The mock implementation generates a realistic
 * one-day snapshot; a Live implementation would pull the same CSV shape from a vendor
 * AODB export. Selected by the {@code app.flight.source} config value.
 */
public interface FlightSource {

    String mode();

    /** The day's AODB export as 33-column CSV (see {@link AodbCsvCodec}). */
    String aodbSnapshotCsv(int flightCount);
}
