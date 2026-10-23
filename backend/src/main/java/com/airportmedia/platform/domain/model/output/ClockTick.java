package com.airportmedia.platform.domain.model.output;

import java.time.Instant;

/**
 * A single heartbeat emitted on the live clock stream. Ticket 04 replaces the
 * wall-clock source with the authoritative simulation clock; the wire shape
 * (a tick counter + an instant) stays stable for subscribers. The counter is
 * monotonic within one stream subscription and restarts if the client reconnects.
 */
public record ClockTick(long tickNumber, Instant serverTime) {}
