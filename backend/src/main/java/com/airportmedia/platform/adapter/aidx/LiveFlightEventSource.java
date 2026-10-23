package com.airportmedia.platform.adapter.aidx;

import jakarta.enterprise.context.ApplicationScoped;

/** Stub for a real AIDX vendor feed. Implement this one class to go live. */
@ApplicationScoped
public class LiveFlightEventSource implements FlightEventSource {

    @Override
    public String mode() {
        return "live";
    }

    @Override
    public String buildFlightLegNotif(AidxLegChange change) {
        throw new UnsupportedOperationException(
                "Live AIDX feed not wired in this POC. Set app.aidx.source=mock.");
    }
}
