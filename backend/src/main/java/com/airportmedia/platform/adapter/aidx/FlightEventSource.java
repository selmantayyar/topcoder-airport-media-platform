package com.airportmedia.platform.adapter.aidx;

/**
 * A source of AIDX real-time flight-leg updates. The mock serialises changes into
 * IATA_AIDX_FlightLegNotifRQ XML (which {@link AidxTranslator} parses back); a Live
 * implementation would receive the same messages pushed from a vendor feed.
 */
public interface FlightEventSource {

    String mode();

    /** Serialise a leg change as an IATA_AIDX_FlightLegNotifRQ message. */
    String buildFlightLegNotif(AidxLegChange change);
}
