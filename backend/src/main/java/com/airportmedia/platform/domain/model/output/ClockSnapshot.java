package com.airportmedia.platform.domain.model.output;

import java.time.Instant;

/** The simulation clock's state at one moment, as broadcast to every client. */
public record ClockSnapshot(Instant simTime, int speed, boolean running) {}
