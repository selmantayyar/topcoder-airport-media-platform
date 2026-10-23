package com.airportmedia.platform.adapter.aodb;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Stub for the real AODB vendor feed. Going live means implementing this one class
 * (pull the vendor's CSV/JSON export) — nothing else in the app changes, because the
 * rest of the system only ever sees canonical entities via {@link AodbTranslator}.
 */
@ApplicationScoped
public class LiveFlightSource implements FlightSource {

    @Override
    public String mode() {
        return "live";
    }

    @Override
    public String aodbSnapshotCsv(int flightCount) {
        throw new UnsupportedOperationException(
                "Live AODB feed not wired in this POC. Set app.flight.source=mock.");
    }
}
