package com.airportmedia.platform.domain.model.output;

import java.time.Instant;

/**
 * The envelope for everything pushed on the live SSE channel. {@code type} lets each
 * client dispatch (clock ticks, injected flight/screen events, later dashboard updates);
 * {@code data} is the type-specific payload.
 */
public record ServerEvent(String type, Instant at, Object data) {

    public static ServerEvent of(String type, Object data) {
        return new ServerEvent(type, Instant.now(), data);
    }
}
